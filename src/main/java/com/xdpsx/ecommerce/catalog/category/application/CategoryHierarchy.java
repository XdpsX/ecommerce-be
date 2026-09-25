package com.xdpsx.ecommerce.catalog.category.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.persistence.PessimisticLockException;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryHierarchyAnchor;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

/**
 * Hierarchy invariants for Category writes: sibling-group ordering, the three-level limit and cycle prevention.
 *
 * <p>Every mutation must run inside the caller's transaction and must lock the sibling groups it touches through this
 * component, so validation and renumbering happen on the same snapshot. The database has no unique constraint on
 * {@code (parent_id, display_order)} on purpose; this component is what keeps the order continuous and unique.
 */
@Component
public class CategoryHierarchy {
    /**
     * Retry budget for a deadlock or lock-wait timeout. The hierarchy anchor already removes the known cycles, so this
     * is a safety net for contention that is not covered by the design (for example a foreign-key gap lock); a
     * first-attempt win never retries.
     */
    private static final int DEFAULT_MAX_WRITE_ATTEMPTS = 3;

    /**
     * Serializes hierarchy writes. A category write locks a node first and whole sibling groups second, so two writers
     * of the same group would otherwise take the two sides in opposite order.
     *
     * <p>Taking the anchor row {@code SELECT ... FOR UPDATE} at the very start of every write makes the lock graph a
     * chain instead of a cycle: a writer either holds the anchor and can finish, or waits on the anchor while holding
     * nothing. A per-group anchor could not do that, because a move spans two groups, and a root group has no row of its
     * own to lock.
     *
     * <p>The cost is that hierarchy writes serialize with each other. That is acceptable for admin-managed taxonomy
     * (rare, small, and already locked group-wide) and buys a deadlock-free contract instead of a retry-dependent one.
     *
     * <p>Deliberately not {@code GET_LOCK}: a MySQL named lock is bound to the physical session, is not released by
     * commit or rollback, and would therefore leak into the connection pool. A row lock has transaction lifetime.
     */
    private final CategoryRepository categoryRepository;

    private final int maxWriteAttempts;

    public CategoryHierarchy(CategoryRepository categoryRepository) {
        this(categoryRepository, DEFAULT_MAX_WRITE_ATTEMPTS);
    }

    /**
     * @param maxWriteAttempts how many times a contended write may be attempted; {@code 1} disables retrying, which a
     *     test uses to prove the group anchor serializes writers on its own
     */
    public CategoryHierarchy(CategoryRepository categoryRepository, int maxWriteAttempts) {
        this.categoryRepository = categoryRepository;
        this.maxWriteAttempts = maxWriteAttempts;
    }

