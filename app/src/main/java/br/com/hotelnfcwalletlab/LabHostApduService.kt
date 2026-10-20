package br.com.hotelnfcwalletlab

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.time.Instant

/** Isolated ISO-DEP laboratory service, never a MIFARE Classic emulator. */
class LabHostApduService : HostApduService() {
    private var selected = false
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val prefs = getSharedPreferences("lab_hce", MODE_PRIVATE)
        val reply = LabApdu.process(commandApdu, prefs.getBoolean("enabled", false),
            selected, prefs.getString("token", "") ?: "")
        selected = reply.selected
        val log = Instant.now().toString() + " RX " + LabApdu.decode(commandApdu) +
            " TX " + LabApdu.decode(reply.bytes) + " " + reply.event
        val old = prefs.getString("logs", "") ?: ""
        prefs.edit().putString("logs", (log + "\n" + old).take(16000)).apply()
        return reply.bytes
    }
    override fun onDeactivated(reason: Int) {
        selected = false
        val prefs = getSharedPreferences("lab_hce", MODE_PRIVATE)
        prefs.edit().putString("logs", Instant.now().toString() +
            " NFC desconectado motivo=" + reason + "\n" +
            (prefs.getString("logs", "") ?: "")).apply()
    }
}
