package com.example

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CacheSelectionStore
import com.example.widget.CacheTwisterWidgetProvider
import com.example.widget.MiniTornadoWidgetProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetSafetyTest {
    @Test fun selectedPackagesAndExplicitEmptySurviveNewStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(setOf(context.packageName), CacheSelectionStore(context).read())
        CacheSelectionStore(context).save(setOf("first.approved", "second.approved"))
        assertEquals(setOf("first.approved", "second.approved"), CacheSelectionStore(context).read())
        CacheSelectionStore(context).save(emptySet())
        assertEquals(emptySet<String>(), CacheSelectionStore(context).read())
    }

    @Test fun bothWidgetReceiversArePrivate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(CacheTwisterWidgetProvider::class.java, MiniTornadoWidgetProvider::class.java).forEach {
            @Suppress("DEPRECATION")
            val receiver = context.packageManager.getReceiverInfo(ComponentName(context, it), 0)
            assertFalse(receiver.exported)
        }
    }
}
