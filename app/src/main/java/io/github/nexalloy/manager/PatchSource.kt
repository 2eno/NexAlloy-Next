package io.github.nexalloy.manager

import android.content.Context
import io.github.nexalloy.core.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * A place NexAlloy downloads patch bundles from, like a Morphe patch source.
 *
 * [url] is either a GitHub repository (`owner/repo` or `https://github.com/owner/repo`), whose
 * latest release provides the bundles as `.nxb` assets, or the URL of a bundle JSON file:
 * ```json
 * { "version": "1.2.0", "download_url": "https://…/bundle.nxb", "description": "changelog" }
 * ```
 */
data class PatchSource(
    val id: String,
    val name: String,
    val url: String,
    /** Also consider GitHub pre-releases. */
    val prerelease: Boolean = false,
    /** Release version whose bundles are installed, null if none are. */
    val installedVersion: String? = null,
) {
    val githubRepository: String?
        get() = GITHUB_REPO.matchEntire(url.trim().removeSuffix("/").removeSuffix(".git"))?.groupValues?.get(1)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("prerelease", prerelease)
        .put("installedVersion", installedVersion)

    companion object {
        private val GITHUB_REPO = Regex("""(?:https?://github\.com/)?([\w.-]+/[\w.-]+)""")

        fun fromJson(json: JSONObject) = PatchSource(
            id = json.getString("id"),
            name = json.getString("name"),
            url = json.getString("url"),
            prerelease = json.optBoolean("prerelease"),
            installedVersion = json.optString("installedVersion").takeIf { it.isNotEmpty() && it != "null" },
        )

        fun create(name: String, url: String, prerelease: Boolean = false) =
            PatchSource(UUID.randomUUID().toString(), name, url.trim(), prerelease)

        /** The NexAlloy Patches and 2eno Patches bundles. */
        val default = PatchSource(
            id = "default",
            name = "NexAlloy Patches",
            url = "2eno/NexAlloy-Next.2eno",
        )
    }
}

/** A release of a [PatchSource]. */
class SourceRelease(
    val version: String,
    val changelog: String,
    val pageUrl: String?,
    /** File name to download URL of each bundle in the release. */
    val bundles: List<Pair<String, String>>,
)

/** Persists the configured sources in the manager's own preferences. */
class SourceStore(context: Context) {
    private val prefs = context.getSharedPreferences("sources", Context.MODE_PRIVATE)

    fun all(): List<PatchSource> {
        val raw = prefs.getString(KEY, null) ?: return listOf(PatchSource.default)
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { PatchSource.fromJson(array.getJSONObject(it)) }.map {
                // The default source moved from the NexAlloy repository to its own.
                if (it.id == PatchSource.default.id && it.url == "2eno/NexAlloy-Next") it.copy(url = PatchSource.default.url) else it
            }
        }.getOrDefault(listOf(PatchSource.default))
    }

    fun get(id: String) = all().find { it.id == id }

    fun save(source: PatchSource) = write(all().filter { it.id != source.id } + source)

    fun remove(id: String) = write(all().filter { it.id != id })

    private fun write(sources: List<PatchSource>) {
        val array = JSONArray()
        sources.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "sources"
    }
}

/** Fetches releases of patch sources. Blocking; call off the main thread. */
object SourceClient {
    const val BUNDLE_EXTENSION = ".nxb"

    fun latestRelease(source: PatchSource): SourceRelease {
        val repository = source.githubRepository
        return if (repository != null && !source.url.contains("raw.githubusercontent.com")) {
            latestGitHubRelease(repository, source.prerelease)
        } else {
            jsonRelease(source.url)
        }
    }

    private fun latestGitHubRelease(repository: String, prerelease: Boolean): SourceRelease {
        val releases = JSONArray(get("https://api.github.com/repos/$repository/releases?per_page=20"))
        val candidates = (0 until releases.length()).map { releases.getJSONObject(it) }
            .filter { !it.optBoolean("draft") }
            .filter { it.getJSONArray("assets").let { assets ->
                (0 until assets.length()).any { i -> assets.getJSONObject(i).getString("name").endsWith(BUNDLE_EXTENSION) }
            } }
        // Without a stable release, fall back to pre-releases, e.g. for projects still in alpha.
        val allowPrerelease = prerelease || candidates.none { !it.optBoolean("prerelease") }
        for (release in candidates) {
            if (release.optBoolean("prerelease") && !allowPrerelease) continue
            val assets = release.getJSONArray("assets")
            val bundles = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.getString("name").endsWith(BUNDLE_EXTENSION) }
                .map { it.getString("name") to it.getString("browser_download_url") }
            if (bundles.isEmpty()) continue
            return SourceRelease(
                version = release.getString("tag_name"),
                changelog = release.optString("body"),
                pageUrl = release.optString("html_url"),
                bundles = bundles,
            )
        }
        throw SourceException("No release of $repository contains a $BUNDLE_EXTENSION patch bundle")
    }

    private fun jsonRelease(url: String): SourceRelease {
        val json = JSONObject(get(url))
        val downloadUrl = json.optString("download_url").takeIf { it.isNotEmpty() }
            ?: throw SourceException("$url has no download_url")
        return SourceRelease(
            version = json.optString("version", downloadUrl),
            changelog = json.optString("description"),
            pageUrl = null,
            bundles = listOf(downloadUrl.substringAfterLast('/') to downloadUrl),
        )
    }

    fun get(url: String): String = open(url).inputStream.use { it.readBytes().decodeToString() }

    fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "NexAlloy/${BuildConfig.VERSION_NAME}")
        if (url.startsWith("https://api.github.com/")) {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
        }
        val code = connection.responseCode
        if (code !in 200..299) throw SourceException("HTTP $code for $url")
        return connection
    }
}

class SourceException(message: String) : Exception(message)
