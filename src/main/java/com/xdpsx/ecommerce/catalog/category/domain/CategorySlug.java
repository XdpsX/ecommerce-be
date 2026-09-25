package com.xdpsx.ecommerce.catalog.category.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic slug normalization for Category.
 *
 * <p>Contract: trim, lowercase, strip diacritics (including {@code đ -> d}), collapse every run of non
 * alphanumeric characters into a single {@code -}, then trim leading and trailing dashes. The result is a
 * lowercase ASCII identifier made of letters, digits and single dashes.
 *
 * <p>The same contract is implemented in the Liquibase backfill for legacy rows, so a normalized legacy name
 * and a newly generated slug agree on identical input.
 */
public final class CategorySlug {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private CategorySlug() {}

    /**
     * @return the normalized slug, or an empty string when the input has no usable characters
     */
    public static String normalize(String value) {
        if (value == null) return "";

        String decomposed = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        // U+0111 has no canonical decomposition, so the stroke letter is mapped explicitly.
        String ascii = withoutMarks.replace('đ', 'd');
        String dashed = NON_ALPHANUMERIC.matcher(ascii).replaceAll("-");
        return EDGE_DASHES.matcher(dashed).replaceAll("");
    }

    /**
     * An explicit client supplied slug must already be in normalized form; normalizing is not applied silently.
     */
    public static boolean isNormalized(String slug) {
        return slug != null && !slug.isEmpty() && slug.equals(normalize(slug));
    }
}
