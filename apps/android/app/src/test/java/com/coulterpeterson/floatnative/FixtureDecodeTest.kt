package com.coulterpeterson.floatnative

import com.coulterpeterson.floatnative.api.ManualApi
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3Channel
import com.coulterpeterson.floatnative.openapi.models.ContentCreatorListV3Response
import com.coulterpeterson.floatnative.utils.buildAppMoshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Decode every JSON fixture in src/test/resources/fixtures/ through the
 * production model used for that endpoint. Mirrors the iOS test of the same
 * name. Add a new fixture and a route entry below when capturing fixtures
 * for a new endpoint.
 */
class FixtureDecodeTest {

    // Use the same Moshi setup as production so test results match runtime.
    private val moshi = buildAppMoshi()

    private val fixturesDir: File =
        File(javaClass.classLoader!!.getResource("fixtures")!!.toURI())

    @Test
    fun every_fixture_decodes() {
        val files = fixturesDir.listFiles { _, name -> name.endsWith(".json") }
            ?: emptyArray()
        assertTrue(
            "No fixtures discovered. Verify src/test/resources/fixtures/ exists.",
            files.isNotEmpty()
        )
        for (file in files.sortedBy { it.name }) {
            val text = file.readText()
            val decoded = decodeForFixture(text, file.name)
            assertNotNull("Fixture ${file.name} produced null", decoded)
        }
    }

    /**
     * Goes through ManualApi itself, as the skipping depends on the Retrofit
     * converter passing along the method's @Lossy.
     */
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun history_skips_an_entry_it_cannot_read() {
        val fixture = File(fixturesDir, "get_api_v3_content_history_offset_0.json").readText()
        val root = moshi.adapter<Map<String, Any?>>().fromJson(fixture)!!
        val response = root["response"] as Map<*, *>
        val readable = (response["body"] as List<*>).first()
        val body = moshi.adapter<Any>().toJson(listOf(readable, mapOf("userId" to "unreadable")))

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://example.test/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ManualApi::class.java)

        val history = runBlocking { api.getWatchHistory(offset = 0) }.body()!!

        assertEquals(1, history.size)
        // The TV screen goes by this now that the attachment lists are gone.
        assertTrue(history[0].blogPost!!.metadata.hasVideo)
    }

    /**
     * Real Floatplane responses sometimes return `channel` as just a string ID
     * instead of the full ChannelModel. The custom JsonAdapter must surface
     * that as [BlogPostModelV3Channel.AsId] without the lossy adapter
     * silently dropping the post.
     */
    @Test
    fun channel_as_string_decodes_to_AsId() {
        val file = File(fixturesDir, "get_api_v3_content_creator_list_channel_as_string.json")
        assertTrue("Fixture missing: ${file.name}", file.exists())
        val response = decodeForFixture(file.readText(), file.name)
            as ContentCreatorListV3Response
        // Without the sealed-class adapter the post would be dropped by the
        // lossy decoder and the list would be empty.
        assertEquals(
            "Expected one post; was the channel-as-string variant silently dropped?",
            1,
            response.blogPosts.size,
        )
        val channel = response.blogPosts[0].channel
        assertTrue(
            "Expected AsId variant; got ${channel.javaClass.simpleName}",
            channel is BlogPostModelV3Channel.AsId,
        )
        assertEquals("59f94c0bdd241b70349eb72c", channel.channelId)
        assertEquals(null, channel.channelObject)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun decodeForFixture(text: String, fileName: String): Any? {
        val envelopeAdapter = moshi.adapter<Map<String, Any>>()
        val envelope = envelopeAdapter.fromJson(text)
            ?: error("Fixture $fileName not valid JSON")

        @Suppress("UNCHECKED_CAST")
        val request = envelope["request"] as? Map<String, Any>
            ?: error("Fixture $fileName missing request")
        val path = request["path"] as? String
            ?: error("Fixture $fileName missing request.path")

        @Suppress("UNCHECKED_CAST")
        val response = envelope["response"] as? Map<String, Any>
            ?: error("Fixture $fileName missing response")

        @Suppress("UNCHECKED_CAST")
        val body = response["body"] ?: error("Fixture $fileName missing response.body")
        val bodyJson = moshi.adapter<Any>().toJson(body)

        return when {
            path.startsWith("/api/v3/content/creator/list") ->
                moshi.adapter(ContentCreatorListV3Response::class.java).fromJson(bodyJson)
            path.startsWith("/api/v3/content/history") -> {
                val type = com.squareup.moshi.Types.newParameterizedType(
                    List::class.java,
                    com.coulterpeterson.floatnative.api.WatchHistoryResponse::class.java,
                )
                moshi.adapter<List<com.coulterpeterson.floatnative.api.WatchHistoryResponse>>(type)
                    .fromJson(bodyJson)
            }
            path.startsWith("/api/v3/content/post") ->
                moshi.adapter(com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response::class.java)
                    .fromJson(bodyJson)
            path.startsWith("/api/v3/creator/info") ->
                moshi.adapter(com.coulterpeterson.floatnative.openapi.models.CreatorModelV3::class.java)
                    .fromJson(bodyJson)
            path.startsWith("/api/v3/comment") -> {
                val type = com.squareup.moshi.Types.newParameterizedType(
                    List::class.java,
                    com.coulterpeterson.floatnative.openapi.models.CommentModel::class.java,
                )
                moshi.adapter<List<com.coulterpeterson.floatnative.openapi.models.CommentModel>>(type)
                    .fromJson(bodyJson)
            }
            else -> {
                throw AssertionError(
                    "No fixture decoder mapping for path $path. Add one in FixtureDecodeTest.decodeForFixture()."
                )
            }
        }
    }
}
