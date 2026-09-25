-- liquibase formatted sql

-- changeset xdpsx:category-cr1-model-and-order
-- comment: Replace categories.public_flg with status, restore slug and display_order, and make the parent FK restrictive.
-- The new columns are added nullable first so every existing row can be backfilled before the NOT NULL
-- and UNIQUE constraints are enforced. Legacy rows are preserved; no row is dropped or re-parented.

ALTER TABLE categories
ADD COLUMN status VARCHAR(16) NULL AFTER name,
ADD COLUMN slug VARCHAR(160) NULL AFTER status,
ADD COLUMN display_order INT NULL AFTER slug;

-- Lifecycle is a direct mapping of the old visibility flag, not a derived value.
UPDATE categories
SET status = CASE
    WHEN public_flg = TRUE THEN 'ACTIVE'
    ELSE 'INACTIVE'
END;

-- Slug backfill mirrors CategorySlug.normalize: trim -> lowercase -> strip diacritics (including
-- đ -> d) -> collapse every run of non-alphanumeric characters into a single dash -> trim the
-- dashes. MySQL has no Unicode decomposition function, so accented letters are transliterated below. This
-- mapping is generated from the same NFD/strip-marks rule the Java normalizer uses, so it covers the whole
-- Latin range instead of only the letters that happen to appear in one dataset. Each statement folds every
-- accented variant of one target letter over the same value, which keeps the statements reviewable and
-- avoids a single enormous nested expression.
UPDATE categories
SET slug = LOWER(TRIM(name));

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'á', 'a'), 'à', 'a'), 'ȧ', 'a'), 'â', 'a'), 'ä', 'a'), 'ǎ', 'a'), 'ă', 'a'), 'ā', 'a'), 'ã', 'a'), 'å', 'a'), 'ą', 'a'), 'ấ', 'a'), 'ầ', 'a'), 'ắ', 'a'), 'ằ', 'a'), 'ǡ', 'a'), 'ǻ', 'a'), 'ǟ', 'a'), 'ẫ', 'a'), 'ẵ', 'a'), 'ả', 'a'), 'ȁ', 'a'), 'ȃ', 'a'), 'ẩ', 'a'), 'ẳ', 'a'), 'ạ', 'a'), 'ḁ', 'a'), 'ậ', 'a'), 'ặ', 'a');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(slug, 'ḃ', 'b'), 'ḅ', 'b'), 'ḇ', 'b');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ć', 'c'), 'ċ', 'c'), 'ĉ', 'c'), 'č', 'c'), 'ç', 'c'), 'ḉ', 'c');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ḋ', 'd'), 'ď', 'd'), 'ḑ', 'd'), 'đ', 'd'), 'ḍ', 'd'), 'ḓ', 'd'), 'ḏ', 'd');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'é', 'e'), 'è', 'e'), 'ė', 'e'), 'ê', 'e'), 'ë', 'e'), 'ě', 'e'), 'ĕ', 'e'), 'ē', 'e'), 'ẽ', 'e'), 'ę', 'e'), 'ȩ', 'e'), 'ế', 'e'), 'ề', 'e'), 'ḗ', 'e'), 'ḕ', 'e'), 'ễ', 'e'), 'ḝ', 'e'), 'ẻ', 'e'), 'ȅ', 'e'), 'ȇ', 'e'), 'ể', 'e'), 'ẹ', 'e'), 'ḙ', 'e'), 'ḛ', 'e'), 'ệ', 'e');

UPDATE categories
SET slug = REPLACE(slug, 'ḟ', 'f');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ǵ', 'g'), 'ġ', 'g'), 'ĝ', 'g'), 'ǧ', 'g'), 'ğ', 'g'), 'ḡ', 'g'), 'ģ', 'g');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ḣ', 'h'), 'ĥ', 'h'), 'ḧ', 'h'), 'ȟ', 'h'), 'ḩ', 'h'), 'ḥ', 'h'), 'ḫ', 'h'), 'ẖ', 'h');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'í', 'i'), 'ì', 'i'), 'î', 'i'), 'ï', 'i'), 'ǐ', 'i'), 'ĭ', 'i'), 'ī', 'i'), 'ĩ', 'i'), 'į', 'i'), 'ḯ', 'i'), 'ỉ', 'i'), 'ȉ', 'i'), 'ȋ', 'i'), 'ị', 'i'), 'ḭ', 'i');

