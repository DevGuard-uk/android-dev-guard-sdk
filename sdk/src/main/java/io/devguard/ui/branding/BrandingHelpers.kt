package io.devguard.ui.branding

import android.graphics.Color
import io.devguard.models.LockScreenBranding

internal data class BrandingFooter(
    val label: String,
    val brand: String,
    val url: String,
    val hidePoweredBy: Boolean,
    val primaryColor: Int,
    val accentColor: Int,
    val hasCustom: Boolean,
)

internal object BrandingHelpers {
    private const val DEFAULT_PRIMARY = "#D32F2F"
    private const val DEFAULT_ACCENT = "#B71C1C"

    fun resolveFooter(branding: LockScreenBranding?): BrandingFooter {
        val hasCustom = branding != null && (
            !branding.brandName.isNullOrBlank() || !branding.logoUrl.isNullOrBlank()
            )
        val primary = parseColor(branding?.primaryColor, DEFAULT_PRIMARY)
        val accent = parseColor(branding?.accentColor ?: branding?.primaryColor, DEFAULT_ACCENT)
        return BrandingFooter(
            label = if (hasCustom) "Powered by" else "Secured by",
            brand = if (hasCustom) branding?.brandName?.trim().takeUnless { it.isNullOrBlank() } ?: "DevGuard" else "DevGuard",
            url = if (hasCustom && !branding?.websiteUrl.isNullOrBlank()) branding!!.websiteUrl!!.trim() else "https://devguard.uk",
            hidePoweredBy = branding?.hidePoweredBy == true,
            primaryColor = primary,
            accentColor = accent,
            hasCustom = hasCustom,
        )
    }

    fun parseColor(hex: String?, fallback: String): Int {
        val raw = hex?.trim()?.let { if (it.startsWith("#")) it else "#$it" } ?: fallback
        return try {
            Color.parseColor(if (raw.length == 7) raw else fallback)
        } catch (_: Exception) {
            Color.parseColor(fallback)
        }
    }
}

internal object ContactUrls {
    fun whatsApp(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotEmpty()) "https://wa.me/$digits" else null
    }

    fun mailto(email: String): String? =
        if (email.isNotBlank()) "mailto:$email" else null

    fun tel(phone: String): String? {
        val cleaned = phone.filter { it.isDigit() || it == '+' }
        return if (cleaned.isNotEmpty()) "tel:$cleaned" else null
    }
}
