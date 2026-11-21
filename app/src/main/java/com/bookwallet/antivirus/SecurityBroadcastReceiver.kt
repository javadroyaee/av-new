package com.bookwallet.antivirus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SecurityBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            for (sms in messages) {
                val sender = sms.originatingAddress ?: "ناشناس"
                val body = sms.messageBody ?: ""
                val analysis = SecurityAnalyzer.analyzeSms(sender, body)
                if (analysis.isDangerous) {
                    val alertIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("TRIGGER_ALERT", true)
                        putExtra("ALERT_TITLE", "هشدار پیامک حاوی لینک مشکوک")
                        putExtra("ALERT_DESC", "فرستنده: $sender\n$body\n\nتشخیص: ${analysis.detail}")
                    }
                    context.startActivity(alertIntent)
                }
            }
        }
    }
}

object SecurityAnalyzer {
    data class AnalysisResult(val isDangerous: Boolean, val detail: String)

    fun analyzeSms(sender: String, body: String): AnalysisResult {
        val lower = body.lowercase()
        val hasPhishingKeywords = listOf("ابلاغیه", "ثنا", "سهام عدالت", "یارانه", "مسدود", "حساب بانکی", "بسته پستی").any { lower.contains(it) }
        val hasUrl = Regex("(https?://|www\\.|bit\\.ly|t\\.me|zil\\.ink|[a-z0-9-]+\\.(apk|xyz|top|site))").containsMatchIn(lower)
        if (hasUrl && (hasPhishingKeywords || sender.startsWith("+989") || sender.startsWith("09"))) {
            return AnalysisResult(true, "پیامک حاوی لینک فیشینگ و کلمات فریبنده شناسایی شد!")
        }
        return AnalysisResult(false, "ایمن")
    }

    fun isSpywareOrHidden(packageName: String, appName: String, hasLauncherIcon: Boolean): Boolean {
        if (isWhitelisted(packageName)) return false
        val combined = "$packageName $appName".lowercase()
        val keywords = listOf("spy", "hack", "keylog", "monitor", "tracker", "rat", "hidden", "smsreader")
        return !hasLauncherIcon || keywords.any { combined.contains(it) }
    }

    fun isFreeVpnRisk(packageName: String, appName: String): Boolean {
        if (isWhitelisted(packageName)) return false
        val combined = "$packageName $appName".lowercase()
        return combined.contains("vpn") || combined.contains("proxy") || combined.contains("turbo") || combined.contains("hotspot")
    }

    private fun isWhitelisted(pkg: String): Boolean {
        val white = setOf(
            "com.whatsapp", "org.telegram.messenger", "com.instagram.android",
            "ir.eitaa.messenger", "ir.nasim", "com.farsitel.bazaar",
            "com.android.chrome", "com.google.android.youtube"
        )
        return white.contains(pkg) || pkg.startsWith("com.google.")
    }
}