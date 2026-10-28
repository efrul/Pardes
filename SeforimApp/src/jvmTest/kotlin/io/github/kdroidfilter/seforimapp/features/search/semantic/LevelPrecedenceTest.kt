package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimapp.features.search.semantic.BookLevels.ACHARONIM
import io.github.kdroidfilter.seforimapp.features.search.semantic.BookLevels.CHAZAL
import io.github.kdroidfilter.seforimapp.features.search.semantic.BookLevels.CONTEMPORARY
import io.github.kdroidfilter.seforimapp.features.search.semantic.BookLevels.MIKRA
import io.github.kdroidfilter.seforimapp.features.search.semantic.BookLevels.RISHONIM
import io.github.kdroidfilter.seforimapp.features.search.semantic.BookLevels.UNKNOWN
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The precedence of the earlier level among the lines that hold the query as a sentence ([LevelPrecedence], as
 * semantic-model/eval-real/level_order.py measures it), the books' levels ([BookLevels]), the library they are read from
 * ([LibraryLines]) and the lines' keys ([HybridSearchEngine.verbatimKey]).
 */
class LevelPrecedenceTest {
    // A page of lines given as (text, level), each line's id its place
    private fun order(
        query: String,
        vararg lines: Pair<String, Int>,
    ): List<Int> = LevelPrecedence.order(lines.indices.toList(), query, { lines[it].first }, { lines[it].second })

    private val other = "ענין אחר לגמרי"

    @Test
    fun `the Gemara holding a sentence comes before the Acharonim quoting it`() {
        val query = "אין עומדין להתפלל אלא מתוך כובד ראש"
        val page =
            order(
                query,
                "כדאיתא בגמרא אין עומדין להתפלל אלא מתוך כובד ראש" to ACHARONIM,
                other to ACHARONIM,
                "מתני׳ אֵין עוֹמְדִין לְהִתְפַּלֵּל אֶלָּא מִתּוֹךְ כֹּבֶד רֹאשׁ" to CHAZAL,
            )
        assertEquals(listOf(2, 1, 0), page)
    }

    @Test
    fun `the earliest holder below the first places takes the first when the line there is later`() {
        val query = "אין עומדין להתפלל אלא מתוך כובד ראש"
        val lines = List(30) { other to ACHARONIM }.toMutableList()
        lines[25] = "אין עומדין להתפלל אלא מתוך כובד ראש" to CHAZAL
        assertEquals(listOf(25) + (0..24) + (26..29), order(query, *lines.toTypedArray()))
        // A verse first stays first: no level is earlier
        lines[0] = other to MIKRA
        assertEquals((0..29).toList(), order(query, *lines.toTypedArray()))
    }

    @Test
    fun `holders of one level keep the fused order`() {
        val query = "ואהבת לרעך כמוך"
        val page =
            order(
                query,
                other to CHAZAL,
                "ואהבת לרעך כמוך זה כלל גדול" to ACHARONIM,
                other to RISHONIM,
                "וכתיב ואהבת לרעך כמוך" to ACHARONIM,
            )
        assertEquals(listOf(0, 1, 2, 3), page)
    }

    @Test
    fun `holders trade the places holders take in the first twenty, the others come right after them`() {
        val holders = setOf(5, 25)
        val level = mapOf(5 to ACHARONIM, 25 to CHAZAL)
        val place = (0..29).associateWith { it }
        val earliest = compareBy<Int>({ level[it] ?: UNKNOWN }, { place.getValue(it) })
        assertEquals(
            (0..4) + 25 + (6..19) + 5 + (20..24) + (26..29),
            LevelPrecedence.byLevel((0..29).toList(), holders, earliest),
        )
        // No holder in the first twenty: the page as it is
        assertEquals((0..29).toList(), LevelPrecedence.byLevel((0..29).toList(), setOf(25), earliest))
    }

