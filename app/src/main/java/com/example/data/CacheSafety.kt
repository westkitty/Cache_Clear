package com.example.data

import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal data class CacheSweepResult(val deletedFileBytes: Long, val skipped: Int, val failed: Int) {
    operator fun plus(other: CacheSweepResult) =
        CacheSweepResult(deletedFileBytes + other.deletedFileBytes, skipped + other.skipped, failed + other.failed)
}

/** Operates only on the children of a framework-provided cache root, not the root itself. */
internal fun sweepCacheRoot(cacheRoot: Path): CacheSweepResult {
    var bytes = 0L
    var skipped = 0
    var failed = 0
    try {
        val requestedRoot = cacheRoot.toAbsolutePath().normalize()
        val rootAttrs = Files.readAttributes(requestedRoot, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        if (!rootAttrs.isDirectory || rootAttrs.isSymbolicLink) return CacheSweepResult(0, 1, 0)
        val root = requestedRoot.toRealPath()
        fun visit(path: Path, depth: Int) {
            try {
                val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (attrs.isSymbolicLink || depth > 64 || path.parent.toRealPath() != path.parent ||
                    !path.startsWith(root) || path.toRealPath() != path
                ) {
                    skipped++
                    return
                }
                if (attrs.isDirectory) {
                    Files.newDirectoryStream(path).use { children -> children.forEach { visit(it, depth + 1) } }
                } else if (!attrs.isRegularFile) {
                    skipped++
                    return
                }
                // A parent directory can become nonempty if a child was skipped or is still in use.
                if (path.parent.toRealPath() != path.parent || path.toRealPath() != path) {
                    skipped++
                    return
                }
                if (Files.deleteIfExists(path) && attrs.isRegularFile) bytes += attrs.size()
            } catch (_: NoSuchFileException) {
                skipped++
            } catch (_: DirectoryIteratorException) {
                failed++
            } catch (_: IOException) {
                failed++
            } catch (_: SecurityException) {
                failed++
            }
        }
        Files.newDirectoryStream(root).use { children -> children.forEach { visit(it, 1) } }
    } catch (_: NoSuchFileException) {
        // A device may not currently have an external cache volume mounted.
    } catch (_: DirectoryIteratorException) {
        failed++
    } catch (_: IOException) {
        failed++
    } catch (_: SecurityException) {
        failed++
    }
    return CacheSweepResult(bytes, skipped, failed)
}

internal class ConfirmationGate<T : Any> {
    private var pending: Pair<T, Set<String>>? = null

    fun arm(report: T, selected: Set<String>) {
        pending = report to selected.toSet()
    }

    fun invalidate() {
        pending = null
    }

    fun consume(report: T, selected: Set<String>, ownPackage: String): Boolean {
        val current = pending ?: return false
        if (current.first !== report) return false
        pending = null
        return selected.isNotEmpty() && ownPackage in selected && current.second == selected
    }
}

enum class ExternalCacheStatus(val label: String) {
    SETTINGS_OPENED("Settings opened · no change measured"),
    UNMEASURED("Settings opened · not measured"),
    REPORTED_ZERO("Android reports 0 B cache"),
    ALREADY_LOW("Cache was already low"),
    DECREASED("Measured cache decreased")
}

internal fun externalCacheStatus(before: Long?, after: Long?): ExternalCacheStatus = when {
    after == null -> ExternalCacheStatus.UNMEASURED
    after == 0L -> ExternalCacheStatus.REPORTED_ZERO
    before == null -> ExternalCacheStatus.SETTINGS_OPENED
    after < before -> ExternalCacheStatus.DECREASED
    before <= 16_384L && after <= 16_384L -> ExternalCacheStatus.ALREADY_LOW
    else -> ExternalCacheStatus.SETTINGS_OPENED
}
