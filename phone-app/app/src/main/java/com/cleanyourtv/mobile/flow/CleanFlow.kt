package com.cleanyourtv.mobile.flow

import com.cleanyourtv.mobile.adb.Shell
import com.cleanyourtv.mobile.data.Analysis
import com.cleanyourtv.mobile.data.BackupRecord
import com.cleanyourtv.mobile.data.BackupStore
import com.cleanyourtv.mobile.data.RestoreSummary
import com.cleanyourtv.mobile.safety.Safety
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

interface FlowReporter {
    suspend fun step(key: String, text: String)
    suspend fun log(text: String)
    suspend fun progress(done: Int, total: Int)
}

class CleanFlow(
    private val shell: Shell,
    private val backups: BackupStore,
    private val installLauncher: suspend (File) -> Unit,
) {

    suspend fun run(
        analysis: Analysis,
        host: String,
        port: Int,
        launcherApk: File?,
        launcherPackage: String?,
        applyDns: Boolean,
        reporter: FlowReporter,
    ): BackupRecord {
        val plan = analysis.plan
        val createdAt = System.currentTimeMillis()
        var record = BackupRecord(
            id = "$createdAt-${plan.device.model.ifBlank { "tv" }.replace(Regex("[^A-Za-z0-9._-]"), "_")}",
            createdAt = createdAt,
            host = host,
            port = port,
            device = plan.device,
            listId = plan.list.id,
            listTitle = plan.list.title,
            installed = plan.installed.sorted(),
            disabledBefore = plan.disabledBefore.sorted(),
            targetStates = plan.requested.associate { pkg ->
                pkg.name to when {
                    pkg.name !in plan.installed -> "absent"
                    pkg.name in plan.disabledBefore -> "disabled"
                    else -> "enabled"
                }
            },
            homeComponent = plan.currentHome,
            animationScales = analysis.animationScales,
            launcherPackage = launcherPackage,
            launcherApkName = launcherApk?.name,
            dnsMode = analysis.dnsMode,
            dnsSpecifier = analysis.dnsSpecifier,
        )

        reporter.step("backup", "Writing the restore record")
        backups.save(record)
        reporter.log("Recorded ${record.targetStates.size} packages, Home ${plan.currentHome ?: "unknown"}")
        reporter.log("Saved before the first change. Restore is available from the home screen.")

        var homeVerified = false
        if (launcherApk != null && launcherPackage != null) {
            reporter.step("install", "Installing the launcher")
            installLauncher(launcherApk)
            reporter.log("Installed $launcherPackage")

            reporter.step("home", "Making the new launcher Home")
            val component = findHomeComponent(launcherPackage)
            if (component == null) {
                reporter.log("$launcherPackage declares no HOME activity; the stock launcher stays.")
            } else {
                val setHome = shell.exec("cmd package set-home-activity $component")
                reporter.log(setHome.output.trim().ifBlank { "set-home-activity sent" })
                homeVerified = verifyHome(launcherPackage, reporter)
                if (homeVerified) {
                    reporter.log("Home is $component")
                } else {
                    reporter.log("Home did not switch to $launcherPackage; the stock launcher stays enabled.")
                }
            }
        } else {
            reporter.log("No launcher APK selected: the stock launcher stays enabled.")
        }

        // If Home could not be identified, keep every package that claims it instead of guessing.
        val unknownHomeClaims = Safety.protectedWhenHomeUnknown(plan.homeClaims, plan.stockLauncher)
        val toDisable = plan.effectiveNames.filter { name ->
            name in plan.installed &&
                name != launcherPackage &&
                name !in unknownHomeClaims &&
                !shouldProtectStock(name, plan.stockLauncher, homeVerified, plan.isCvteBoard)
        }
        val protectedStock = plan.effectiveNames.filter { name ->
            name in unknownHomeClaims ||
                shouldProtectStock(name, plan.stockLauncher, homeVerified, plan.isCvteBoard)
        }
        if (protectedStock.isNotEmpty()) {
            reporter.log("Keeping the stock launcher: ${protectedStock.joinToString(", ")}")
        }
        reporter.log("${toDisable.size} packages to disable, ${plan.skipped.size} already skipped by safety checks.")

        val applied = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()
        var attempted = 0
        val total = toDisable.size
        var done = 0
        reporter.step("disable", "Disabling packages")
        reporter.progress(0, total)

        for (batch in toDisable.chunked(BATCH_SIZE)) {
            val batchAttempts = ArrayList<String>()
            for (name in batch) {
                if (name in plan.disabledBefore) {
                    reporter.log("$name was already disabled; leaving it as it was.")
                    done++
                    reporter.progress(done, total)
                    continue
                }
                attempted++
                batchAttempts += name
                val result = runCatching { shell.exec("pm disable-user --user 0 $name") }
                    .getOrElse { error ->
                        if (error is IOException) throw error
                        failed[name] = error.message ?: "command failed"
                        done++
                        reporter.progress(done, total)
                        continue
                    }
                if (result.output.contains("Exception", ignoreCase = true) ||
                    result.output.contains("illegal", ignoreCase = true) ||
                    result.output.contains("Error", ignoreCase = true)
                ) {
                    failed[name] = result.output.trim().take(240).ifBlank { "the TV refused it" }
                }
                done++
                reporter.progress(done, total)
            }
            if (batchAttempts.isNotEmpty()) {
                val nowDisabled = Safety.parsePackageLines(shell.exec("pm list packages -d").output)
                for (name in batchAttempts) {
                    when {
                        name in nowDisabled -> {
                            applied += name
                            failed.remove(name)
                        }
                        name in plan.disabledBefore -> Unit
                        else -> failed.putIfAbsent(name, "the TV did not list it as disabled after the batch")
                    }
                }
                record = record.copy(applied = applied.toList(), failed = failed.toMap())
                backups.save(record)
                reporter.log("Batch checked: ${applied.size} disabled so far.")
            }
            if (attempted > 0 && applied.isEmpty()) {
                throw IOException(
                    "the TV refused every pm disable-user command. Nothing was changed. " +
                        "Some firmware (Fire OS 5, for example) does not allow this; stop here.",
                )
            }
        }

        reporter.step("verify", "Checking the disabled list")
        val finalDisabled = Safety.parsePackageLines(shell.exec("pm list packages -d").output)
        for (name in applied.toList()) {
            if (name !in finalDisabled) {
                applied.remove(name)
                failed[name] = "the firmware re-enabled it before the run finished"
            }
        }

        if (applyDns) {
            reporter.step("dns", "Setting ad-blocking Private DNS")
            shell.exec("settings put global private_dns_specifier dns.adguard-dns.com")
            val mode = shell.exec("settings put global private_dns_mode hostname")
            if (mode.ok) {
                record = record.copy(dnsApplied = true)
                reporter.log("Private DNS set to dns.adguard-dns.com. YouTube ads are not affected.")
            } else {
                reporter.log("Private DNS was refused: ${mode.output.trim()}")
            }
        }

        record = record.copy(
            applied = applied.sorted(),
            failed = failed.toMap(),
            finished = true,
        )
        backups.save(record)
        reporter.step("done", "Finished")
        reporter.progress(total, total)
        return record
    }

    suspend fun restore(record: BackupRecord, reporter: FlowReporter): RestoreSummary {
        reporter.step("restore", "Re-enabling packages")
        val messages = LinkedHashMap<String, String>()
        val disabledNow = Safety.parsePackageLines(shell.exec("pm list packages -d").output)
        val toRestore = LinkedHashSet<String>()
        toRestore += record.applied
        // Also catch a package disabled in a batch that the run did not get to record, for example
        // when the phone lost the connection halfway through it.
        for ((name, state) in record.targetStates) {
            if (state == "enabled" && name in disabledNow) toRestore += name
        }
        val wasDisabled = toRestore.filter { it in disabledNow }
        val alreadyEnabled = toRestore.size - wasDisabled.size
        val total = toRestore.size
        var done = 0
        reporter.progress(0, total)

        for (name in wasDisabled) {
            val result = runCatching { shell.exec("pm enable $name") }.getOrElse { error ->
                if (error is IOException) throw error
                messages[name] = error.message ?: "command failed"
                done++
                reporter.progress(done, total)
                continue
            }
            if (!result.ok || result.output.contains("Exception", ignoreCase = true)) {
                messages[name] = result.output.trim().take(240).ifBlank { "the TV refused it" }
            }
            done++
            reporter.progress(done, total)
        }

        val stillDisabled = Safety.parsePackageLines(shell.exec("pm list packages -d").output)
        val failed = LinkedHashMap<String, String>()
        for (name in wasDisabled) {
            if (name in stillDisabled) {
                failed[name] = messages[name] ?: "still disabled after pm enable"
            }
        }
        val enabled = wasDisabled.size - failed.size

        var homeRestored = false
        val home = record.homeComponent
        if (home != null) {
            reporter.step("restore_home", "Restoring Home")
            shell.exec("cmd package set-home-activity $home")
            val answer = Safety.parseHomeClaims(shell.exec(HOME_QUERY).output).lastOrNull()
            homeRestored = Safety.homePackage(answer) == Safety.homePackage(home)
            if (!homeRestored) {
                reporter.log("Home was not restored automatically. Set it on the TV before rebooting.")
            }
        }

        var settingsRestored = 0
        reporter.step("restore_settings", "Restoring display settings")
        for ((key, value) in record.animationScales) {
            val result = shell.exec("settings put global $key $value")
            if (result.ok) settingsRestored++
        }
        if (record.dnsApplied) {
            val mode = record.dnsMode ?: "opportunistic"
            shell.exec("settings put global private_dns_mode $mode")
            if (record.dnsSpecifier.isNullOrBlank()) {
                shell.exec("settings delete global private_dns_specifier")
            } else {
                shell.exec("settings put global private_dns_specifier ${record.dnsSpecifier}")
            }
        }

        reporter.step("restore_done", "Restore finished")
        reporter.progress(total, total)
        return RestoreSummary(
            enabled = enabled,
            alreadyEnabled = alreadyEnabled,
            failed = failed,
            homeRestored = homeRestored,
            settingsRestored = settingsRestored,
        )
    }

    private fun shouldProtectStock(
        name: String,
        stockLauncher: String?,
        homeVerified: Boolean,
        isCvte: Boolean,
    ): Boolean {
        // A CVTE board hangs on its boot animation when its own launcher is gone, so it stays
        // enabled even after the new launcher answers HOME.
        if (isCvte && name.startsWith("com.cvte.tv.launcher")) return true
        if (stockLauncher == null || name != stockLauncher) return false
        if (isCvte) return true
        return !homeVerified
    }

    private suspend fun findHomeComponent(packageName: String): String? {
        val output = shell.exec(CLAIMS_QUERY).output
        return Safety.parseHomeClaims(output).firstOrNull { Safety.homePackage(it) == packageName }
    }

    private suspend fun verifyHome(packageName: String, reporter: FlowReporter): Boolean {
        shell.exec("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
        delay(1500)
        val focused = shell.exec("dumpsys activity activities").output
        val resumed = Regex("mResumedActivity:.*?([A-Za-z_][A-Za-z0-9_.]*)/").find(focused)
            ?.groupValues?.get(1)
        val focusedApp = Regex("mFocusedApp=.*?([A-Za-z_][A-Za-z0-9_.]*)/").find(focused)
            ?.groupValues?.get(1)
        val answer = Safety.parseHomeClaims(shell.exec(HOME_QUERY).output).lastOrNull()
        reporter.log("Home answers: ${answer ?: "nothing"}")
        return resumed == packageName || focusedApp == packageName ||
            Safety.homePackage(answer) == packageName
    }

    companion object {
        private const val BATCH_SIZE = 8
        private const val HOME_QUERY =
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        private const val CLAIMS_QUERY =
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    }
}