    @Test
    fun `of two words only a verse holding them as written takes the first place`() {
        // "בראשית ברא": the Midrash quoting the verse ranked first, the verse third
        val page =
            order(
                "בראשית ברא",
                "בראשית ברא אלהים, בזכות התורה" to CHAZAL,
                other to ACHARONIM,
                "(א) <big>בְּ</big>רֵאשִׁ֖ית בָּרָ֣א אֱלֹהִ֑ים אֵ֥ת הַשָּׁמַ֖יִם" to MIKRA,
            )
        assertEquals(listOf(2, 0, 1), page)
        // Two words are more a subject than a sentence: the Gemara holding them doesn't come before the Acharonim
        assertEquals(listOf(0, 1), order("דין תפילה", other to ACHARONIM, "ומדין תפילה למדנו" to CHAZAL))
        // A verse that holds them only spelling apart takes no place
        assertEquals(listOf(0, 1), order("דין תפילה", other to ACHARONIM, "מדין תפלה" to MIKRA))
    }

    @Test
    fun `a page without a sentence held stays as it is`() {
        assertEquals(listOf(0, 1), order("שבת", other to ACHARONIM, "שבת" to MIKRA))
        assertEquals(listOf(0, 1), order("ואהבת לרעך כמוך", other to ACHARONIM, "ואהבת את רעך" to CHAZAL))
        assertEquals(emptyList(), order("ואהבת לרעך כמוך"))
    }

    @Test
    fun `a sentence is held in a row and in order, a line's word with or without prefixes`() {
        assertTrue(LevelPrecedence.holdsPhrase("ואהבת לרעך כמוך זה כלל גדול", "ואהבת לרעך כמוך", spelling = false))
        assertFalse(LevelPrecedence.holdsPhrase("לרעך ואהבת כמוך", "ואהבת לרעך כמוך", spelling = false))
        assertFalse(LevelPrecedence.holdsPhrase("ואהבת את לרעך כמוך", "ואהבת לרעך כמוך", spelling = false))
        assertFalse(LevelPrecedence.holdsPhrase("ואהבת לרעך", "ואהבת לרעך כמוך", spelling = false))
        // The line's word without one or two prefixes, the query's without its own
        assertTrue(LevelPrecedence.holdsPhrase("ובבית המקדש היו", "בית המקדש", spelling = false))
        assertTrue(LevelPrecedence.holdsPhrase("תורה מן השמים", "ולתורה מן השמים", spelling = false))
        assertFalse(LevelPrecedence.holdsPhrase("רבית המקדש", "בית המקדש", spelling = false))
    }

    @Test
    fun `spelling apart, a sentence is held in full and defective spelling and with the divine name as written`() {
        assertFalse(LevelPrecedence.holdsPhrase("צדק צדק תרדף", "צדק צדק תרדוף", spelling = false))
        assertTrue(LevelPrecedence.holdsPhrase("צדק צדק תרדף", "צדק צדק תרדוף", spelling = true))
        // The Mishnah's plural
        assertTrue(LevelPrecedence.holdsPhrase("בית שמאי אומרין", "בית שמאי אומרים", spelling = true))
        assertTrue(LevelPrecedence.holdsPhrase("אָנֹכִי יְהוָה אֱלֹהֶיךָ", "ה' אלהיך", spelling = true))
        assertTrue(LevelPrecedence.holdsPhrase("בראשית ברא אלהים", "ברא אלוקים", spelling = true))
        // The skeleton of the query's word as written: מגדל is no גדול
        assertTrue(LevelPrecedence.holdsPhrase("מגדל עוז שם ה'", "מגדל עוז", spelling = true))
        assertFalse(LevelPrecedence.holdsPhrase("גדול עוז", "מגדל עוז", spelling = true))
    }

    // What a line's word may stand for, as level_order.py's forms
    private fun formsOf(word: String): Set<String> =
        buildSet {
            add(word)
            if (word.length > 1 && word[0] in "והבכלמש") {
                add(word.substring(1))
                if (word.length > 2 && word[1] in "והבכלמש") add(word.substring(2))
            }
        }

