-- Legacy fixture for the Category CR1 migration (changeset-6.sql).
--
-- This is the pre-CR1 shape of `categories`: changeset-1 plus the changeset-3/5 additions. It is loaded by
-- CategoryMigrationTest, which executes changeset-6.sql against it on a real MySQL server. It can also be run by
-- hand:
--
--   docker exec mysql mysql -uroot -prootpass -e "DROP DATABASE IF EXISTS catmig_verify; CREATE DATABASE catmig_verify CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
--   docker cp src/test/resources/db/category-cr1-legacy-fixture.sql mysql:/tmp/f.sql
--   docker exec mysql bash -c "mysql -uroot -prootpass --default-character-set=utf8mb4 catmig_verify < /tmp/f.sql"
--   docker cp src/main/resources/db/changelog/changesets/changeset-6.sql mysql:/tmp/c6.sql
--   docker exec mysql bash -c "mysql -uroot -prootpass --default-character-set=utf8mb4 catmig_verify < /tmp/c6.sql"
--
-- Row shapes that must be covered:
--   * "Foo" / "Foo!" normalize to the same slug while "Foo 2" already owns the value the -{id} suffix would use
--     for id 2, so a single-pass suffix would produce foo-2 twice.
--   * ids 4 and 12 collide across the two collision sources: id 4 is punctuation-only and falls back to
--     `category-4`, while id 12 "Category 4" normalizes to exactly `category-4`. The fallback collision must be
--     broken up just like the normalization collision.
--   * Vietnamese diacritics, including the stroke d, on both short and long names.
--   * mixed public_flg values and two levels of parent/child for the order and FK checks.
--
-- Expected result after changeset-6 (ids 1..12), where order is display_order:
--   id  slug                                       status    order  parent
--    1  foo                                        ACTIVE        0  -
--    2  foo-2                                      ACTIVE        1  -
--    3  foo-2-3                                    ACTIVE        2  -
--    4  category-4                                 INACTIVE      3  -
--    5  dien-thoai                                 ACTIVE        4  -
--    6  dien-thoai-6                               ACTIVE        5  -
--    7  giay-dep                                   ACTIVE        6  -
--    8  may-anh                                    ACTIVE        0  1
--    9  ong-kinh                                   ACTIVE        1  1
--   10  phu-kien-cho-dien-thoai-va-may-tinh-bang   INACTIVE      0  2
--   11  category-11                                INACTIVE      7  -
--   12  category-4-12                              ACTIVE        8  -

CREATE TABLE categories (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  public_flg BOOLEAN NOT NULL DEFAULT FALSE,
  image_id VARCHAR(36) NULL,
  parent_id INT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT NULL,
  UNIQUE KEY name (name),
  KEY fk_parent_category (parent_id),
  CONSTRAINT fk_parent_category FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE SET NULL
) ENGINE = InnoDB;

-- created_at is explicit because changeset-3 made it NOT NULL without a default.
INSERT INTO categories (name, public_flg, parent_id, created_at) VALUES
 ('Foo', TRUE, NULL, NOW()),
 ('Foo!', TRUE, NULL, NOW()),
 ('Foo 2', TRUE, NULL, NOW()),
 ('!!!', FALSE, NULL, NOW()),
 ('Điện thoại', TRUE, NULL, NOW()),
 ('Điện thoại!', TRUE, NULL, NOW()),
 ('Giày dép', TRUE, NULL, NOW()),
 ('Máy ảnh', TRUE, 1, NOW()),
 ('Ống kính', TRUE, 1, NOW()),
 ('Phụ kiện cho điện thoại và máy tính bảng', FALSE, 2, NOW()),
 ('***', FALSE, NULL, NOW()),
 ('Category 4', TRUE, NULL, NOW());