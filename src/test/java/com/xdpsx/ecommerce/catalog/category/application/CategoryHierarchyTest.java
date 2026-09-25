package com.xdpsx.ecommerce.catalog.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.PessimisticLockException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

/**
 * Guards the retry contract for hierarchy writes.
 *
 * <p>Deliberately not a database test: the point is which exceptions are retried, how many times, and what the caller
 * sees once the budget is exhausted. The MySQL persistence test covers that contention actually occurs.
 */
class CategoryHierarchyTest {

    private final CategoryHierarchy hierarchy = new CategoryHierarchy(mock(CategoryRepository.class));

    @Test
    void executeWithRetry_ShouldReturnTheFirstResult_WithoutRetrying() {
        AtomicInteger attempts = new AtomicInteger();

        String result = hierarchy.executeWithRetry(() -> {
            attempts.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void executeWithRetry_ShouldRetryLockContention_AndSucceedOnALaterAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        String result = hierarchy.executeWithRetry(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotAcquireLockException("Lock wait timeout exceeded");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    /**
     * A deadlock is reported by InnoDB as {@code SQLState 40001} but reaches the application as an optimistic-locking
     * failure, so it must be classified as lock contention rather than as a normal optimistic conflict.
     */
    @Test
    void executeWithRetry_ShouldRetryADeadlockReportedAsOptimisticLockFailure() {
        AtomicInteger attempts = new AtomicInteger();

        Integer result = hierarchy.executeWithRetry(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new ObjectOptimisticLockingFailureException(
                        com.xdpsx.ecommerce.catalog.category.domain.Category.class,
                        new PessimisticLockException("Deadlock found when trying to get lock"));
            }
            return 7;
        });

        assertThat(result).isEqualTo(7);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void executeWithRetry_ShouldReturnRetryableConflict_WhenTheRetryBudgetIsExhausted() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> hierarchy.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new PessimisticLockingFailureException("Lock wait timeout exceeded");
                }))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.CONCURRENT_WRITE_CONFLICT));

        assertThat(attempts.get()).isGreaterThan(1);
    }

    @Test
    void executeWithRetry_ShouldNotRetryABusinessRejection() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> hierarchy.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new ApplicationException(ErrorCode.INVALID_CATEGORY_ORDER);
                }))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.INVALID_CATEGORY_ORDER));

        assertThat(attempts.get()).isEqualTo(1);
    }
}
