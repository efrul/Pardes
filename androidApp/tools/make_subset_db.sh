#!/usr/bin/env bash
# Builds a small development copy of seforim.db for the Android app.
#
# Usage: make_subset_db.sh <full seforim.db> <output subset.db> [base book ids...]
#
# Keeps the given base books (default: Bereshit, Berakhot (Bavli), Mishnah Berakhot),
# every book linked to them as COMMENTARY / TARGUM (CONN_TYPES, up to MAX_LINES lines), all of their
# lines, TOC, alt-TOC and metadata, the links between kept books, and only the
# categories that are ancestors of kept books.
set -euo pipefail

SRC="$1"
OUT="$2"
shift 2
BASE_IDS="${*:-1 103 40}"
BASE_LIST=$(echo "$BASE_IDS" | tr ' ' ',')
CONN_TYPES="${CONN_TYPES:-1,3}"   # COMMENTARY, TARGUM
MAX_LINES="${MAX_LINES:-10000}"    # skip very large linked books
FULL_LINES="${FULL_LINES:-3000}"   # linked books larger than this keep only linked lines

rm -f "$OUT" "$OUT-wal" "$OUT-shm"

# Same schema (tables, indexes, views) as the full database.
sqlite3 -readonly "$SRC" ".schema" | grep -v '^CREATE TABLE sqlite_' > "$OUT.schema.sql"
sqlite3 "$OUT" "PRAGMA page_size=4096;" < /dev/null
sqlite3 "$OUT" < "$OUT.schema.sql"
rm -f "$OUT.schema.sql"

sqlite3 "$OUT" <<SQL
PRAGMA foreign_keys=OFF;
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
ATTACH DATABASE '$SRC' AS src;

CREATE TEMP TABLE keep_book(id INTEGER PRIMARY KEY);
INSERT INTO keep_book SELECT id FROM src.book WHERE id IN ($BASE_LIST);
INSERT OR IGNORE INTO keep_book
  SELECT DISTINCT l.targetBookId FROM src.link l JOIN src.book b ON b.id = l.targetBookId
  WHERE l.sourceBookId IN ($BASE_LIST) AND l.connectionTypeId IN ($CONN_TYPES) AND b.totalLines <= $MAX_LINES;
INSERT OR IGNORE INTO keep_book
  SELECT DISTINCT l.sourceBookId FROM src.link l JOIN src.book b ON b.id = l.sourceBookId
  WHERE l.targetBookId IN ($BASE_LIST) AND l.connectionTypeId IN ($CONN_TYPES) AND b.totalLines <= $MAX_LINES;

-- Categories: every ancestor of a kept book's category.
CREATE TEMP TABLE keep_cat(id INTEGER PRIMARY KEY);
INSERT OR IGNORE INTO keep_cat
  SELECT cc.ancestorId FROM src.category_closure cc
  JOIN src.book b ON b.categoryId = cc.descendantId
  WHERE b.id IN (SELECT id FROM keep_book);
INSERT OR IGNORE INTO keep_cat SELECT categoryId FROM src.book WHERE id IN (SELECT id FROM keep_book);

INSERT INTO category SELECT * FROM src.category WHERE id IN (SELECT id FROM keep_cat);
INSERT INTO category_closure SELECT * FROM src.category_closure
  WHERE ancestorId IN (SELECT id FROM keep_cat) AND descendantId IN (SELECT id FROM keep_cat);

-- Small reference tables, copied whole.
INSERT INTO connection_type SELECT * FROM src.connection_type;
INSERT INTO schema_meta SELECT * FROM src.schema_meta;
INSERT INTO source SELECT * FROM src.source;
INSERT INTO license SELECT * FROM src.license;
INSERT INTO edition SELECT * FROM src.edition;
INSERT INTO author SELECT * FROM src.author;
INSERT INTO author_alias SELECT * FROM src.author_alias;
INSERT INTO author_bio SELECT * FROM src.author_bio;
INSERT INTO topic SELECT * FROM src.topic;
INSERT INTO pub_date SELECT * FROM src.pub_date;
INSERT INTO pub_place SELECT * FROM src.pub_place;

-- Books and per-book tables.
INSERT INTO book SELECT * FROM src.book WHERE id IN (SELECT id FROM keep_book);
-- Base books and small linked books are kept whole; large linked books (> FULL_LINES)
-- keep only the lines linked to/from a base-book line.
CREATE TEMP TABLE keep_line(id INTEGER PRIMARY KEY);
INSERT OR IGNORE INTO keep_line SELECT l.id FROM src.line l JOIN src.book b ON b.id = l.bookId
  WHERE b.id IN (SELECT id FROM keep_book) AND (b.id IN ($BASE_LIST) OR b.totalLines <= $FULL_LINES);
INSERT OR IGNORE INTO keep_line SELECT k.targetLineId FROM src.link k
  WHERE k.sourceBookId IN ($BASE_LIST) AND k.targetBookId IN (SELECT id FROM keep_book);
INSERT OR IGNORE INTO keep_line SELECT k.sourceLineId FROM src.link k
  WHERE k.targetBookId IN ($BASE_LIST) AND k.sourceBookId IN (SELECT id FROM keep_book);
INSERT INTO line SELECT * FROM src.line WHERE id IN (SELECT id FROM keep_line);
INSERT INTO tocEntry SELECT * FROM src.tocEntry WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO tocText SELECT * FROM src.tocText WHERE id IN (SELECT textId FROM tocEntry);
INSERT INTO line_toc SELECT lt.* FROM src.line_toc lt JOIN line l ON l.id = lt.lineId;
INSERT INTO alt_toc_structure SELECT * FROM src.alt_toc_structure WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO alt_toc_entry SELECT * FROM src.alt_toc_entry WHERE structureId IN (SELECT id FROM alt_toc_structure);
INSERT OR IGNORE INTO tocText SELECT * FROM src.tocText WHERE id IN (SELECT textId FROM alt_toc_entry);
INSERT INTO line_alt_toc SELECT la.* FROM src.line_alt_toc la JOIN line l ON l.id = la.lineId;
INSERT INTO book_acronym SELECT * FROM src.book_acronym WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO book_author SELECT * FROM src.book_author WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO book_topic SELECT * FROM src.book_topic WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO book_pub_date SELECT * FROM src.book_pub_date WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO book_pub_place SELECT * FROM src.book_pub_place WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO book_edition SELECT * FROM src.book_edition WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO book_has_links SELECT * FROM src.book_has_links WHERE bookId IN (SELECT id FROM keep_book);
INSERT INTO default_commentator SELECT * FROM src.default_commentator
  WHERE bookId IN (SELECT id FROM keep_book) AND commentatorBookId IN (SELECT id FROM keep_book);
INSERT INTO default_targum SELECT * FROM src.default_targum
  WHERE bookId IN (SELECT id FROM keep_book) AND targumBookId IN (SELECT id FROM keep_book);

-- Links whose both ends are kept.
INSERT INTO link SELECT * FROM src.link
  WHERE sourceBookId IN (SELECT id FROM keep_book) AND targetBookId IN (SELECT id FROM keep_book)
    AND sourceLineId IN (SELECT id FROM keep_line) AND targetLineId IN (SELECT id FROM keep_line);

DETACH DATABASE src;
SQL

sqlite3 "$OUT" "PRAGMA journal_mode=DELETE; VACUUM; ANALYZE;"
echo "books=$(sqlite3 "$OUT" 'select count(*) from book') lines=$(sqlite3 "$OUT" 'select count(*) from line') links=$(sqlite3 "$OUT" 'select count(*) from link') size=$(du -h "$OUT" | cut -f1)"
