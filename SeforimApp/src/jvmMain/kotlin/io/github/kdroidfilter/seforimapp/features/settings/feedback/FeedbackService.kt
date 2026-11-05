package io.github.kdroidfilter.seforimapp.features.settings.feedback

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Pardes's feedback service (feedback-service/, a Cloudflare Worker), at [url], the build's FEEDBACK_URL: it takes the
 * packages ([FeedbackPackage], in parts of at most [PART_BYTES]) and deletes what a sender sent. A build without the
 * address has none ([available] false): feedback is then only saved to files. A network failure is an IOException; a
 * refusal of the service a [FeedbackServiceProblem].
 */
class FeedbackService(
    private val client: HttpClient,
    url: String?,
) {
    private val base: String? = url?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    val available: Boolean get() = base != null

    /** Sends a package's [json]; its id on the service. */
    suspend fun send(json: String): Long {
        val response = client.post("${address()}/v1/packages") { setBody(TextContent(json, ContentType.Application.Json)) }
        return answer(response).getValue("id").jsonPrimitive.long
    }

    /** Deletes everything [sender] sent; how many packages there were. */
    suspend fun delete(sender: String): Int = answer(client.delete("${address()}/v1/senders/$sender")).getValue("deleted").jsonPrimitive.int

    private fun address(): String = checkNotNull(base) { "This build has no feedback service" }

    private suspend fun answer(response: HttpResponse): JsonObject {
        val body = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
        if (!response.status.isSuccess() || body == null) {
            val reason = body?.get("error")?.jsonPrimitive?.contentOrNull ?: response.status.description
            throw FeedbackServiceProblem(response.status.value, reason)
        }
        return body
    }
}

/** The service's refusal, its HTTP [status] and its reason. Too many packages ([tooMany]) can be sent again later. */
class FeedbackServiceProblem(
    val status: Int,
    val reason: String,
) : Exception("$status $reason") {
    val tooMany: Boolean get() = status == 429
}