    /**
     * Runs a hierarchy write and retries it when the database resolves lock contention by aborting the transaction.
     *
     * <p>{@code supplier} must own a complete transaction so a retry starts from a clean one; a retried attempt must
     * never resume a half-applied unit of work. The attempt loop therefore lives here while the transactional unit is
     * supplied by the caller.
     *
     * @throws ApplicationException {@link ErrorCode#CONCURRENT_WRITE_CONFLICT} when every attempt was aborted, so the
     *     client gets a retryable conflict instead of a raw database failure
     */
    public <T> T executeWithRetry(Supplier<T> supplier) {
        for (int attempt = 1; ; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException ex) {
                if (!isLockContention(ex)) throw ex;
                if (attempt >= maxWriteAttempts) {
                    // Contention outlived the retry budget, so report a retryable conflict instead of letting a raw
                    // database failure surface as an unexplained server error.
                    throw new ApplicationException(ErrorCode.CONCURRENT_WRITE_CONFLICT, ex);
                }
            }
        }
    }

    /**
     * Classifies lock contention, including a deadlock, which InnoDB reports as {@code SQLState 40001} but Hibernate
     * wraps in an optimistic-locking exception because the original transaction rolled back.
     */
    private boolean isLockContention(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || cause instanceof CannotAcquireLockException
                    || cause instanceof PessimisticLockException
                    || cause instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
            if (cause == cause.getCause()) break;
        }
        return false;
    }

    /**
     * Renumbers a sibling group to {@code 0..N-1}, preserving the order the group was read in.
     *
     * <p>The list must come from {@link #lockSiblingGroup(Integer)} or {@link #lockGroupsForMove}; renumbering an
     * unlocked read could overwrite a concurrent writer's order.
     *
     * @return the normalized group
     */
    public List<Category> normalizeSiblingOrder(List<Category> siblings) {
        for (int index = 0; index < siblings.size(); index++) {
            siblings.get(index).setDisplayOrder(index);
        }
        return siblings;
    }

    /**
     * Locks and loads a sibling group in display order. {@code parentId == null} addresses the root group.
     *
     * <p>Order is taken from the locked rows instead of a {@code MAX(display_order)} read, which a concurrent writer
     * could invalidate between the read and the insert. The caller must already hold {@link #lockHierarchy()}.
     */
    public List<Category> lockSiblingGroup(Integer parentId) {
        return parentId == null
                ? new ArrayList<>(categoryRepository.findRootsForUpdate())
                : new ArrayList<>(categoryRepository.findChildrenForUpdate(parentId));
    }

    /**
     * Acquires the hierarchy write anchor. Must be the first lock a hierarchy write takes.
     *
     * <p>{@code findByIdForUpdate} is called first so an absent anchor row fails with a clear "resource not found"
     * instead of a bare {@code NoResultException} from the lock query itself.
     */
    public void lockHierarchy() {
        if (categoryRepository.hierarchyAnchorExists()) {
            categoryRepository.lockHierarchyAnchor();
            return;
        }
        throw new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "categoryHierarchyAnchor", "resourceId", CategoryHierarchyAnchor.SINGLETON_ID));
    }

    /**
     * Locks the sibling groups a move touches. The moved node is excluded from the old group so the caller can decide
     * whether the same-parent case is a reorder or a no-op.
     *
     * <p>Groups are still read in a stable order (roots first, then ascending parent id). With the hierarchy anchor held
     * that ordering no longer decides deadlocks, but it keeps the group reads deterministic. The caller must already
     * hold {@link #lockHierarchy()}.
     */
    public MoveSnapshot lockGroupsForMove(Category moved, Integer targetParentId) {
        Integer currentParentId =
                moved.getParent() == null ? null : moved.getParent().getId();
        boolean sameGroup = currentParentId == null ? targetParentId == null : currentParentId.equals(targetParentId);

        if (sameGroup) {
            List<Category> group = lockSiblingGroup(targetParentId);
            group.removeIf(sibling -> sibling.getId().equals(moved.getId()));
            return new MoveSnapshot(normalizeSiblingOrder(group), null);
        }

        Integer firstParentId = isRootFirst(currentParentId, targetParentId) ? currentParentId : targetParentId;
        Integer secondParentId = isRootFirst(currentParentId, targetParentId) ? targetParentId : currentParentId;

        List<Category> firstGroup = lockSiblingGroup(firstParentId);
        List<Category> secondGroup = lockSiblingGroup(secondParentId);

        // The previous group is the one that currently contains the moved node.
        boolean previousGroupIsFirst =
                firstParentId == null ? currentParentId == null : firstParentId.equals(currentParentId);
        List<Category> oldGroup = previousGroupIsFirst ? firstGroup : secondGroup;
        List<Category> newGroup = previousGroupIsFirst ? secondGroup : firstGroup;

        oldGroup.removeIf(sibling -> sibling.getId().equals(moved.getId()));
        return new MoveSnapshot(normalizeSiblingOrder(oldGroup), normalizeSiblingOrder(newGroup));
    }

    private boolean isRootFirst(Integer firstParentId, Integer secondParentId) {
        if (firstParentId == null) return true;
        if (secondParentId == null) return false;
        return firstParentId < secondParentId;
    }

    /**
     * Position to insert at, validated against the group that will receive the node.
     *
     * <p>A different group accepts {@code 0..size}; the same group has already had the moved node removed, so it
     * accepts {@code 0..size} as well.
     */
    public int validateInsertPosition(Integer position, int targetGroupSize) {
        if (position == null || position < 0 || position > targetGroupSize) {
            throw new ApplicationException(
                    ErrorCode.INVALID_CATEGORY_ORDER,
                    Map.of("position", String.valueOf(position), "maxPosition", targetGroupSize));
        }
        return position;
    }

    /**
     * Rejects creating a child under a node that is already at the maximum depth.
     *
     * <p>Separate from {@link #checkMoveAllowed} because a node being created has no id yet: it cannot have
     * descendants, so only the parent level matters.
     */
    public void checkNewNodePlacement(Category newParent) {
        if (newParent == null) return;

        int parentLevel = lockAncestorChain(newParent.getId()).size();
        if (parentLevel + 1 > Category.MAX_DEPTH) {
            throw new ApplicationException(
                    ErrorCode.INVALID_CATEGORY_DEPTH,
                    Map.of("parentId", newParent.getId(), "maxDepth", Category.MAX_DEPTH));
        }
    }

    /**
     * Rejects self-parenting and moving a node under one of its own descendants, then enforces the three-level limit
     * for the whole moved subtree.
     *
     * <p>The target parent's ancestor chain is locked before it is measured, so the computed level cannot change under
     * the transaction. The subtree height is read from an unlocked projection: a concurrent move inside the subtree is
     * not covered, which is the same accepted limitation as an over-deep tree.
     */
    public void checkMoveAllowed(Category moved, Category newParent) {
        if (newParent != null && newParent.getId().equals(moved.getId())) {
            throw new ApplicationException(
                    ErrorCode.INVALID_CATEGORY_HIERARCHY, Map.of("categoryId", moved.getId(), "reason", "self-parent"));
        }

        int newParentLevel = 0;
        if (newParent != null) {
            // Walking up from the target parent is enough: any descendant of the moved node has the moved node on its
            // ancestor chain, so it would be found here.
            List<Integer> targetChain = lockAncestorChain(newParent.getId());
            if (targetChain.contains(moved.getId())) {
                throw new ApplicationException(
                        ErrorCode.INVALID_CATEGORY_HIERARCHY,
                        Map.of("categoryId", moved.getId(), "parentId", newParent.getId(), "reason", "descendant"));
            }
            newParentLevel = targetChain.size();
        }

        int subtreeHeight = subtreeHeightOf(moved.getId(), parentViews());
        if (newParentLevel + subtreeHeight > Category.MAX_DEPTH) {
            throw new ApplicationException(
                    ErrorCode.INVALID_CATEGORY_DEPTH,
                    Map.of("categoryId", moved.getId(), "maxDepth", Category.MAX_DEPTH));
        }
    }

    /**
     * Locks the ancestor chain of a node, starting at the node itself, and returns the ids nearest-first.
     *
     * <p>Each hop locks the row it reads, so the level measured from this chain stays valid for the rest of the
     * transaction. A node that disappeared between two hops is reported as not found instead of silently shortening
     * the chain.
     *
     * <p>Chains are walked nearest-first; because the depth is capped at three, two concurrent moves cannot hold a
     * long enough chain to matter, and MySQL may still pick a deadlock victim to retry.
     */
    private List<Integer> lockAncestorChain(Integer startId) {
        List<Integer> chain = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Integer currentId = startId;
        while (currentId != null) {
            // A pre-existing cycle would otherwise make this loop forever; report it as an invalid hierarchy instead.
            if (!visited.add(currentId)) {
                throw new ApplicationException(
                        ErrorCode.INVALID_CATEGORY_HIERARCHY, Map.of("categoryId", currentId, "reason", "cycle"));
            }

            Integer missingId = currentId;
            CategoryRepository.CategoryParentView view = categoryRepository
                    .findParentViewForUpdate(missingId)
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", missingId)));
            chain.add(missingId);
            currentId = view.getParentId();
        }
        return chain;
    }

    /**
     * Cycle and depth walk over the whole tree, expressed as a bounded traversal instead of unbounded recursion.
     *
     * <p>Assumption: the stored tree is well formed (no cycle, {@code MAX_DEPTH} respected). A pre-existing cycle would
     * otherwise make these traversals non-terminating, which is not something a write use case can repair.
     */
    private int subtreeHeightOf(Integer rootId, Map<Integer, Integer> parentById) {
        Map<Integer, List<Integer>> childrenByParent = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : parentById.entrySet()) {
            if (entry.getValue() != null) {
                childrenByParent
                        .computeIfAbsent(entry.getValue(), key -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }

        int height = 0;
        Set<Integer> currentLevel = new HashSet<>();
        currentLevel.add(rootId);
        while (!currentLevel.isEmpty()) {
            height++;
            Set<Integer> nextLevel = new HashSet<>();
            for (Integer nodeId : currentLevel) {
                nextLevel.addAll(childrenByParent.getOrDefault(nodeId, List.of()));
            }
            currentLevel = nextLevel;
        }
        return height;
    }

    private Map<Integer, Integer> parentViews() {
        Map<Integer, Integer> parentById = new HashMap<>();
        for (CategoryRepository.CategoryParentView view : categoryRepository.findAllParentViews()) {
            parentById.put(view.getId(), view.getParentId());
        }
        return parentById;
    }

    /**
     * Locked sibling group after the moved node was removed, plus the target group when the move changes parent.
     *
     * @param oldGroup remaining siblings of the previous group, already normalized
     * @param newGroup target siblings before insertion, already normalized; {@code null} for a same-parent move
     */
    public record MoveSnapshot(List<Category> oldGroup, List<Category> newGroup) {
        public boolean isSameGroup() {
            return newGroup == null;
        }
    }
}