UPDATE categories
SET slug = REPLACE(REPLACE(slug, 'ĵ', 'j'), 'ǰ', 'j');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ḱ', 'k'), 'ǩ', 'k'), 'ķ', 'k'), 'ḳ', 'k'), 'ḵ', 'k');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ĺ', 'l'), 'ľ', 'l'), 'ļ', 'l'), 'ḷ', 'l'), 'ḽ', 'l'), 'ḻ', 'l'), 'ḹ', 'l');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(slug, 'ḿ', 'm'), 'ṁ', 'm'), 'ṃ', 'm');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ń', 'n'), 'ǹ', 'n'), 'ṅ', 'n'), 'ň', 'n'), 'ñ', 'n'), 'ņ', 'n'), 'ṇ', 'n'), 'ṋ', 'n'), 'ṉ', 'n');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ó', 'o'), 'ò', 'o'), 'ȯ', 'o'), 'ô', 'o'), 'ö', 'o'), 'ǒ', 'o'), 'ŏ', 'o'), 'ō', 'o'), 'õ', 'o'), 'ǫ', 'o'), 'ő', 'o'), 'ố', 'o'), 'ồ', 'o'), 'ṓ', 'o'), 'ṑ', 'o'), 'ṍ', 'o'), 'ȱ', 'o'), 'ȫ', 'o'), 'ỗ', 'o'), 'ṏ', 'o'), 'ȭ', 'o'), 'ǭ', 'o'), 'ỏ', 'o'), 'ȍ', 'o'), 'ȏ', 'o'), 'ơ', 'o'), 'ổ', 'o'), 'ọ', 'o'), 'ớ', 'o'), 'ờ', 'o'), 'ỡ', 'o'), 'ộ', 'o'), 'ở', 'o'), 'ợ', 'o');

UPDATE categories
SET slug = REPLACE(REPLACE(slug, 'ṕ', 'p'), 'ṗ', 'p');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ŕ', 'r'), 'ṙ', 'r'), 'ř', 'r'), 'ŗ', 'r'), 'ȑ', 'r'), 'ȓ', 'r'), 'ṛ', 'r'), 'ṟ', 'r'), 'ṝ', 'r');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ś', 's'), 'ṡ', 's'), 'ŝ', 's'), 'š', 's'), 'ṥ', 's'), 'ş', 's'), 'ṧ', 's'), 'ṣ', 's'), 'ș', 's'), 'ṩ', 's');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ṫ', 't'), 'ẗ', 't'), 'ť', 't'), 'ţ', 't'), 'ṭ', 't'), 'ț', 't'), 'ṱ', 't'), 'ṯ', 't');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ú', 'u'), 'ù', 'u'), 'û', 'u'), 'ü', 'u'), 'ǔ', 'u'), 'ŭ', 'u'), 'ū', 'u'), 'ũ', 'u'), 'ů', 'u'), 'ų', 'u'), 'ű', 'u'), 'ǘ', 'u'), 'ǜ', 'u'), 'ṹ', 'u'), 'ǚ', 'u'), 'ṻ', 'u'), 'ǖ', 'u'), 'ủ', 'u'), 'ȕ', 'u'), 'ȗ', 'u'), 'ư', 'u'), 'ụ', 'u'), 'ṳ', 'u'), 'ứ', 'u'), 'ừ', 'u'), 'ṷ', 'u'), 'ṵ', 'u'), 'ữ', 'u'), 'ử', 'u'), 'ự', 'u');

UPDATE categories
SET slug = REPLACE(REPLACE(slug, 'ṽ', 'v'), 'ṿ', 'v');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ẃ', 'w'), 'ẁ', 'w'), 'ẇ', 'w'), 'ŵ', 'w'), 'ẅ', 'w'), 'ẘ', 'w'), 'ẉ', 'w');

UPDATE categories
SET slug = REPLACE(REPLACE(slug, 'ẋ', 'x'), 'ẍ', 'x');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ý', 'y'), 'ỳ', 'y'), 'ẏ', 'y'), 'ŷ', 'y'), 'ÿ', 'y'), 'ȳ', 'y'), 'ỹ', 'y'), 'ẙ', 'y'), 'ỷ', 'y'), 'ỵ', 'y');

