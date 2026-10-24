package com.cleanyourtv.mobile.flow

import com.cleanyourtv.mobile.adb.Shell
import com.cleanyourtv.mobile.data.Analysis
import com.cleanyourtv.mobile.data.DeviceInfo
import com.cleanyourtv.mobile.data.Finding
import com.cleanyourtv.mobile.data.ListPackage
import com.cleanyourtv.mobile.data.Plan
import com.cleanyourtv.mobile.data.TvList
import com.cleanyourtv.mobile.safety.Safety
import java.io.IOException

/**
 * Reads the TV and works out what a list would really do there, applying the same guards as
 * tools/disable.sh: core packages, setupwraith and the saved Wi-Fi, Home ownership, and the
 * TV_INPUT_HARDWARE permission behind HDMI/AV/tuner. Skipped packages are reported, never silently
 * dropped.
 */
class PlanBuilder(private val shell: Shell) {

    suspend fun analyze(
        device: DeviceInfo,
        list: TvList,
        requested: List<ListPackage>,
    ): Analysis {
        val installed = Safety.parsePackageLines(shell.exec("pm list packages").output)
        if (installed.isEmpty()) {
            throw IOException("the TV returned no packages; check the connection and try again")
        }
        val disabledBefore = Safety.parsePackageLines(shell.exec("pm list packages -d").output)
        val currentHome = currentHome()
        val claims = homeClaims(currentHome)
        val wifiOwners = Safety.parseWifiOwners(
            shell.exec("dumpsys wifi | grep -o 'cname=[^ ]*'").output,
        )
        val adbSecure = shell.exec("getprop ro.adb.secure").output.trim().ifBlank { null }

        val (nonCore, skipped) = Safety.removeCore(requested).let { (kept, core) ->
            kept to LinkedHashMap(core)
        }
        var working = nonCore

        val notInstalled = working.filterNot { it.name in installed }
        for (pkg in notInstalled) {
            skipped[pkg.name] = "not installed on this TV"
        }
        working = working.filter { it.name in installed }

        // setupwraith: a Wi-Fi network saved during first setup belongs to it, and disabling it
        // deletes the network, which takes the TV off Wi-Fi and ADB with it.
        val setupWraith = working.firstOrNull { it.name == Safety.SETUPWRAITH }
        if (setupWraith != null) {
            when {
                wifiOwners.isEmpty() -> {
                    skipped[Safety.SETUPWRAITH] =
                        "could not check which app owns the saved Wi-Fi; leaving it enabled"
                    working = working.filterNot { it.name == Safety.SETUPWRAITH }
                }
                wifiOwners.any { it.contains("setupwraith", ignoreCase = true) } -> {
                    skipped[Safety.SETUPWRAITH] =
                        "the saved Wi-Fi belongs to it; disabling it would take the TV off Wi-Fi"
                    working = working.filterNot { it.name == Safety.SETUPWRAITH }
                }
            }
        }

        // Home guard: a list that disables the app owning Home leaves nothing to start.
        val currentHomePkg = Safety.homePackage(currentHome)
        if (currentHomePkg != null && working.any { it.name == currentHomePkg }) {
            val others = Safety.otherHomeOwners(claims, currentHome, working.map { it.name }.toSet())
            if (others.isEmpty()) {
                skipped[currentHomePkg] =
                    "it owns Home and no other usable launcher is installed and staying enabled"
                working = working.filterNot { it.name == currentHomePkg }
            }
        }

        // HDMI/AV/tuner: a package asking for TV_INPUT_HARDWARE is usually the software layer under
        // the inputs. A false positive exists, so the finding tells the owner how to check.
        val tvInput = checkTvInputs(working.map { it.name })
        if (tvInput != null) {
            for (name in tvInput) {
                skipped[name] =
                    "asks for TV_INPUT_HARDWARE (HDMI/AV/tuner); disabling it can leave inputs black"
            }
            working = working.filterNot { it.name in tvInput }
        }

        val isCvte = installed.any { it.startsWith("com.cvte.") }
        val animationScales = animationScales()
        val dnsMode = setting("private_dns_mode")
        val dnsSpecifier = setting("private_dns_specifier")

        val findings = buildFindings(
            device = device,
            list = list,
            skipped = skipped,
            working = working,
            currentHome = currentHome,
            wifiOwners = wifiOwners,
            tvInput = tvInput,
            adbSecure = adbSecure,
            isCvte = isCvte,
            setupWraithSkipped = skipped.containsKey(Safety.SETUPWRAITH),
        )

        return Analysis(
            plan = Plan(
                device = device,
                list = list,
                requested = requested,
                effective = working,
                skipped = skipped,
                findings = findings,
                currentHome = currentHome,
                homeClaims = claims,
                setupWraithOwnsWifi = when {
                    wifiOwners.isEmpty() -> null
                    else -> wifiOwners.any { it.contains("setupwraith", ignoreCase = true) }
                },
                wifiOwners = wifiOwners,
                tvInputPackages = tvInput,
                adbSecure = adbSecure,
                stockLauncher = currentHomePkg,
                isCvteBoard = isCvte,
                installed = installed,
                disabledBefore = disabledBefore,
            ),
            animationScales = animationScales,
            dnsMode = dnsMode,
            dnsSpecifier = dnsSpecifier,
        )
    }

    private suspend fun currentHome(): String? {
        val answer = shell.exec(HOME_QUERY)
        val candidate = Safety.parseHomeClaims(answer.output)
            .lastOrNull { !it.startsWith("android/") }
        if (candidate != null) return candidate
        val dump = shell.exec("dumpsys activity").output
        val match = Regex("mHomeProcess:.*?([A-Za-z_][A-Za-z0-9_.]*)/[A-Za-z0-9_.]+").find(dump)
        return match?.groupValues?.get(1)?.takeIf { Safety.isPackageName(it) }
    }

