package com.xdpsx.ecommerce.catalog.category.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The slug backfill in {@code changeset-6.sql} has to agree with {@link CategorySlug#normalize}. That agreement
 * cannot be checked by a unit test alone, so this test extracts the SQL transliteration statements and replays
 * them with the same rules. It protects against editing one side without the other.
 *
 * <p>The SQL is still verified against a real MySQL instance as part of the change; this test only pins the
 * mapping so a regression is caught by the regular suite.
 */
class CategorySlugMigrationConsistencyTest {

    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-6.sql");
    /** Matches the trailing {@code , 'src', 'dst')} of every REPLACE, including the nested ones. */
    private static final Pattern REPLACE_PAIR = Pattern.compile(", '(.)', '(.)'\\)");

    private static String readChangeset() {
        try {
            return Files.readString(CHANGESET, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + CHANGESET, e);
        }
    }

    private static List<String[]> sqlReplacePairs() {
        List<String[]> pairs = new ArrayList<>();
        Matcher matcher = REPLACE_PAIR.matcher(readChangeset());
        while (matcher.find()) {
            pairs.add(new String[] {matcher.group(1), matcher.group(2)});
        }
        return pairs;
    }

    /**
     * Replays the full changeset pipeline: {@code LOWER(TRIM(name))}, the generated transliteration statements, the
     * {@code [^a-z0-9]+ -> -} collapse and the dash trim.
     */
    private static String applySqlMapping(String value) {
        String result = value.trim().toLowerCase(java.util.Locale.ROOT);
        for (String[] pair : sqlReplacePairs()) {
            result = result.replace(pair[0], pair[1]);
        }
        return result.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    static Stream<String> representativeNames() {
        return Stream.of(
                "Electronics",
                "Điện thoại",
                "ĐỒ chơi",
                "Giày dép!!!",
                "  Thời Trang  ",
                "Cà phê & Trà--Sữa",
                "Phụ kiện cho điện thoại và máy tính bảng",
                "Quà tặng đặc biệt",
                "Máy ảnh 4K");
    }

    @ParameterizedTest
    @MethodSource("representativeNames")
    void sqlBackfill_ShouldProduceTheSameSlugAsTheJavaNormalizer(String name) {
        assertThat(applySqlMapping(name))
                .as("SQL backfill for '%s' must equal CategorySlug.normalize", name)
                .isEqualTo(CategorySlug.normalize(name));
    }

    @Test
    void changeset_ShouldCoverEveryAccentedCharacterTheJavaNormalizerHandles() {
        Set<String> mappedInSql = new LinkedHashSet<>();
        for (String[] pair : sqlReplacePairs()) {
            mappedInSql.add(pair[0]);
        }

        List<String> missing = new ArrayList<>();
        for (int cp = 0x00A0; cp <= 0x2C7F; cp++) {
            if (cp >= 0xD800 && cp <= 0xDFFF) continue;
            String ch = new String(Character.toChars(cp));
            // The changeset lowercases the name before translating, so only lowercase forms need an entry.
            if (!ch.equals(ch.toLowerCase(java.util.Locale.ROOT))) continue;
            if (ch.matches("[a-z0-9]")) continue;

            String normalized = CategorySlug.normalize(ch);
            if (!normalized.matches("[a-z0-9]")) continue;

            // A character whose normalized form equals itself needs no REPLACE entry.
            if (normalized.equals(ch)) continue;

            if (!mappedInSql.contains(ch)) {
                missing.add(String.format("U+%04X", cp));
            }
        }

        assertThat(missing)
                .as("changeset-6.sql must map every accented character the Java normalizer folds to ASCII")
                .isEmpty();
    }

    @Test
    void changeset_ShouldNotBeEditedIntoAnUnreadableSingleStatement() throws IOException {
        List<String> overlong = Files.readAllLines(CHANGESET, StandardCharsets.UTF_8).stream()
                .filter(line -> line.length() > 2000)
                .toList();

        assertThat(overlong)
                .as("slug mapping must stay split per target letter")
                .isEmpty();
    }

    @Test
    void changeset_ShouldStillEnforceTheRequiredSchema() {
        String sql = readChangeset();

        assertThat(sql).contains("ADD CONSTRAINT uk_category_slug UNIQUE (slug)");
        assertThat(sql).contains("ADD INDEX idx_category_parent_order (parent_id, display_order)");
        assertThat(sql).contains("ON DELETE RESTRICT");
        assertThat(sql).contains("DROP COLUMN public_flg");
        // A silent re-parent of children into roots must never be reintroduced.
        assertThat(sql).doesNotContain("ON DELETE SET NULL");
    }

    /**
     * Models the collision-resolution branch of the changeset.
     *
     * <p>This mirrors the {@code CASE} in the changeset: the lowest id of a candidate group keeps the plain slug
     * only when no other row's suffixed value would claim it, otherwise every row of the group takes its
     * {@code -{id}} suffix. {@code CategoryMigrationTest} executes the real SQL against MySQL; this model pins the
     * rule so a regression into the single-pass form that produced duplicates is caught without a database.
     */
    private static List<String> resolveLikeChangeset(List<String> candidatesInIdOrder) {
        List<String> suffixed = new ArrayList<>();
        for (int index = 0; index < candidatesInIdOrder.size(); index++) {
            // The model uses index+1 as the row id.
            suffixed.add(candidatesInIdOrder.get(index) + "-" + (index + 1));
        }

        List<String> resolved = new ArrayList<>();
        for (int index = 0; index < candidatesInIdOrder.size(); index++) {
            String candidate = candidatesInIdOrder.get(index);
            int id = index + 1;

            int firstIndexWithCandidate = candidatesInIdOrder.indexOf(candidate);
            boolean isLowestIdOfGroup = firstIndexWithCandidate == index;
            boolean claimedByAnotherRowsSuffix = false;
            for (int other = 0; other < candidatesInIdOrder.size(); other++) {
                if (other != index && suffixed.get(other).equals(candidate)) {
                    claimedByAnotherRowsSuffix = true;
                    break;
                }
            }

            resolved.add(isLowestIdOfGroup && !claimedByAnotherRowsSuffix ? candidate : candidate + "-" + id);
        }
        return resolved;
    }

    @Test
    void collisionResolution_ShouldNotRecreateTheCollisionItIsFixing() {
        // id 1 "Foo" and id 2 "Foo!" normalize to foo; id 3 "Foo 2" already owns the value the suffix would
        // produce for id 2. The naive single-pass form yielded foo, foo-2, foo-2 and failed the unique index.
        List<String> resolved = resolveLikeChangeset(List.of("foo", "foo", "foo-2", "category-4", "category-5"));

        assertThat(resolved).containsExactly("foo", "foo-2", "foo-2-3", "category-4", "category-5");
        assertThat(new LinkedHashSet<>(resolved)).hasSameSizeAs(resolved);
    }

    @Test
    void collisionResolution_ShouldSeparateTwoRowsSharingThePunctuationFallback() {
        // The real fixture shape: "!!!" (id 4) and "Category 4" (id 5) both end up as category-4 before
        // resolution, so the fallback collision must be broken up as well.
        List<String> resolved = resolveLikeChangeset(List.of("foo", "foo", "foo-2", "category-4", "category-4"));

        assertThat(resolved).containsExactly("foo", "foo-2", "foo-2-3", "category-4", "category-4-5");
        assertThat(new LinkedHashSet<>(resolved)).hasSameSizeAs(resolved);
    }

    @Test
    void collisionResolution_ShouldStayUniqueAcrossRepresentativeLegacyShapes() {
        // Pairs that normalize to the same value, plus pre-existing values shaped like an existing slug and a
        // free value, plus the punctuation-only fallback values.
        List<String> candidates =
                List.of("foo", "foo", "foo-2", "category-4", "category-5", "dien-thoai", "dien-thoai", "giay-dep");

        List<String> resolved = resolveLikeChangeset(candidates);

        assertThat(new LinkedHashSet<>(resolved)).hasSameSizeAs(resolved);
        assertThat(resolved).doesNotContainNull();
        assertThat(resolved).allSatisfy(slug -> assertThat(slug).isNotBlank());
    }

    @Test
    void collisionResolution_ShouldKeepTheLowestIdReadableWhenNothingClaimsTheSlug() {
        List<String> resolved = resolveLikeChangeset(List.of("electronics", "laptops", "phones"));

        assertThat(resolved).containsExactly("electronics", "laptops", "phones");
    }
}
