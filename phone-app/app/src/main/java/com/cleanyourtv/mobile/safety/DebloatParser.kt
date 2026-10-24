package com.cleanyourtv.mobile.safety

import com.cleanyourtv.mobile.data.ListPackage

class DebloatFormatException(val line: Int, val token: String, message: String) : Exception(message)

/**
 * Reads the same format as tools/disable.sh and tools/recovery.py: one bare package name per line,
 * `#` starts a comment, a UTF-8 BOM and CRLF are ignored. Anything that is not a package name stops
 * the whole list, because a name is pasted into a shell command on the TV.
 */
object DebloatParser {
    private val PACKAGE_RE = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun isPackageName(value: String): Boolean = PACKAGE_RE.matches(value)

    fun parse(text: String): List<ListPackage> {
        val names = LinkedHashMap<String, String?>()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
            val line = raw.removeSuffix("\r")
            val hash = line.indexOf('#')
            val code = if (hash >= 0) line.substring(0, hash) else line
            val note = if (hash >= 0) line.substring(hash + 1).trim().ifBlank { null } else null
            val token = code.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            if (token.isEmpty()) return@forEachIndexed
            if (!PACKAGE_RE.matches(token)) {
                throw DebloatFormatException(
                    index + 1,
                    token,
                    "line ${index + 1}: not a package name: $token",
                )
            }
            names.putIfAbsent(token, note)
        }
        if (names.isEmpty()) {
            throw DebloatFormatException(0, "", "the list has no package names")
        }
        return names.map { ListPackage(it.key, it.value) }
    }

    fun header(text: String): List<String> {
        val lines = mutableListOf<String>()
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.removeSuffix("\r").trim()
            if (!line.startsWith("#")) break
            val value = line.trimStart('#').trim()
            if (value.isNotEmpty()) lines += value
            if (lines.size >= 14) break
        }
        return lines
    }
}
