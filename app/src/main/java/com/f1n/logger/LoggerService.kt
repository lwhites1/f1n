package com.f1n.logger

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Step 1 experiment: answer the terminal just far enough that it sends the
 * transaction amount, log it, then fail the read on purpose (status 6985).
 * Nothing here can complete a payment.
 */
class LoggerService : HostApduService() {

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        LogStore.add("<- ${apdu.hex()}")
        val reply = respond(apdu)
        LogStore.add("-> ${reply.hex()}")
        return reply
    }

    override fun onDeactivated(reason: Int) {
        LogStore.add("-- link lost (reason=$reason)")
    }

    private fun respond(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_UNKNOWN
        val ins = apdu[1].toInt() and 0xFF

        // SELECT
        if (ins == 0xA4) {
            val lc = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
            val name = if (apdu.size >= 5 + lc) apdu.copyOfRange(5, 5 + lc) else ByteArray(0)
            return when (name.hex()) {
                PPSE_HEX -> ppseResponse()
                VISA_HEX, MC_HEX -> aidResponse(name)
                else -> SW_NOT_FOUND
            }
        }

        // GET PROCESSING OPTIONS: data holds the PDOL values the terminal sent
        if (ins == 0xA8) {
            parseGpo(apdu)
            return SW_CONDITIONS_NOT_SATISFIED // fail the read on purpose
        }

        return SW_UNKNOWN
    }

    private fun parseGpo(apdu: ByteArray) {
        // 80 A8 00 00 Lc | 83 len <PDOL data> | 00
        if (apdu.size < 9 || (apdu[5].toInt() and 0xFF) != 0x83) {
            LogStore.add("GPO: unexpected format")
            return
        }
        val len = apdu[6].toInt() and 0xFF
        val d = apdu.copyOfRange(7, minOf(7 + len, apdu.size))
        if (d.size < PDOL_LEN) {
            LogStore.add("GPO: short PDOL data (${d.size} of $PDOL_LEN bytes)")
            return
        }
        // Order matches PDOL below: 9F02(6) 9F03(6) 9F1A(2) 95(5) 5F2A(2) 9A(3) 9C(1) 9F37(4)
        val amount = d.copyOfRange(0, 6).hex()
        val country = d.copyOfRange(12, 14).hex()
        val currency = d.copyOfRange(19, 21).hex()
        val date = d.copyOfRange(21, 24).hex()
        val type = d.copyOfRange(24, 25).hex()
        val cents = amount.toLongOrNull() ?: -1L
        LogStore.add(
            "AMOUNT: raw=$amount  => ${"%d.%02d".format(cents / 100, cents % 100)}  " +
                "country=$country currency=$currency date=$date type=$type"
        )
    }

    // 6F { 84 PPSE, A5 { BF0C { 61 { 4F Visa, 50 label, 87 01 } 61 { 4F MC ... } } } } 90 00
    private fun ppseResponse(): ByteArray {
        fun entry(aid: ByteArray, label: String): ByteArray {
            val l = label.toByteArray()
            val inner = byteArrayOf(0x4F, aid.size.toByte()) + aid +
                byteArrayOf(0x50, l.size.toByte()) + l +
                byteArrayOf(0x87.toByte(), 0x01, 0x01)
            return byteArrayOf(0x61, inner.size.toByte()) + inner
        }
        val entries = entry(VISA, "VISA") + entry(MC, "MASTERCARD")
        val bf0c = byteArrayOf(0xBF.toByte(), 0x0C, entries.size.toByte()) + entries
        val a5 = byteArrayOf(0xA5.toByte(), bf0c.size.toByte()) + bf0c
        val name = PPSE_NAME.toByteArray()
        val df = byteArrayOf(0x84.toByte(), name.size.toByte()) + name
        val body = df + a5
        return byteArrayOf(0x6F, body.size.toByte()) + body + SW_OK
    }

    // 6F { 84 AID, A5 { 50 label, 9F38 PDOL } } 90 00
    private fun aidResponse(aid: ByteArray): ByteArray {
        val label = "F1N LOGGER".toByteArray()
        val pdol = byteArrayOf(
            0x9F.toByte(), 0x02, 0x06,  // amount authorised
            0x9F.toByte(), 0x03, 0x06,  // amount other
            0x9F.toByte(), 0x1A, 0x02,  // terminal country
            0x95.toByte(), 0x05,        // TVR
            0x5F, 0x2A, 0x02,           // currency
            0x9A.toByte(), 0x03,        // date
            0x9C.toByte(), 0x01,        // transaction type
            0x9F.toByte(), 0x37, 0x04   // unpredictable number
        )
        val a5Body = byteArrayOf(0x50, label.size.toByte()) + label +
            byteArrayOf(0x9F.toByte(), 0x38, pdol.size.toByte()) + pdol
        val a5 = byteArrayOf(0xA5.toByte(), a5Body.size.toByte()) + a5Body
        val df = byteArrayOf(0x84.toByte(), aid.size.toByte()) + aid
        val body = df + a5
        return byteArrayOf(0x6F, body.size.toByte()) + body + SW_OK
    }

    companion object {
        private const val PPSE_NAME = "2PAY.SYS.DDF01"
        private const val PPSE_HEX = "325041592E5359532E4444463031"
        private const val VISA_HEX = "A0000000031010"
        private const val MC_HEX = "A0000000041010"
        private val VISA = byteArrayOf(0xA0.toByte(), 0, 0, 0, 0x03, 0x10, 0x10)
        private val MC = byteArrayOf(0xA0.toByte(), 0, 0, 0, 0x04, 0x10, 0x10)
        private const val PDOL_LEN = 29 // 6+6+2+5+2+3+1+4

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
        private val SW_UNKNOWN = byteArrayOf(0x6F, 0x00)
    }
}

fun ByteArray.hex(): String = joinToString("") { "%02X".format(it) }
