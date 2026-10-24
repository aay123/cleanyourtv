package com.cleanyourtv.mobile.safety

import com.cleanyourtv.mobile.data.ListPackage

/**
 * The hard safety rules from tools/disable.sh, kept as pure functions so they can be tested on the
 * JVM. Nothing here talks to a TV.
 */
object Safety {
    // Keep CORE_RE and CORE_OK in step with tools/disable.sh, tools/recovery.py and the tests there.
    val CORE_RE = Regex(
        "^(android|com\\.android\\.systemui|com\\.android\\.shell|com\\.android\\.phone|" +
            "com\\.android\\.settings|com\\.android\\.tv\\.settings|" +
            "com\\.(google\\.)?android\\.(permissioncontroller|packageinstaller|ext\\.services|" +
            "ext\\.shared|modulemetadata)|com\\.google\\.android\\.overlay\\.modules.*|" +
            "com\\.android\\.providers\\..*)$",
    )
    val CORE_OK = Regex("^com\\.android\\.providers\\.(calendar|contacts|userdictionary|blockednumber)$")

    val SETUPWRAITH = "com.google.android.tungsten.setupwraith"

    private val PACKAGE_RE = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val NOT_LAUNCHERS = setOf(
        SETUPWRAITH,
        "com.android.tv.settings",
        "com.android.settings",
        "com.android.provision",
    )
    private val FALLBACK_HOME_RE = Regex("/\\S*FallbackHome$")
    private val FACTORY_HOME_RE = Regex("factorytest|factory_test|factorymode", RegexOption.IGNORE_CASE)

    /** Splits a requested list into what may be disabled and the core names that never may be. */
    fun removeCore(requested: List<ListPackage>): Pair<List<ListPackage>, Map<String, String>> {
        val blocked = LinkedHashMap<String, String>()
        val kept = requested.filter { pkg ->
            val isCore = CORE_RE.matches(pkg.name) && !CORE_OK.matches(pkg.name)
            if (isCore) blocked[pkg.name] = "core Android package; disabling it can stop the TV booting"
            !isCore
        }
        return kept to blocked
    }

    fun isPackageName(value: String): Boolean = PACKAGE_RE.matches(value)

    fun homePackage(component: String?): String? =
        component?.substringBefore('/')?.takeIf { PACKAGE_RE.matches(it) }

    /**
     * Whether a HOME claimant counts as a launcher the viewer can actually use. The setup wizard,
     * the settings apps, a vendor FallbackHome and a factory-test launcher answer HOME but leave the
     * viewer somewhere they cannot live.
     */
    fun isRealLauncher(component: String, owner: String?): Boolean {
        if (FALLBACK_HOME_RE.containsMatchIn(component)) return false
        if (FACTORY_HOME_RE.containsMatchIn(component)) return false
        if (owner != null && owner in NOT_LAUNCHERS) return false
        return true
    }

    /** Packages that could take Home once the requested list is applied. */
    fun otherHomeOwners(
        claims: List<String>,
        currentHome: String?,
        requested: Set<String>,
    ): List<String> {
        val currentOwner = homePackage(currentHome)
        return claims
            .filter { isRealLauncher(it, homePackage(it)) }
            .mapNotNull { homePackage(it) }
            .distinct()
            .filter { it != currentOwner && it !in requested && it !in NOT_LAUNCHERS }
    }

    /** When Home could not be identified, every package that claims it stays enabled. */
    fun protectedWhenHomeUnknown(claims: List<String>, currentHome: String?): Set<String> =
        if (currentHome != null) emptySet() else claims.mapNotNull { homePackage(it) }.toSet()

    fun parsePackageLines(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it == "android" || PACKAGE_RE.matches(it) }
            .toSet()

    fun parseHomeClaims(output: String): List<String> =
        Regex("([A-Za-z_][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+)")
            .findAll(output)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

    fun parseWifiOwners(output: String): List<String> =
        Regex("cname=(\\S+)").findAll(output).map { it.groupValues[1] }.distinct().toList()

    fun parseTvInputPackages(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { PACKAGE_RE.matches(it) }
            .toSet()
}
