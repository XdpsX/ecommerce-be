package com.xdpsx.ecommerce.catalog.category.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The slug contract is shared with the Liquibase backfill for legacy rows, so the individual normalization rules
 * are pinned here rather than only through a service test.
 */
class CategorySlugTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Electronics          | electronics",
                "Điện thoại           | dien-thoai",
                "ĐỒ chơi              | do-choi",
                "Giày dép!!!          | giay-dep",
                "  Thời Trang         | thoi-trang",
                "Cà phê & Trà--Sữa    | ca-phe-tra-sua",
                "Máy ảnh 4K           | may-anh-4k",
                "Shoes_Nike 2024      | shoes-nike-2024"
            })
    void normalize_ShouldApplyTheDocumentedRules(String input, String expected) {
        assertThat(CategorySlug.normalize(input)).isEqualTo(expected.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!!", "***", "   ", "---"})
    void normalize_ShouldReturnEmpty_WhenNoUsableCharacterRemains(String input) {
        assertThat(CategorySlug.normalize(input)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"electronics", "may-anh-4k", "a", "0-9"})
    void isNormalized_ShouldAcceptAlreadyNormalizedSlugs(String slug) {
        assertThat(CategorySlug.isNormalized(slug)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Electronics", "may anh", "trailing-", "-leading", "dien-thoai-", "a--b"})
    void isNormalized_ShouldRejectSlugsThatWouldChange(String slug) {
        assertThat(CategorySlug.isNormalized(slug)).isFalse();
    }
}
