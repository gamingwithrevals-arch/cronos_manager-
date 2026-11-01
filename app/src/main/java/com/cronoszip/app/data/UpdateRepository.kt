package com.cronoszip.app.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class UpdateRepository(private val context: Context) {
    private val repoOwner = "aizenemu"
    private val repoName = "cronosmanager"

    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "CronosZip-Android")
        try {
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val bodyText = json.optString("body")
            val htmlUrl = json.optString("html_url")
            val published = json.optString("published_at")
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl = ""
            var apkName = ""
            var apkSize: Long = 0
            var shaUrl = ""
            var versionUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val browserUrl = asset.optString("browser_download_url")
                val size = asset.optLong("size")
                if (name.endsWith(".sha256") && shaUrl.isBlank()) {
                    shaUrl = browserUrl
                }
                if (name == "version.txt" && versionUrl.isBlank()) {
                    versionUrl = browserUrl
                }
                if (name.endsWith(".apk") && !name.contains("unaligned") && apkUrl.isBlank()) {
                    apkUrl = browserUrl
                    apkName = name
                    apkSize = size
                }
            }
            var sha = ""
            if (shaUrl.isNotEmpty()) {
                sha = downloadText(shaUrl).trim()
                sha = sha.split("\\s+".toRegex()).firstOrNull().orEmpty()
            }
            val releaseCode = if (versionUrl.isNotEmpty()) {
                downloadText(versionUrl).trim().toLongOrNull() ?: 0L
            } else {
                0L
            }
            UpdateInfo(
                tagName = tag,
                versionName = tag.removePrefix("v"),
                versionCode = releaseCode,
                apkUrl = apkUrl,
                apkName = apkName,
                apkSize = apkSize,
                sha256Url = shaUrl,
                sha256 = sha,
                body = bodyText,
                htmlUrl = htmlUrl,
                publishedAt = published,
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun downloadApk(info: UpdateInfo, onProgress: (Long, Long) -> Unit): File? = withContext(Dispatchers.IO) {
        if (info.apkUrl.isBlank()) return@withContext null
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, info.apkName.ifBlank { "cronoszip-update.apk" })
        val url = URL(info.apkUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.setRequestProperty("User-Agent", "CronosZip-Android")
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            conn.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var done: Long = 0
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        done += read.toLong()
                        onProgress(done, total)
                    }
                    output.flush()
                }
            }
            if (info.sha256.isNotEmpty()) {
                val actual = sha256(file)
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    file.delete()
                    return@withContext null
                }
            }
            file
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "CronosZip-Android")
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun currentVersion(): Pair<String, Long> {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            info.versionName.orEmpty() to code
        } catch (e: Exception) {
            "0" to 0L
        }
    }
}
