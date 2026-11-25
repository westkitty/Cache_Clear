package com.example

import com.example.data.ConfirmationGate
import com.example.data.ExternalCacheStatus
import com.example.data.externalCacheStatus
import com.example.data.sweepCacheRoot
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheSafetyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun onlyCacheChildrenAreDeletedAndCounted() {
        val cache = temporary.newFolder("cache").toPath()
        val sibling = temporary.newFolder("files").toPath()
        val preferences = Files.write(sibling.resolve("preferences"), byteArrayOf(9))
        val nested = Files.createDirectory(cache.resolve("nested"))
        Files.write(nested.resolve("temp"), byteArrayOf(1, 2, 3))
        val result = sweepCacheRoot(cache)
        assertEquals(3L, result.deletedFileBytes)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
        assertTrue(Files.isDirectory(cache))
        assertFalse(Files.exists(nested))
        assertArrayEquals(byteArrayOf(9), Files.readAllBytes(preferences))
    }

    @Test fun emptyRootIsNotMistakenForDeletion() {
        val result = sweepCacheRoot(temporary.newFolder().toPath())
        assertEquals(0L, result.deletedFileBytes)
        assertEquals(0, result.failed)
    }

    @Test fun anUnreviewedOrCanceledActionIsNeverAdmitted() {
        val gate = ConfirmationGate<Any>()
        val report = Any()
        assertFalse(gate.consume(report, setOf("own"), "own"))
        gate.arm(report, setOf("own"))
        gate.invalidate()
        assertFalse(gate.consume(report, setOf("own"), "own"))
    }

    @Test fun anEmptyOrChangedScopeIsNeverAdmitted() {
        val gate = ConfirmationGate<Any>()
        val report = Any()
        gate.arm(report, emptySet())
        assertFalse(gate.consume(report, emptySet(), "own"))
        gate.arm(report, setOf("own", "other"))
        assertFalse(gate.consume(report, setOf("own"), "own"))
        assertFalse(gate.consume(report, setOf("own", "other"), "own"))
        gate.arm(report, setOf("other"))
        assertFalse(gate.consume(report, setOf("other"), "own"))
    }

    @Test fun reportMustBeCurrentAndIsSingleUse() {
        val gate = ConfirmationGate<Any>()
        val stale = Any()
        val current = Any()
        val scope = setOf("own", "other")
        gate.arm(stale, scope)
        gate.arm(current, scope)
        assertFalse(gate.consume(stale, scope, "own"))
        assertTrue(gate.consume(current, scope, "own"))
        assertFalse(gate.consume(current, scope, "own"))
    }

    @Test fun settingsReturnAndRevokedAccessDoNotProveADeletion() {
        assertEquals(ExternalCacheStatus.UNMEASURED, externalCacheStatus(40_000, null))
        assertEquals(ExternalCacheStatus.REPORTED_ZERO, externalCacheStatus(null, 0))
        assertEquals(ExternalCacheStatus.SETTINGS_OPENED, externalCacheStatus(null, 40_000))
        assertEquals(ExternalCacheStatus.SETTINGS_OPENED, externalCacheStatus(40_000, 40_000))
        assertEquals(ExternalCacheStatus.SETTINGS_OPENED, externalCacheStatus(30_000, 60_000))
        assertEquals(ExternalCacheStatus.ALREADY_LOW, externalCacheStatus(8, 8))
        assertEquals(ExternalCacheStatus.REPORTED_ZERO, externalCacheStatus(40_000, 0))
        assertEquals(ExternalCacheStatus.DECREASED, externalCacheStatus(40_000, 4))
    }
}