UPDATE categories
SET slug = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(slug, 'ź', 'z'), 'ż', 'z'), 'ẑ', 'z'), 'ž', 'z'), 'ẓ', 'z'), 'ẕ', 'z');

UPDATE categories
SET slug = REGEXP_REPLACE(slug, '[^a-z0-9]+', '-');

UPDATE categories
SET slug = TRIM(BOTH '-' FROM slug);

-- A legacy name made only of punctuation ("!!!" or "***") normalizes to nothing. Legacy rows must not be
-- lost or rejected, so they receive a deterministic fallback identifier. Application create/update instead
-- rejects an empty normalized slug so an admin fixes the name.
UPDATE categories
SET slug = CONCAT('category-', id)
WHERE slug = '';

-- Collision resolution.
--
-- Doing this in one pass is wrong: appending -{id} to the later rows of each duplicate group can land on a
-- value another row already owns. For example "Foo" -> foo and "Foo!" -> foo collide, while "Foo 2" already
-- owns foo-2, so suffixing the second row would create a second foo-2.
--
-- The candidates are therefore resolved over the whole set in one statement. Two facts make that possible
-- without any iteration:
--   * CONCAT(base, '-', id) is globally unique across rows, because the trailing -<digits> identifies the id
--     (two different rows can never produce the same trailing suffix);
--   * a plain candidate is kept only by the lowest id of its group and only when no other row's suffixed value
--     would claim it.
-- So a kept candidate is the single one for its group, every suffixed value is distinct, and no kept candidate
-- equals a suffixed value. The lowest id therefore keeps its readable slug and the rest get a stable -{id}
-- suffix, with every legacy row preserved.
--
-- The suffix is only a migration fallback that preserves every row; application create/update never adds a
-- suffix and instead reports the conflict so an admin can choose a different name or slug.
--
-- The resolved value is computed in a derived table and then copied in. MySQL rejects selecting the update
-- target directly, and a derived table is materialized before the update runs, so each row is written from an
-- already-decided value. A temporary table is deliberately not used: Liquibase may execute the statements of
-- one file on different connections, which would make a session-scoped table unavailable.
-- (Do not start a comment line with the word that introduces a Liquibase directive, or the formatted-SQL
-- parser treats it as a malformed directive instead of a comment.)
UPDATE categories c
JOIN (
    SELECT a.id,
           CASE
               WHEN a.id = (SELECT MIN(d.id) FROM categories d WHERE d.slug = a.slug)
                   AND NOT EXISTS (
                       SELECT 1 FROM categories o
                       WHERE o.id <> a.id AND CONCAT(o.slug, '-', o.id) = a.slug
                   )
               THEN a.slug
               ELSE CONCAT(a.slug, '-', a.id)
           END AS resolved_slug
    FROM categories a
) resolved ON resolved.id = c.id
SET c.slug = resolved.resolved_slug;

-- Continuous sibling order starting at 0, assigned by id inside each parent group. Root categories
-- (parent_id IS NULL) are their own group.
UPDATE categories c
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY id) - 1 AS sibling_index
    FROM categories
) ordered ON ordered.id = c.id
SET c.display_order = ordered.sibling_index;

ALTER TABLE categories
MODIFY COLUMN status VARCHAR(16) NOT NULL,
MODIFY COLUMN slug VARCHAR(160) NOT NULL,
MODIFY COLUMN display_order INT NOT NULL;

ALTER TABLE categories
ADD CONSTRAINT uk_category_slug UNIQUE (slug),
ADD INDEX idx_category_parent_order (parent_id, display_order);

-- MySQL refuses to change a foreign key action in place, so the constraint is dropped and recreated
-- with the same name. Children must never be silently promoted to roots.
ALTER TABLE categories DROP FOREIGN KEY fk_parent_category;

ALTER TABLE categories
ADD CONSTRAINT fk_parent_category FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT;

-- Dropped only after every backfill and enforcement step above succeeded.
ALTER TABLE categories DROP COLUMN public_flg;