package com.cleanyourtv.mobile.flow

import com.cleanyourtv.mobile.adb.Shell
import com.cleanyourtv.mobile.adb.ShellResult
import com.cleanyourtv.mobile.data.DeviceInfo
import com.cleanyourtv.mobile.data.ListPackage
import com.cleanyourtv.mobile.data.TvList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanBuilderTest {

    private val device = DeviceInfo(
        model = "Test TV",
        brand = "Vendor",
        device = "testdevice",
        androidVersion = "12",
        sdk = 31,
    )

    @Test
    fun `core packages are skipped and reported`() = runTest {
        val shell = shell(
            "android" to "",
            "com.android.systemui" to "",
            "com.android.providers.media" to "",
            "com.android.providers.calendar" to "",
            "com.foo.app" to "",
        )
        val requested = listOf(
            ListPackage("android", null),
            ListPackage("com.android.systemui", null),
            ListPackage("com.android.providers.media", null),
            ListPackage("com.android.providers.calendar", null),
            ListPackage("com.foo.app", null),
        )

        val analysis = PlanBuilder(shell).analyze(device, list(), requested)

        assertEquals(
            listOf("com.android.providers.calendar", "com.foo.app"),
            analysis.plan.effectiveNames,
        )
        assertEquals(3, analysis.plan.skippedCount)
        assertTrue(analysis.plan.findings.any { it.title == "Core Android packages removed" })
    }

    @Test
    fun `setupwraith is kept when it owns the saved wifi`() = runTest {
        val shell = shell("com.google.android.tungsten.setupwraith" to "", "com.foo.app" to "")
        shell.put("dumpsys wifi", "mNetworkInfo cname=setupwraith\n")
        val requested = listOf(
            ListPackage("com.google.android.tungsten.setupwraith", null),
            ListPackage("com.foo.app", null),
        )

        val analysis = PlanBuilder(shell).analyze(device, list(), requested)

        assertEquals(listOf("com.foo.app"), analysis.plan.effectiveNames)
        assertTrue(analysis.plan.skipped.keys.contains("com.google.android.tungsten.setupwraith"))
        assertEquals(true, analysis.plan.setupWraithOwnsWifi)
    }

    @Test
    fun `setupwraith is kept when the wifi owner cannot be read`() = runTest {
        val shell = shell("com.google.android.tungsten.setupwraith" to "", "com.foo.app" to "")
        shell.put("dumpsys wifi", "")
        val requested = listOf(
            ListPackage("com.google.android.tungsten.setupwraith", null),
            ListPackage("com.foo.app", null),
        )

        val analysis = PlanBuilder(shell).analyze(device, list(), requested)

        assertTrue(analysis.plan.skipped.keys.contains("com.google.android.tungsten.setupwraith"))
        assertEquals(null, analysis.plan.setupWraithOwnsWifi)
    }

    @Test
    fun `the current Home app stays enabled when nothing else can take Home`() = runTest {
        val shell = shell("com.vendor.stock" to "")
        shell.put(HOME_QUERY, "com.vendor.stock/.Home\n")
        shell.put(CLAIMS_QUERY, "com.vendor.stock/.Home\n")
        val requested = listOf(ListPackage("com.vendor.stock", null))

        val analysis = PlanBuilder(shell).analyze(device, list(), requested)

        assertTrue(analysis.plan.effectiveNames.isEmpty())
        assertTrue(analysis.plan.skipped.keys.contains("com.vendor.stock"))
    }

    @Test
    fun `the current Home app may be disabled when another launcher is installed`() = runTest {
        val shell = shell("com.vendor.stock" to "")
        shell.put(HOME_QUERY, "com.vendor.stock/.Home\n")
        shell.put(CLAIMS_QUERY, "com.vendor.stock/.Home\ncom.gathin.tvhome/.Main\n")
        val requested = listOf(ListPackage("com.vendor.stock", null))

        val analysis = PlanBuilder(shell).analyze(device, list(), requested)

        assertEquals(listOf("com.vendor.stock"), analysis.plan.effectiveNames)
        assertFalse(analysis.plan.skipped.keys.contains("com.vendor.stock"))
    }

    @Test
    fun `a package asking for TV_INPUT_HARDWARE stays enabled`() = runTest {
        val shell = shell("com.input.service" to "", "com.foo.app" to "")
        shell.put("for p in", "com.input.service\n")
        val requested = listOf(
            ListPackage("com.input.service", null),
            ListPackage("com.foo.app", null),
        )

        val analysis = PlanBuilder(shell).analyze(device, list(), requested)

        assertEquals(listOf("com.foo.app"), analysis.plan.effectiveNames)
        assertTrue(analysis.plan.skipped.keys.contains("com.input.service"))
        assertTrue(analysis.plan.findings.any { it.title == "Input services kept" })
    }

    @Test
    fun `a CVTE board is reported and keeps its stock launcher for the run`() = runTest {
        val shell = shell("com.cvte.foo" to "", "com.foo.app" to "")
        val requested = listOf(ListPackage("com.foo.app", null))

        val analysis = PlanBuilder(shell).analyze(device, list(), requested)

        assertTrue(analysis.plan.isCvteBoard)
        assertTrue(analysis.plan.findings.any { it.title == "CVTE board" })
    }

    private fun shell(vararg installed: Pair<String, String>): FakeShell =
        FakeShell(installed.toList())

    private fun list(): TvList = TvList(
        id = "test-tv",
        title = "Test TV list",
        os = "Android TV 12",
        notes = "",
        header = emptyList(),
        packageCount = 3,
    )

    private class FakeShell(installed: List<Pair<String, String>>) : Shell {
        val commands = mutableListOf<String>()
        private val responses = LinkedHashMap<String, String>()

        init {
            responses["pm list packages -d"] = ""
            responses["pm list packages"] = installed.joinToString("\n") { "package:${it.first}" }
            responses[HOME_QUERY] = "com.foo.launcher/.Main\n"
            responses[CLAIMS_QUERY] = "com.foo.launcher/.Main\n"
            responses["dumpsys wifi"] = "mNetworkInfo cname=WifiNetworkFactory\n"
            responses["getprop ro.adb.secure"] = "1\n"
            responses["for p in"] = ""
            responses["settings get global window_animation_scale"] = "1.0\n"
            responses["settings get global transition_animation_scale"] = "1.0\n"
            responses["settings get global animator_duration_scale"] = "1.0\n"
            responses["settings get global private_dns_mode"] = "off\n"
            responses["settings get global private_dns_specifier"] = "null\n"
        }

        fun put(prefix: String, output: String) {
            responses[prefix] = output
        }

        override suspend fun exec(command: String): ShellResult {
            commands += command
            val output = responses.entries
                .firstOrNull { command.startsWith(it.key) }
                ?.value
                ?: ""
            return ShellResult(output, "", 0)
        }
    }

    private companion object {
        const val HOME_QUERY =
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        const val CLAIMS_QUERY =
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    }
}
