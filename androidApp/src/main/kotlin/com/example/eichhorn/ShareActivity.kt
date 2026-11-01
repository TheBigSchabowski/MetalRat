package com.example.eichhorn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Invisible target for "Teilen › Merken" and the text selection menu ("Merken"). Saves, toasts, closes. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = try {
            when (intent?.action) {
                Intent.ACTION_PROCESS_TEXT ->
                    Capturer.captureSelection(this, intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty())
                Intent.ACTION_SEND -> Capturer.captureShare(
                    this,
                    text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                    subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                    referrerPkg = referrer?.host?.takeIf { it != packageName && it != "android" },
                )
                else -> null
            }
        } catch (e: Exception) {
            "Konnte nicht merken: ${e.message}"
        }
        message?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show() }
        finish()
    }
}
