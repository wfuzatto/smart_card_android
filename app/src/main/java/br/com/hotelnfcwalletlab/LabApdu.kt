package br.com.hotelnfcwalletlab

/** Proprietary lab AID: F0 + ASCII HOTEL + 01. Not a payment or BIS identifier. */
object LabApdu {
    const val AID = "F0484F54454C01"
    private val OK = byteArrayOf(0x90.toByte(), 0x00)
    private val NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    private val WRONG_STATE = byteArrayOf(0x69, 0x85.toByte())
    private val BAD_LENGTH = byteArrayOf(0x67, 0x00)
    private val UNSUPPORTED = byteArrayOf(0x6D, 0x00)

    fun decode(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    fun hex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.matches(Regex("[0-9a-fA-F]*")))
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    data class Reply(val bytes: ByteArray, val selected: Boolean, val event: String)

    fun process(command: ByteArray, enabled: Boolean, selected: Boolean, opaqueToken: String): Reply {
        if (!enabled) return Reply(WRONG_STATE, false, "HCE desativado")
        if (command.size < 4) return Reply(BAD_LENGTH, false, "APDU incompleta")
        val cla = command[0].toInt() and 0xFF
        val ins = command[1].toInt() and 0xFF
        val p1 = command[2].toInt() and 0xFF
        val p2 = command[3].toInt() and 0xFF
        if (cla == 0 && ins == 0xA4 && p1 == 4 && p2 == 0) {
            if (command.size < 5) return Reply(BAD_LENGTH, false, "SELECT sem Lc")
            val len = command[4].toInt() and 0xFF
            if (len != 7 || command.size < 5 + len) return Reply(NOT_FOUND, false, "SELECT AID desconhecido")
            val aid = decode(command.copyOfRange(5, 5 + len))
            return if (aid == AID) Reply(OK, true, "AID selecionado")
                   else Reply(NOT_FOUND, false, "Outro AID")
        }
        if (cla == 0x80 && ins == 0xCA && p1 == 0 && p2 == 0) {
            if (!selected || opaqueToken.isBlank()) return Reply(WRONG_STATE, selected, "Sem seleção/credencial")
            val data = opaqueToken.toByteArray(Charsets.US_ASCII)
            if (data.size > 230) return Reply(BAD_LENGTH, selected, "Identificador muito longo")
            return Reply(data + OK, selected, "GET DATA respondido")
        }
        return Reply(UNSUPPORTED, selected, "Comando não suportado")
    }
}
