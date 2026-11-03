package com.cronoszip.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One installed app. [sourcePaths] lists the base APK first, then any split APKs. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val sourcePaths: List<String>,
    val totalBytes: Long,
) {
    val isSplit: Boolean get() = sourcePaths.size > 1
}

/** A finished backup: the file written, how many APK files went into it, and the bytes copied. */
data class AppExtraction(val folder: String, val files: Int, val bytes: Long)

/**
 * Lists installed apps and backs them up. The APK paths come from PackageManager, which unrooted
 * apps may read. Only APK files are copied; app data is not readable without root.
 */
object InstalledApps {
    private const val OUTPUT_FOLDER = "Chronos Apps"
    private const val BUFFER_SIZE = 256 * 1024
    private val iconCache = object : LruCache<String, Bitmap>(128) {}

    fun list(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        return packages.mapNotNull { toApp(pm, it) }
            .sortedBy { it.label.lowercase() }
    }

    private fun toApp(pm: PackageManager, pkg: PackageInfo): InstalledApp? {
        val info: ApplicationInfo = pkg.applicationInfo ?: return null
        val source: String = info.sourceDir ?: return null
        val paths = mutableListOf(source)
        info.splitSourceDirs?.forEach { paths.add(it) }

        val isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val isUserApp = !isSystemApp || isUpdatedSystemApp

        return InstalledApp(
            packageName = info.packageName,
            label = pm.getApplicationLabel(info).toString(),
            versionName = pkg.versionName.orEmpty(),
            versionCode = versionCodeOf(pkg),
            isSystem = !isUserApp,
            installTime = pkg.firstInstallTime,
            updateTime = pkg.lastUpdateTime,
            sourcePaths = paths,
            totalBytes = paths.sumOf { File(it).length() },
        )
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(pkg: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode else pkg.versionCode.toLong()

    /**
     * Writes the backup into Downloads/Chronos Apps. A single APK becomes `<Name>_<Version>.apk`.
     * An app with split APKs becomes `<Name>_<Version>.apks`, a ZIP holding base.apk and every split.
     * A partly written file is deleted if the backup fails. [onBytes] reports (copied, total).
     */
    fun extract(app: InstalledApp, onBytes: (Long, Long) -> Unit): Result<AppExtraction> {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), OUTPUT_FOLDER)
        val extension = if (app.isSplit) "apks" else "apk"
        val output = File(dir, "${baseName(app)}.$extension")
        return runCatching {
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IllegalStateException("Could not create ${dir.absolutePath}.")
            }
            app.sourcePaths.forEach { path ->
                if (!File(path).canRead()) {
                    throw IllegalStateException("Android does not allow reading ${File(path).name} for ${app.label}.")
                }
            }
            val total = app.totalBytes
            var done = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            val report: (Int) -> Unit = { n ->
                done += n
                onBytes(done, total)
            }
            if (app.isSplit) {
                writeBundle(app.sourcePaths, output, buffer, report)
            } else {
                copyFile(File(app.sourcePaths.first()), output, buffer, report)
            }
            AppExtraction(folder = output.absolutePath, files = app.sourcePaths.size, bytes = done)
        }.onFailure { output.delete() }
    }

    private fun writeBundle(paths: List<String>, output: File, buffer: ByteArray, report: (Int) -> Unit) {
        ZipOutputStream(FileOutputStream(output).buffered()).use { zip ->
            paths.forEachIndexed { index, path ->
                val source = File(path)
                val entryName = if (index == 0) "base.apk" else source.name
                zip.putNextEntry(ZipEntry(entryName))
                FileInputStream(source).use { input -> pump(input, zip, buffer, report) }
                zip.closeEntry()
            }
        }
    }

    private fun copyFile(source: File, output: File, buffer: ByteArray, report: (Int) -> Unit) {
        FileInputStream(source).use { input ->
            FileOutputStream(output).use { out -> pump(input, out, buffer, report) }
        }
    }

    private fun pump(input: InputStream, output: OutputStream, buffer: ByteArray, report: (Int) -> Unit) {
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            report(n)
        }
    }

    /** `<Name>_<Version>` with characters that are unsafe in file names replaced by underscores. */
    private fun baseName(app: InstalledApp): String {
        val label = app.label.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { app.packageName }
        val version = app.versionName.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "unknown" }
        return "${label}_$version"
    }

    /** The app icon drawn into a square bitmap of [px] pixels, or null when it can't be read. */
    fun icon(context: Context, packageName: String, px: Int): Bitmap? {
        val cacheKey = "$packageName:$px"
        iconCache.get(cacheKey)?.let { return it }

        val pm = context.packageManager
        val appInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }

        val drawable: Drawable = try {
            appInfo.loadIcon(pm)
        } catch (_: Exception) {
            return null
        }

        val bitmap = try {
            drawable.toBitmap(px, px)
        } catch (_: Exception) {
            return null
        }

        val cached = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        iconCache.put(cacheKey, cached)
        return cached
    }
}
