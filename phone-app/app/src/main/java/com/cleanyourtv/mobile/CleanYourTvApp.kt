package com.cleanyourtv.mobile

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import java.io.File

class CleanYourTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // One host key per install, kept in the app's private files. The TV remembers its public
        // half after the first "Allow USB debugging?" prompt, so later runs do not ask again.
        val dir = File(filesDir, "adb").apply { mkdirs() }
        val certFile = File(dir, "adbkey.pem")
        val keyFile = File(dir, "adbkey.pk8")
        val loaded = certFile.isFile && keyFile.isFile &&
            runCatching { KadbCert.set(certFile.readBytes(), keyFile.readBytes()) }.isSuccess
        if (!loaded) {
            val (cert, key) = KadbCert.get(notAfter = System.currentTimeMillis() + KEY_LIFETIME_MS)
            certFile.writeBytes(cert)
            keyFile.writeBytes(key)
        }
    }

    private companion object {
        // ADB keys normally outlive the TV; a short certificate would force a new pairing prompt.
        const val KEY_LIFETIME_MS = 10L * 365 * 24 * 60 * 60 * 1000
    }
}
