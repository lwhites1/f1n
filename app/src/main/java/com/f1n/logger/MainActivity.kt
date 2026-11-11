package com.f1n.logger

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Shows the live APDU log. While this screen is open, F1n claims taps as the
 * preferred payment service, so Wallet stays out of the way. Close or leave
 * the screen and Wallet gets the tap back.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private var cardEmulation: CardEmulation? = null
    private val service by lazy { ComponentName(this, LoggerService::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(24, 24, 24, 24)
        }
        scroll = ScrollView(this).apply { addView(logView) }
        setContentView(scroll)

        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null || !adapter.isEnabled) {
            LogStore.add("NFC is off or missing. Turn it on in settings.")
        } else {
            cardEmulation = CardEmulation.getInstance(adapter)
        }
        LogStore.listener = { runOnUiThread { render() } }
        render()
    }

    override fun onResume() {
        super.onResume()
        val ok = cardEmulation?.setPreferredService(this, service) ?: false
        LogStore.add(if (ok) "Armed: tap the phone to a terminal." else "Could not claim taps.")
    }

    override fun onPause() {
        cardEmulation?.unsetPreferredService(this)
        super.onPause()
    }

    override fun onDestroy() {
        LogStore.listener = null
        super.onDestroy()
    }

    private fun render() {
        logView.text = LogStore.lines.joinToString("\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}

object LogStore {
    val lines = mutableListOf<String>()
    var listener: (() -> Unit)? = null

    @Synchronized
    fun add(line: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        lines.add("$t $line")
        listener?.invoke()
    }
}