    @Test
    fun `a line's word is matched as its forms and their skeletons would be, without making them`() {
        val words = listOf("ובבית", "בבית", "בית", "רבית", "והשמימ", "שמימ", "תרדופ", "תרדפ", "אומרינ", "ינ", "וינ", "ו", "מי", "שבתות")
        val forms = words.flatMap(::formsOf).toSet()
        for (word in words) {
            for (form in forms) {
                assertEquals(form in formsOf(word), LevelPrecedence.isFormOf(word, form), "$word as $form")
                val skeleton = LevelPrecedence.skeleton(form)
                val expected = formsOf(word).any { LevelPrecedence.skeleton(it) == skeleton }
                assertEquals(expected, LevelPrecedence.hasSkeleton(word, skeleton), "$word with the skeleton $skeleton")
            }
        }
    }

    @Test
    fun `skeletons and the divine name`() {
        assertEquals("תרדפ", LevelPrecedence.skeleton("תרדופ"))
        assertEquals("אמרמ", LevelPrecedence.skeleton("אומרינ"))
        assertEquals("יהה", LevelPrecedence.skeleton("יהוה"))
        assertEquals(setOf("ה", "יהוה", "יי", "השמ"), LevelPrecedence.names("ה"))
        assertEquals(setOf("אלוקינו", "אלהינו"), LevelPrecedence.names("אלוקינו"))
        assertEquals(setOf("אלוק", "אלוה"), LevelPrecedence.names("אלוק"))
        assertEquals(setOf("שבת"), LevelPrecedence.names("שבת"))
    }

    @Test
    fun `in a line's key a tag inside a word leaves it whole, one that ends a line and an entity part words`() {
        assertEquals("בראשית ברא", HybridSearchEngine.verbatimKey("<big>בְּ</big>רֵאשִׁית <span class=\"x\">בָּרָא</span>"))
        assertEquals(
            "אחד שנימ שלשה ארבעה חמשה ששה",
            HybridSearchEngine.verbatimKey("אחד<br>שנים</p>שלשה<BR />ארבעה</div>חמשה<br\n/>ששה"),
        )
        assertEquals("אחד שנימ שלשה", HybridSearchEngine.verbatimKey("אחד&nbsp;שנים&#160;שלשה&amp;"))
        // No entity, no tag: punctuation
        assertEquals("at t x y", HybridSearchEngine.verbatimKey("AT&T x < y"))
        assertEquals("ab", HybridSearchEngine.verbatimKey("a<i>b</i>"))
        assertEquals("שלומ עולמ 12", HybridSearchEngine.verbatimKey("  שָׁלוֹם, עוֹלָם! ״12״ "))
        assertEquals("", HybridSearchEngine.verbatimKey("<p></p>"))
    }

    @Test
    fun `a book's level by its authors, the Tanakh, a category named for an era, a compilation, Chazal`() {
        val tanakh = "תנ״ך"
        assertEquals(MIKRA, BookLevels.levelOf("בראשית", listOf(tanakh, "תורה"), emptyList()))
        assertEquals(CHAZAL, BookLevels.levelOf("ברכות", listOf("תלמוד", "בבלי", "סדר זרעים"), emptyList()))
        assertEquals(CHAZAL, BookLevels.levelOf("בראשית רבה", listOf("מדרש", "אגדה", "מדרש רבה"), emptyList()))
        assertEquals(CHAZAL, BookLevels.levelOf("תרגום אונקלוס על בראשית", listOf(tanakh, "תרגומים", "תרגום אונקלוס", "תורה"), emptyList()))
        assertEquals(RISHONIM, BookLevels.levelOf("רש\"י על בראשית", listOf(tanakh, "ראשונים", "רש״י", "תורה"), listOf("RI")))
        assertEquals(RISHONIM, BookLevels.levelOf("רש\"י על בראשית", listOf(tanakh, "ראשונים", "רש״י", "תורה"), emptyList()))
        assertEquals(ACHARONIM, BookLevels.levelOf("משנה ברורה", listOf("הלכה", "שולחן ערוך", "מפרשים"), listOf("AH")))
        assertEquals(CONTEMPORARY, BookLevels.levelOf("ספר", listOf("מחשבת ישראל", "מחברי זמננו"), emptyList()))
        // The latest of its authors' eras, before its categories
        assertEquals(ACHARONIM, BookLevels.levelOf("ספר", listOf(tanakh, "תורה"), listOf("RI", "AH")))
        assertEquals(ACHARONIM, BookLevels.levelOf("עין יעקב", listOf("מדרש", "אגדה"), listOf("AH")))
        assertEquals(RISHONIM, BookLevels.levelOf("ילקוט שמעוני על התורה", listOf("מדרש", "אגדה"), emptyList()))
        // Chazal's categories, but for the commentaries and notes in them
        assertEquals(UNKNOWN, BookLevels.levelOf("ספר", listOf("מדרש", "אגדה", "מדרש לקח טוב", "מפרשים"), emptyList()))
        assertEquals(UNKNOWN, BookLevels.levelOf("הערות על מדרש", listOf("מדרש", "אגדה"), emptyList()))
        assertEquals(UNKNOWN, BookLevels.levelOf("הלכות גדולות", listOf("הלכה"), emptyList()))
        assertEquals(ACHARONIM, UNKNOWN)
    }