    private suspend fun homeClaims(currentHome: String?): List<String> {
        val output = shell.exec(CLAIMS_QUERY).output
        val claims = Safety.parseHomeClaims(output)
        return if (claims.isEmpty() && currentHome != null) listOf(currentHome) else claims
    }

    private suspend fun checkTvInputs(names: List<String>): Set<String>? {
        if (names.isEmpty()) return emptySet()
        val found = LinkedHashSet<String>()
        for (chunk in names.chunked(25)) {
            val joined = chunk.joinToString(" ")
            val command = "for p in $joined; do dumpsys package \$p | " +
                "grep -q TV_INPUT_HARDWARE && echo \$p; done; true"
            val result = shell.exec(command)
            if (!result.ok) return null
            found += Safety.parseTvInputPackages(result.output)
        }
        return found
    }

    private suspend fun animationScales(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (key in SCALE_KEYS) {
            setting(key)?.let { result[key] = it }
        }
        return result
    }

    private suspend fun setting(key: String): String? =
        shell.exec("settings get global $key").output.trim().takeUnless { it.isEmpty() || it == "null" }

    private fun buildFindings(
        device: DeviceInfo,
        list: TvList,
        skipped: Map<String, String>,
        working: List<ListPackage>,
        currentHome: String?,
        wifiOwners: List<String>,
        tvInput: Set<String>?,
        adbSecure: String?,
        isCvte: Boolean,
        setupWraithSkipped: Boolean,
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        findings += Finding(
            Finding.Level.INFO,
            list.title,
            buildString {
                append(list.os.ifBlank { "Android TV" })
                append(" · ")
                append(list.packageCount)
                append(" packages in the list, ")
                append(working.size)
                append(" will be disabled")
            },
        )
        if (working.isEmpty()) {
            findings += Finding(
                Finding.Level.BLOCK,
                "Nothing left to disable",
                "Every package in this list was skipped by a safety check. Pick another list.",
            )
        }
        if (skipped.keys.any { Safety.CORE_RE.matches(it) && !Safety.CORE_OK.matches(it) }) {
            findings += Finding(
                Finding.Level.WARN,
                "Core Android packages removed",
                "This list contains Android system packages. The app refuses to disable them, " +
                    "because the TV may not boot again without them.",
            )
        }
        if (setupWraithSkipped) {
            findings += Finding(
                Finding.Level.WARN,
                "Setup wizard kept",
                "Disabling the setup wizard can delete the saved Wi-Fi network and drop ADB. " +
                    "Forget and re-join the network on the TV first, then run the list again.",
            )
        }
        if (currentHome == null) {
            findings += Finding(
                Finding.Level.WARN,
                "Home could not be identified",
                "The Home guard did not run. Check that this list does not disable the launcher " +
                    "before continuing.",
            )
        }
        if (tvInput == null) {
            findings += Finding(
                Finding.Level.WARN,
                "Input services were not checked",
                "The TV did not answer the TV_INPUT_HARDWARE check. Test every HDMI/AV input after " +
                    "the run; a port that notices the cable but stays black means an input service " +
                    "was disabled.",
            )
        } else if (tvInput.isNotEmpty()) {
            findings += Finding(
                Finding.Level.WARN,
                "Input services kept",
                "${tvInput.size} package(s) ask for TV_INPUT_HARDWARE. They stay enabled so HDMI, AV " +
                    "and the tuner keep working. If a package is a false positive on this TV, use the " +
                    "computer tools with TVINPUT_ALLOW=1.",
            )
        }
        if (wifiOwners.isEmpty()) {
            findings += Finding(
                Finding.Level.WARN,
                "Wi-Fi ownership unknown",
                "The app could not read which app owns the saved Wi-Fi networks, so the setup " +
                    "wizard is kept enabled.",
            )
        }
        if (device.isFireTv) {
            findings += Finding(
                Finding.Level.INFO,
                "Fire TV",
                "Fire OS keeps Home for itself: the debloat works, the launcher half usually does not.",
            )
        }
        if (isCvte) {
            findings += Finding(
                Finding.Level.WARN,
                "CVTE board",
                "The stock CVTE launcher is kept enabled. Set the new launcher as Home, reboot once, " +
                    "and confirm Home and ADB come back before changing anything else.",
            )
        }
        if (device.sdk in 1..25) {
            findings += Finding(
                Finding.Level.WARN,
                "Android ${device.androidVersion} (SDK ${device.sdk})",
                "The reference launcher needs Android 8.0 (SDK 26). The debloat still works; the " +
                    "launcher will not install on this TV.",
            )
        }
        if (adbSecure == "0") {
            findings += Finding(
                Finding.Level.WARN,
                "Network ADB is open",
                "ro.adb.secure=0 means any device on this network can get a shell without the " +
                    "Allow prompt. Turn debugging off and reboot the TV when you are done.",
            )
        }
        if (list.notes.isNotBlank()) {
            findings += Finding(Finding.Level.INFO, "Notes for this TV", list.notes)
        }
        for (line in list.header.take(6)) {
            findings += Finding(Finding.Level.INFO, "List header", line)
        }
        return findings
    }

    companion object {
        private const val HOME_QUERY =
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        private const val CLAIMS_QUERY =
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        private val SCALE_KEYS = listOf(
            "window_animation_scale",
            "transition_animation_scale",
            "animator_duration_scale",
        )
    }
}
