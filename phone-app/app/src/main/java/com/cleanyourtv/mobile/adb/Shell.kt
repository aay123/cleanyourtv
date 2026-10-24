package com.cleanyourtv.mobile.adb

interface Shell {
    suspend fun exec(command: String): ShellResult
}

data class ShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
) {
    val output: String get() = stdout + stderr
    val ok: Boolean get() = exitCode == 0
}
