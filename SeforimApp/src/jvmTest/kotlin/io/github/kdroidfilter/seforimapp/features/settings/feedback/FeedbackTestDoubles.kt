package io.github.kdroidfilter.seforimapp.features.settings.feedback

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.sql.DriverManager
import java.util.concurrent.CopyOnWriteArrayList

/** The feedback service as the Worker answers (feedback-service/src/index.js), in memory: what it took, and its moods. */
internal class ServiceDouble(
    url: String? = "https://feedback.test",
) {
    val received = CopyOnWriteArrayList<FeedbackPackage>()
    val deleted = CopyOnWriteArrayList<String>()

    /** No network: the engine throws, as CIO does without one. */
    @Volatile var offline = false

    /** The service refuses with this. */
    @Volatile var refusal: HttpStatusCode? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")

    val client =
        HttpClient(
            MockEngine { request ->
                if (offline) throw IOException("offline")
                refusal?.let { return@MockEngine respond("""{"error":"refused"}""", it, headers) }
                val path = request.url.encodedPath
                when {
                    request.method == HttpMethod.Post && path == "/v1/packages" -> {
                        received += json.decodeFromString(FeedbackPackage.serializer(), (request.body as TextContent).text)
                        respond("""{"id":${received.size}}""", HttpStatusCode.Created, headers)
                    }
                    request.method == HttpMethod.Delete && path.startsWith("/v1/senders/") -> {
                        val sender = path.substringAfterLast('/')
                        val count = received.count { it.sender == sender }
                        received.removeIf { it.sender == sender }
                        deleted += sender
                        respond("""{"deleted":$count}""", HttpStatusCode.OK, headers)
                    }
                    else -> respond("""{"error":"not found"}""", HttpStatusCode.NotFound, headers)
                }
            },
        )

    val service = FeedbackService(client, url)
}

internal fun feedbackDatabase() = UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))

internal fun UserSettingsDb.grade(
    query: String,
    lineId: Long,
    at: Long,
) = searchFeedbackQueries.upsertGrade(
    searchKey = "search::$query",
    query = query,
    lineId = lineId,
    bookId = 7,
    grade = 2,
    rank = 0,
    searchCategoryId = null,
    searchBookId = null,
    searchTocId = null,
    searchExtended = false,
    model = null,
    gradedAt = at,
)

internal fun UserSettingsDb.search(
    query: String,
    at: Long,
) = visitHistoryQueries.upsertVisit(
    key = "search:$query",
    kind = "search",
    bookId = null,
    searchQuery = query,
    title = query,
    visitedAt = at,
    tocEntryId = null,
    lineId = null,
    searchCategoryId = null,
    searchBookId = null,
    searchTocId = null,
    searchExtended = false,
)

/** A Zayit user database in [folder] whose history holds [searches], each with when it was made. */
internal fun zayitDatabase(
    folder: File,
    vararg searches: Pair<String, Long>,
): File {
    val file = File(folder.apply { mkdirs() }, "user_settings.db")
    DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE visit_history (key TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, searchQuery TEXT, " +
                    "visitedAt INTEGER NOT NULL, visitCount INTEGER NOT NULL DEFAULT 1)",
            )
            searches.forEach { (query, at) ->
                statement.execute("INSERT INTO visit_history VALUES ('search:g:$query', 'search', '$query', $at, 1)")
            }
        }
    }
    return file
}

/** This sender looking for the other apps where the tests say, not on the machine they run on: [zayit]'s copies, no Otzaria. */
internal fun FeedbackSender.withOtherApps(zayit: List<File> = emptyList()): FeedbackSender =
    apply {
        this.zayit = ZayitSearches { zayit }
        this.otzaria = OtzariaSearches { emptyList() }
    }

/** Waits, a while at most, for [done]. */
internal suspend fun eventually(done: () -> Boolean) =
    withTimeout(10_000) {
        while (!done()) delay(20)
    }