    @Test
    fun `the library's levels and lines are read from the database beside its index`() {
        val dir = createTempDirectory()
        try {
            val db = dir.resolve("seforim.db")
            DriverManager.getConnection("jdbc:sqlite:$db").use { con ->
                con.createStatement().use { st ->
                    st.executeUpdate("CREATE TABLE category (id INTEGER PRIMARY KEY, parentId INTEGER, title TEXT)")
                    st.executeUpdate("CREATE TABLE author (id INTEGER PRIMARY KEY, era TEXT)")
                    st.executeUpdate("CREATE TABLE book (id INTEGER PRIMARY KEY, categoryId INTEGER, title TEXT)")
                    st.executeUpdate("CREATE TABLE book_author (bookId INTEGER, authorId INTEGER)")
                    st.executeUpdate("CREATE TABLE line (id INTEGER PRIMARY KEY, bookId INTEGER, content TEXT)")
                    st.executeUpdate(
                        "INSERT INTO category VALUES (1, NULL, 'תנ״ך'), (2, 1, 'תורה'), (3, 1, 'ראשונים'), (4, NULL, 'תלמוד'), " +
                            "(5, 4, 'בבלי'), (6, NULL, 'הלכה')",
                    )
                    st.executeUpdate("INSERT INTO author VALUES (1, 'RI'), (2, 'AH'), (3, NULL)")
                    st.executeUpdate("INSERT INTO book VALUES (1, 2, 'בראשית'), (2, 3, 'רש\"י על בראשית'), (3, 5, 'ברכות'), (4, 6, 'ספר')")
                    st.executeUpdate("INSERT INTO book_author VALUES (2, 1), (4, 1), (4, 2), (3, 3)")
                }
                con.prepareStatement("INSERT INTO line VALUES (?, ?, ?)").use { ps ->
                    for (id in 1..1000) {
                        ps.setLong(1, id.toLong())
                        ps.setLong(2, (id % 4 + 1).toLong())
                        ps.setString(3, "שורה $id")
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            val library = assertNotNull(LibraryLines.beside(dir.resolve("seforim.db.lucene")))
            library.use {
                assertEquals(mapOf(1L to MIKRA, 2L to RISHONIM, 3L to CHAZAL, 4L to ACHARONIM), it.levels())
                // More ids than a statement takes, and one the library hasn't
                val lines = it.lines((1L..1000L) + 5000L)
                assertEquals((1L..1000L).toSet(), lines.keys)
                assertEquals(3L, lines.getValue(10).bookId)
                assertEquals("שורה 10", lines.getValue(10).text)
            }
            assertNull(LibraryLines.beside(dir.resolve("other.db.lucene")))
            assertNull(LibraryLines.beside(dir.resolve("seforim.db.lookup")))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
