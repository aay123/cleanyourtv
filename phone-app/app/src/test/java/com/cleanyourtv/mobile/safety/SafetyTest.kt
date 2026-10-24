package com.cleanyourtv.mobile.safety

import com.cleanyourtv.mobile.data.ListPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {

    @Test
    fun `core packages never reach the disable list`() {
        val requested = listOf(
            ListPackage("com.android.systemui", null),
            ListPackage("com.google.android.ext.services", null),
            ListPackage("com.google.android.overlay.modules.permissioncontroller", null),
            ListPackage("android", null),
            ListPackage("com.android.providers.media", null),
        )
        val (kept, blocked) = Safety.removeCore(requested)

        assertTrue(kept.isEmpty())
        assertEquals(5, blocked.size)
    }

    @Test
    fun `calendar contacts dictionary and blocked numbers may be disabled`() {
        val names = listOf(
            "com.android.providers.calendar",
            "com.android.providers.contacts",
            "com.android.providers.userdictionary",
            "com.android.providers.blockednumber",
        )
        val (kept, blocked) = Safety.removeCore(names.map { ListPackage(it, null) })

        assertEquals(names, kept.map { it.name })
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun `a fallback or factory launcher does not count as a way back to Home`() {
        assertFalse(Safety.isRealLauncher("com.vendor/.FallbackHome", "com.vendor"))
        assertFalse(Safety.isRealLauncher("com.vendor/.FactoryTest", "com.vendor"))
        assertFalse(Safety.isRealLauncher("com.google.android.tungsten.setupwraith/.Main", "com.google.android.tungsten.setupwraith"))
        assertTrue(Safety.isRealLauncher("com.gathin.tvhome/.Main", "com.gathin.tvhome"))
    }

    @Test
    fun `other home owners skip the current one and packages being disabled`() {
        val claims = listOf(
            "com.vendor.stock/.Home",
            "com.vendor/.FallbackHome",
            "com.gathin.tvhome/.Main",
        )
        val owners = Safety.otherHomeOwners(
            claims = claims,
            currentHome = "com.vendor.stock/.Home",
            requested = setOf("com.gathin.tvhome"),
        )

        assertTrue(owners.isEmpty())
    }

    @Test
    fun `wifi owners are read from the cname fields`() {
        val output = "mNetworkInfo cname=setupwraith\nmNetworkInfo cname=WifiNetworkFactory\n"
        assertEquals(listOf("setupwraith", "WifiNetworkFactory"), Safety.parseWifiOwners(output))
    }

    @Test
    fun `home claims are read from brief query output`() {
        val output = """
            priority=0 com.vendor.stock/.Home
            priority=0 com.gathin.tvhome/.Main
        """.trimIndent()
        assertEquals(
            listOf("com.vendor.stock/.Home", "com.gathin.tvhome/.Main"),
            Safety.parseHomeClaims(output),
        )
    }

    @Test
    fun `every home claimant is kept when home is unknown`() {
        val claims = listOf(
            "com.vendor.stock/.Home",
            "com.gathin.tvhome/.Main",
        )
        assertEquals(
            setOf("com.vendor.stock", "com.gathin.tvhome"),
            Safety.protectedWhenHomeUnknown(claims, currentHome = null),
        )
        assertTrue(Safety.protectedWhenHomeUnknown(claims, "com.vendor.stock/.Home").isEmpty())
    }

    @Test
    fun `package lines ignore everything that is not a package`() {
        val output = "package:com.foo.bar\npackage:android\nnot a package\npackage:com.baz.qux\n"
        assertEquals(setOf("com.foo.bar", "android", "com.baz.qux"), Safety.parsePackageLines(output))
    }
}
