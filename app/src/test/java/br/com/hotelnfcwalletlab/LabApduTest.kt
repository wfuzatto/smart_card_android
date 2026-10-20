package br.com.hotelnfcwalletlab

import org.junit.Assert.*
import org.junit.Test

class LabApduTest {
    private val select = LabApdu.hex("00A4040007" + LabApdu.AID)
    private val read = LabApdu.hex("80CA000000")

    @Test fun selectAndGetData() {
        val selected = LabApdu.process(select, true, false, "opaque-123")
        assertEquals("9000", LabApdu.decode(selected.bytes))
        assertTrue(selected.selected)
        val reply = LabApdu.process(read, true, selected.selected, "opaque-123")
        assertEquals("6F70617175652D3132339000", LabApdu.decode(reply.bytes))
    }
    @Test fun disabledOrUnknownAid() {
        assertEquals("6985", LabApdu.decode(LabApdu.process(select, false, false, "x").bytes))
        assertEquals("6A82", LabApdu.decode(LabApdu.process(LabApdu.hex("00A4040007F0010203040506"), true, false, "x").bytes))
        assertEquals("6985", LabApdu.decode(LabApdu.process(read, true, false, "x").bytes))
    }
    @Test fun unsupportedOperation() {
        assertEquals("6D00", LabApdu.decode(LabApdu.process(LabApdu.hex("FF000000"), true, true, "x").bytes))
    }
}
