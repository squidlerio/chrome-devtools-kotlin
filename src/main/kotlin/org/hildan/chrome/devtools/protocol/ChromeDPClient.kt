package org.hildan.chrome.devtools.protocol

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hildan.chrome.devtools.ChromeApi

/**
 * A Chrome Devtools Protocol client.
 *
 * It provides access to the basic HTTP endpoints exposed by the Chrome browser, as well as web socket connections to
 * the browser and its targets to make use of the full Chrome Devtools Protocol API (via [ChromeApi]).
 */
class ChromeDPClient(
    private val remoteDebugUrl: String = "http://localhost:9222",
    private val httpClient: HttpClient = ktorClientWithJson()
) {
    /** Browser version metadata. */
    suspend fun version(): ChromeVersion = httpClient.get("$remoteDebugUrl/json/version")

    /** The current devtools protocol definition, as a JSON string. */
    suspend fun protocolJson(): String = httpClient.get("$remoteDebugUrl/json/protocol")

    /** A list of all available websocket targets (e.g. browser tabs). */
    suspend fun targets(): List<ChromeDPTarget> = httpClient.get("$remoteDebugUrl/json/list")

    /** Opens a new tab. Responds with the websocket target data for the new tab. */
    suspend fun newTab(url: String = "about:blank"): ChromeDPTarget = httpClient.get("$remoteDebugUrl/json/new?$url")

    /** Brings a page into the foreground (activate a tab). */
    suspend fun activateTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/activate/$targetId")

    /** Closes the target page identified by [targetId]. */
    suspend fun closeTab(targetId: String): String = httpClient.get("$remoteDebugUrl/json/close/$targetId")

    /**
     * Opens a web socket connection to interact with the root (browser) session.
     *
     * The returned [ChromeApi] doesn't use session IDs (it is not attached to any target).
     * To attach to a target using the same underlying web socket connection, call [ChromeApi.attachTo] on the returned
     * [ChromeApi].
     *
     * You may prefer directly attaching to any target via a new web socket connection by calling [ChromeDPTarget.attach].
     */
    suspend fun detachedWebSocketDebugger(): ChromeApi {
        val browserDebuggerUrl = version().webSocketDebuggerUrl
        return ChromeDPSession.connectDetached(browserDebuggerUrl)
    }
}

@Serializable
data class ChromeVersion(
    @SerialName("Browser") val browser: String,
    @SerialName("Protocol-Version") val protocolVersion: String,
    @SerialName("User-Agent") val userAgent: String,
    @SerialName("V8-Version") val v8Version: String? = null,
    @SerialName("WebKit-Version") val webKitVersion: String,
    @SerialName("webSocketDebuggerUrl") val webSocketDebuggerUrl: String,
)

/**
 * Targets are the parts of the browser that the Chrome DevTools Protocol can interact with.
 * This includes for instance pages, serviceworkers and extensions.
 *
 * When a client wants to interact with a target using CDP, it has to first attach to the target using
 * [ChromeDPTarget.attach]. This will establish a protocol session to the given target.
 *
 * The client can then interact with the target using the [ChromeApi].
 *
 * To manipulate targets themselves, use the [TargetDomain][org.hildan.chrome.devtools.domains.target.TargetDomain]
 * (accessible through [ChromeApi.target]).
 */
@Serializable
data class ChromeDPTarget(
    val id: String,
    val title: String,
    val type: String,
    val description: String,
    val devtoolsFrontendUrl: String,
    val webSocketDebuggerUrl: String,
) {
    /**
     * Attaches to this target via a new web socket connection.
     * This establishes a new protocol session to this target.
     */
    suspend fun attach(): ChromeApi = ChromeDPSession.connectDetached(webSocketDebuggerUrl).attachTo(id)

    /**
     * Attaches to this target via a new web socket connection, and performs the given operation before closing the
     * connection.
     */
    suspend inline fun <T> use(block: (ChromeApi) -> T): T {
        val api = attach()
        try {
            return block(api)
        } finally {
            api.close()
        }
    }
}

private fun ktorClientWithJson() = HttpClient {
    install(JsonFeature) {
        serializer = KotlinxSerializer(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
    }
}