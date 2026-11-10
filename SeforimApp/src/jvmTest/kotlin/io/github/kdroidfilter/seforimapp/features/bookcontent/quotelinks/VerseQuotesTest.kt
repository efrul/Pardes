package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class VerseQuotesTest {
    // Verses as the library keeps them: their number, teamim, maqaf, paseq and section mark
    private val dayOne =
        "(ה) וַיִּקְרָ֨א אֱלֹהִ֤ים&thinsp;<small>׀</small>&thinsp;לָאוֹר֙ י֔וֹם וְלַחֹ֖שֶׁךְ קָ֣רָא לָ֑יְלָה " +
            "וַֽיְהִי־עֶ֥רֶב וַֽיְהִי־בֹ֖קֶר י֥וֹם אֶחָֽד׃&nbsp;<span class=\"mam-spi-pe\">{פ}</span>"
    private val shema =
        "(ז) וְשִׁנַּנְתָּ֣ם לְבָנֶ֔יךָ וְדִבַּרְתָּ֖ בָּ֑ם " +
            "בְּשִׁבְתְּךָ֤ בְּבֵיתֶ֙ךָ֙ וּבְלֶכְתְּךָ֣ בַדֶּ֔רֶךְ וּֽבְשׇׁכְבְּךָ֖ וּבְקוּמֶֽךָ׃"
    private val manna = "(ד) וַיֹּ֤אמֶר יְהֹוָה֙ אֶל־מֹשֶׁ֔ה הִנְנִ֨י מַמְטִ֥יר לָכֶ֛ם לֶ֖חֶם מִן־הַשָּׁמָ֑יִם"

    private fun verse(
        id: Long,
        html: String,
    ) = Verse(id, wordsOfVerse(html))

    private fun link(
        id: Long,
        words: String,
    ) = "<a class=\"ref\" href=\"seforim://line/$id\">$words</a>"

    @Test
    fun aVersesWordsAreItsLettersAlone() {
        assertEquals(
            listOf("קרא", "אלהם", "לאר", "ם", "לחשך", "קרא", "ללה", "ה", "ערב", "ה", "בקר", "ם", "אחד"),
            wordsOfVerse(dayOne),
        )
        // A note of the verse is none of its words
        val noted =
            "(ב) צַ֤ו אֶֽת־אַהֲרֹן֙ עַל֩ מוֹקְדָ֨הֿ<sup class=\"footnote-marker\">*</sup>" +
                "<i class=\"footnote\">(בספרי תימן)</i> עַל־הַמִּזְבֵּ֤חַ"
        assertEquals(listOf("צ", "את", "אהרן", "על", "מקדה", "על", "המזבח"), wordsOfVerse(noted))
    }

    @Test
    fun theNameIsOneWordHoweverItIsWritten() {
        assertEquals("יהוה", comparedLetters("יְהֹוָה"))
        assertEquals("יהוה", comparedLetters("ה׳"))
        assertEquals("יהוה", comparedLetters("יְיָ"))
        assertEquals(comparedLetters("אֱלֹהֶיךָ"), comparedLetters("אלקיך"))
    }

    @Test
    fun aVerseQuotedInALineIsALinkToIt() {
        val line = "וְאִי בָּעֵית אֵימָא: יָלֵיף מִבְּרִיָּיתוֹ שֶׁל עוֹלָם, דִּכְתִיב: ״וַיְהִי עֶרֶב וַיְהִי בֹקֶר יוֹם אֶחָד״."
        assertEquals(
            "וְאִי בָּעֵית אֵימָא: יָלֵיף מִבְּרִיָּיתוֹ שֶׁל עוֹלָם, דִּכְתִיב: ״${link(7, "וַיְהִי עֶרֶב וַיְהִי בֹקֶר יוֹם אֶחָד")}״.",
            withVerseLinks(line, listOf(verse(7, dayOne))),
        )
    }

    @Test
    fun aQuoteSpelledAnotherWayIsStillTheVerses() {
        // The verse has וּבְשׇׁכְבְּךָ
        val line = "תַּנָּא אַקְּרָא קָאֵי, דִּכְתִיב: ״בְּשָׁכְבְּךָ וּבְקוּמֶךָ״."
        assertEquals(
            "תַּנָּא אַקְּרָא קָאֵי, דִּכְתִיב: ״${link(5252, "בְּשָׁכְבְּךָ וּבְקוּמֶךָ")}״.",
            withVerseLinks(line, listOf(verse(5252, shema))),
        )
    }

    @Test
    fun theNamesGereshIsInsideTheLink() {
        val verse = verse(9, "(יד) סוֹמֵ֣ךְ יְ֭הֹוָה לְכׇל־הַנֹּפְלִ֑ים וְ֝זוֹקֵ֗ף לְכׇל־הַכְּפוּפִֽים׃")
        assertEquals(
            "שֶׁנֶּאֱמַר: ״${link(9, "סוֹמֵךְ ה׳ לְכָל הַנֹּפְלִים")}״.",
            withVerseLinks("שֶׁנֶּאֱמַר: ״סוֹמֵךְ ה׳ לְכָל הַנֹּפְלִים״.", listOf(verse)),
        )
    }

    @Test
    fun aLineThatQuotesNothingOfItsVerseIsLeftAsItIs() {
        val line = "<big><strong>מֵאֵימָתַי</strong></big> קוֹרִין אֶת שְׁמַע בָּעֲרָבִין? מִשָּׁעָה שֶׁהַכֹּהֲנִים נִכְנָסִים."
        assertSame(line, withVerseLinks(line, listOf(verse(5252, shema), verse(7, dayOne))))
        assertSame(line, withVerseLinks(line, emptyList()))
        // One word of the verse, in no quote, is no quote of it
        val word = "אָמַר רַב: לֶחֶם אָכְלוּ."
        assertSame(word, withVerseLinks(word, listOf(verse(4, manna))))
    }

    @Test
    fun aWordQuotedByItselfIsALinkWhenOneVerseAloneHasIt() {
        val line = "כְּתִיב ״לֶחֶם״, וּכְתִיב ״עוּגוֹת״."
        assertEquals("כְּתִיב ״${link(4, "לֶחֶם")}״, וּכְתִיב ״עוּגוֹת״.", withVerseLinks(line, listOf(verse(4, manna))))
        // Two verses have it: no telling which is meant
        val other = verse(6, "(כב) לָֽקְט֥וּ לֶ֙חֶם֙ מִשְׁנֶ֔ה")
        assertSame(line, withVerseLinks(line, listOf(verse(4, manna), other)))
    }

    @Test
    fun eachVerseHasItsQuotesTheLongerFirst() {
        val line =
            "״בְּשִׁבְתְּךָ בְּבֵיתֶךָ״ — פְּרָט לְעוֹסֵק בְּמִצְוָה. ״וּבְלֶכְתְּךָ בַדֶּרֶךְ״ — פְּרָט לְחָתָן, " +
                "וּכְתִיב: ״וַיְהִי עֶרֶב וַיְהִי בֹקֶר״."
        val sitting = link(5252, "בְּשִׁבְתְּךָ בְּבֵיתֶךָ")
        val walking = link(5252, "וּבְלֶכְתְּךָ בַדֶּרֶךְ")
        val evening = link(7, "וַיְהִי עֶרֶב וַיְהִי בֹקֶר")
        assertEquals(
            "״$sitting״ — פְּרָט לְעוֹסֵק בְּמִצְוָה. ״$walking״ — פְּרָט לְחָתָן, וּכְתִיב: ״$evening״.",
            withVerseLinks(line, listOf(verse(5252, shema), verse(7, dayOne))),
        )
    }

    @Test
    fun theTalmudsOwnWordsAreNoQuoteThoughTheVerseHasThem() {
        val feast = verse(8, "(י) וַיֹּ֣אמֶר לָהֶ֡ם לְכוּ֩ אִכְל֨וּ מַשְׁמַנִּ֜ים וּשְׁת֣וּ מַֽמְתַקִּ֗ים")
        // The verse has וַיֹּאמֶר לָהֶם too, before the words quoted
        assertEquals(
            "אָמַר לָהֶם: ״${link(8, "לְכוּ אִכְלוּ מַשְׁמַנִּים")}״, וְהָלְכוּ.",
            withVerseLinks("אָמַר לָהֶם: ״לְכוּ אִכְלוּ מַשְׁמַנִּים״, וְהָלְכוּ.", listOf(feast)),
        )
        // Two words of a verse in no quote, in a line that marks its quotes
        val stars = verse(2, "(טו) מֵעֲל֣וֹת הַשַּׁ֔חַר עַ֖ד צֵ֥את הַכּוֹכָבִֽים׃")
        val dawn = link(2, "מֵעֲלוֹת הַשַּׁחַר")
        assertEquals(
            "סִימָן לַדָּבָר: צֵאת הַכּוֹכָבִים, שֶׁנֶּאֱמַר: ״$dawn״.",
            withVerseLinks("סִימָן לַדָּבָר: צֵאת הַכּוֹכָבִים, שֶׁנֶּאֱמַר: ״מֵעֲלוֹת הַשַּׁחַר״.", listOf(stars)),
        )
        // A line that marks no quote: two words are one
        assertEquals("דִּכְתִיב $dawn וגו׳.", withVerseLinks("דִּכְתִיב מֵעֲלוֹת הַשַּׁחַר וגו׳.", listOf(stars)))
    }

    @Test
    fun aVerseOfTheLinesBeforeIsToldByThreeWords() {
        val watch = verse(12, "(טז) וְהָֽיוּ־לָ֧נוּ הַלַּ֛יְלָה מִשְׁמָ֖ר וְהַיּ֥וֹם מְלָאכָֽה׃")
        val quote = link(12, "וְהָיוּ לָנוּ הַלַּיְלָה מִשְׁמָר וְהַיּוֹם מְלָאכָה")
        // The library links the verse to the line that quotes it first: the next line quotes it again
        val again = "וְכִי תֵּימָא לֵילְיָא הוּא, תָּא שְׁמַע ״וְהָיוּ לָנוּ הַלַּיְלָה מִשְׁמָר וְהַיּוֹם מְלָאכָה״."
        assertEquals("וְכִי תֵּימָא לֵילְיָא הוּא, תָּא שְׁמַע ״$quote״.", withVerseLinks(again, emptyList(), listOf(watch)))
        // Two words of it are not enough, nor one quoted by itself
        val two = "מַאי ״הַלַּיְלָה מִשְׁמָר״, וּמַאי ״מְלָאכָה״?"
        assertSame(two, withVerseLinks(two, emptyList(), listOf(watch)))
        assertEquals(
            "מַאי ״${link(12, "הַלַּיְלָה מִשְׁמָר")}״, וּמַאי ״${link(12, "מְלָאכָה")}״?",
            withVerseLinks(two, listOf(watch)),
        )
        // The line's own verse first
        val own = verse(13, "(א) וְהָיוּ לָנוּ הַלַּיְלָה מִשְׁמָר")
        assertEquals(
            "״${link(13, "וְהָיוּ לָנוּ הַלַּיְלָה מִשְׁמָר")}״",
            withVerseLinks("״וְהָיוּ לָנוּ הַלַּיְלָה מִשְׁמָר״", listOf(own), listOf(watch)),
        )
    }

    @Test
    fun aLinkCrossesNoTagAndNoQuoteMark() {
        val verse = verse(3, "(יא) וְלֹֽא־תִשְׁכַּ֣ח אֶת־אֲמָתֶ֔ךָ וְנָתַתָּ֥ה לַאֲמָתְךָ֖ זֶ֣רַע אֲנָשִׁ֑ים")
        // Two quotes one after the other, of words that follow one another in the verse
        assertEquals(
            "״${link(3, "תִשְׁכַּח אֶת אֲמָתֶךָ")}״. ״${link(3, "וְנָתַתָּה לַאֲמָתְךָ")}״.",
            withVerseLinks("״תִשְׁכַּח אֶת אֲמָתֶךָ״. ״וְנָתַתָּה לַאֲמָתְךָ״.", listOf(verse)),
        )
        assertEquals(
            "״<b>${link(3, "תִשְׁכַּח אֶת")}</b> ${link(3, "אֲמָתֶךָ")}״",
            withVerseLinks("״<b>תִשְׁכַּח אֶת</b> אֲמָתֶךָ״", listOf(verse)),
        )
    }

    @Test
    fun theWordsOfALinkTheLineAlreadyHasAreLeftToIt() {
        val line = "שֶׁנֶּאֱמַר <a class=\"ref\" href=\"seforim://line/5252\">(בְּשִׁבְתְּךָ בְּבֵיתֶךָ)</a> וְגוֹ׳."
        assertSame(line, withVerseLinks(line, listOf(verse(5252, shema))))
    }
}
