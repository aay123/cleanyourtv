package com.cleanyourtv.mobile.data

data class DeviceInfo(
    val model: String = "",
    val brand: String = "",
    val manufacturer: String = "",
    val device: String = "",
    val product: String = "",
    val name: String = "",
    val androidVersion: String = "",
    val sdk: Int = 0,
) {
    val label: String
        get() = listOf(model, brand.ifBlank { manufacturer })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" · ")
            .ifBlank { "Android TV" }

    val isFireTv: Boolean
        get() = listOf(model, brand, manufacturer, device, product, name)
            .any { it.contains("fire", ignoreCase = true) || it.contains("amazon", ignoreCase = true) }
}

data class TvList(
    val id: String,
    val title: String,
    val os: String,
    val notes: String,
    val header: List<String>,
    val packageCount: Int,
)

data class ListPackage(val name: String, val note: String?)

data class Finding(
    val level: Level,
    val title: String,
    val detail: String,
) {
    enum class Level { INFO, WARN, BLOCK }
}

data class Plan(
    val device: DeviceInfo,
    val list: TvList,
    val requested: List<ListPackage>,
    val effective: List<ListPackage>,
    val skipped: Map<String, String>,
    val findings: List<Finding>,
    val currentHome: String?,
    val homeClaims: List<String>,
    val setupWraithOwnsWifi: Boolean?,
    val wifiOwners: List<String>,
    val tvInputPackages: Set<String>?,
    val adbSecure: String?,
    val stockLauncher: String?,
    val isCvteBoard: Boolean,
    val installed: Set<String>,
    val disabledBefore: Set<String>,
) {
    val effectiveNames: List<String> get() = effective.map { it.name }
    val skippedCount: Int get() = skipped.size
}

data class BackupRecord(
    val id: String,
    val createdAt: Long,
    val host: String,
    val port: Int,
    val device: DeviceInfo,
    val listId: String,
    val listTitle: String,
    val installed: List<String>,
    val disabledBefore: List<String>,
    val targetStates: Map<String, String>,
    val homeComponent: String?,
    val animationScales: Map<String, String>,
    val launcherPackage: String?,
    val launcherApkName: String?,
    val dnsMode: String?,
    val dnsSpecifier: String?,
    val dnsApplied: Boolean = false,
    val applied: List<String> = emptyList(),
    val failed: Map<String, String> = emptyMap(),
    val finished: Boolean = false,
) {
    val disabledCount: Int get() = applied.size
    val failedCount: Int get() = failed.size
}

data class RestoreSummary(
    val enabled: Int,
    val alreadyEnabled: Int,
    val failed: Map<String, String>,
    val homeRestored: Boolean,
    val settingsRestored: Int,
)

data class Analysis(
    val plan: Plan,
    val animationScales: Map<String, String>,
    val dnsMode: String?,
    val dnsSpecifier: String?,
)
