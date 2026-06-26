package io.devguard.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import io.devguard.DevGuard
import io.devguard.models.GuardResponse
import io.devguard.models.LicenseStatus
import io.devguard.ui.branding.BrandingHelpers
import io.devguard.ui.branding.ContactUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import android.graphics.BitmapFactory

/**
 * Premium glass lock screen — parity with Flutter PaymentWall / RN LockScreen.
 */
internal class PremiumLockView(
    private val activity: Activity,
    private val response: GuardResponse,
    private val status: LicenseStatus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var showUnlock = false
    private var loading = false
    private var errorText: String? = null

    fun build(): View {
        val footer = BrandingHelpers.resolveFooter(response.branding)
        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        addBlob(root, footer.primaryColor, 0.15f, -0.2f, 0.8f, true)
        addBlob(root, footer.accentColor, 0.12f, 0.7f, 0.75f, false)
        addBlob(root, footer.primaryColor, 0.1f, 0.35f, 0.55f, true)

        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isFillViewport = true
        }

        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24))
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
        }

        val iconWrap = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100)).apply {
                bottomMargin = dp(24)
            }
            background = circleDrawable(footer.primaryColor, 0.13f, footer.primaryColor, 0.27f)
        }
        val iconView = TextView(activity).apply {
            text = "🛡️"
            textSize = 40f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        iconWrap.addView(iconView)
        card.addView(iconWrap)

        response.branding?.logoUrl?.takeIf { it.isNotBlank() }?.let { url ->
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        BitmapFactory.decodeStream(URL(url).openStream())
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bmp != null) {
                    iconView.visibility = View.GONE
                    iconWrap.addView(ImageView(activity).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        layoutParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
                    })
                }
            }
        }

        val defaultTitle = when (status) {
            LicenseStatus.EXPIRED -> "License Expired"
            LicenseStatus.PENDING -> "Connecting..."
            else -> "Access Restricted"
        }
        val title = TextView(activity).apply {
            text = (response.title?.takeIf { it.isNotBlank() } ?: defaultTitle).uppercase()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        card.addView(title)

        val divider = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(3)).apply {
                bottomMargin = dp(16)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                setColor(footer.primaryColor)
                cornerRadius = dp(2).toFloat()
            }
        }
        card.addView(divider)

        val defaultMsg = when (status) {
            LicenseStatus.PENDING -> "Verifying license with DevGuard..."
            LicenseStatus.EXPIRED -> "The license for this application has expired."
            else -> "This application has been remotely locked by the developer."
        }
        val message = TextView(activity).apply {
            text = response.message.ifBlank { defaultMsg }
            setTextColor(Color.argb(179, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.4f)
            setPadding(0, 0, 0, dp(32))
        }
        card.addView(message)

        val actionsHost = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        card.addView(actionsHost)

        fun rebuildActions() {
            actionsHost.removeAllViews()
            if (showUnlock && response.allowUnlock) {
                errorText?.let { err ->
                    actionsHost.addView(TextView(activity).apply {
                        text = err
                        setTextColor(Color.parseColor("#FF5252"))
                        gravity = Gravity.CENTER
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setPadding(0, 0, 0, dp(8))
                    })
                }
                val field = EditText(activity).apply {
                    hint = "License key"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.argb(102, 255, 255, 255))
                    background = inputBg()
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
                actionsHost.addView(field)

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(12), 0, 0)
                }
                row.addView(TextView(activity).apply {
                    text = "Cancel"
                    setTextColor(Color.argb(102, 255, 255, 255))
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        if (!loading) {
                            showUnlock = false
                            errorText = null
                            rebuildActions()
                        }
                    }
                })
                row.addView(FrameLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                    }
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    setOnClickListener {
                        if (loading) return@setOnClickListener
                        val key = field.text?.toString()?.trim().orEmpty()
                        if (key.isEmpty()) return@setOnClickListener
                        loading = true
                        rebuildActions()
                        scope.launch {
                            val ok = DevGuard.unlock(key)
                            loading = false
                            if (!ok) {
                                errorText = "Invalid unlock key. Please try again."
                                rebuildActions()
                            }
                        }
                    }
                    addView(
                        if (loading) ProgressBar(activity).apply {
                            indeterminateDrawable.setTint(Color.BLACK)
                        } else TextView(activity).apply {
                            text = "Unlock"
                            setTextColor(Color.BLACK)
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER
                        },
                    )
                })
                actionsHost.addView(row)
                return
            }

            ContactUrls.whatsApp(response.contactWhatsapp)?.let {
                actionsHost.addView(contactButton("💬  WhatsApp Support", Color.parseColor("#25D366"), it))
            }
            ContactUrls.mailto(response.contactEmail)?.let {
                actionsHost.addView(contactButton("📧  Email Support", footer.primaryColor, it))
            }
            ContactUrls.tel(response.contactPhone)?.let {
                actionsHost.addView(contactButton("📞  Call Support", Color.argb(26, 255, 255, 255), it))
            }
            if (response.allowUnlock) {
                actionsHost.addView(TextView(activity).apply {
                    text = "Enter unlock key"
                    setTextColor(Color.parseColor("#FCD34D"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(4))
                    setOnClickListener {
                        showUnlock = true
                        rebuildActions()
                    }
                })
            }
        }
        rebuildActions()

        outer.addView(card)

        if (!footer.hidePoweredBy) {
            val footerBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, 0)
                setOnClickListener {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(footer.url)))
                }
            }
            footerBox.addView(TextView(activity).apply {
                text = footer.label
                setTextColor(Color.argb(77, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                letterSpacing = 0.06f
                gravity = Gravity.CENTER
            })
            footerBox.addView(TextView(activity).apply {
                text = footer.brand.uppercase()
                setTextColor(Color.argb(153, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
            })
            outer.addView(footerBox)
        }

        scroll.addView(outer)
        root.addView(scroll)
        return root
    }

    private fun contactButton(label: String, color: Int, url: String): View {
        return TextView(activity).apply {
            text = label
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) }
            setOnClickListener {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    private fun addBlob(root: FrameLayout, color: Int, alpha: Float, topFrac: Float, sizeFrac: Float, topRight: Boolean) {
        root.post {
            val w = root.width.takeIf { it > 0 } ?: root.resources.displayMetrics.widthPixels
            val h = root.height.takeIf { it > 0 } ?: root.resources.displayMetrics.heightPixels
            val size = (w * sizeFrac).toInt()
            val blob = View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    this.alpha = (alpha * 255).toInt()
                }
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    if (topRight) {
                        topMargin = (h * topFrac).toInt()
                        marginStart = (w * 0.55f).toInt()
                    } else {
                        topMargin = (h * topFrac).toInt()
                        marginStart = (-w * 0.25f).toInt()
                    }
                }
            }
            root.addView(blob, 0)
        }
    }

    private fun glassCard() = GradientDrawable().apply {
        setColor(Color.argb(13, 255, 255, 255))
        cornerRadius = dp(32).toFloat()
        setStroke(dp(2), Color.argb(26, 255, 255, 255))
    }

    private fun circleDrawable(fill: Int, fillAlpha: Float, stroke: Int, strokeAlpha: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb((fillAlpha * 255).toInt(), Color.red(fill), Color.green(fill), Color.blue(fill)))
            setStroke(dp(1), Color.argb((strokeAlpha * 255).toInt(), Color.red(stroke), Color.green(stroke), Color.blue(stroke)))
        }

    private fun inputBg() = GradientDrawable().apply {
        setColor(Color.argb(13, 255, 255, 255))
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.argb(26, 255, 255, 255))
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        v.toFloat(),
        activity.resources.displayMetrics,
    ).toInt()
}

internal object PendingViewFactory {
    fun create(activity: Activity, message: String): View {
        return FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ProgressBar(activity).apply {
                    indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3B82F6"))
                })
                addView(TextView(activity).apply {
                    text = message.ifBlank { "Connecting to security server..." }
                    setTextColor(Color.argb(204, 255, 255, 255))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(activity, 24), 0, 0)
                })
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ))
        }
    }

    private fun dp(activity: Activity, v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        v.toFloat(),
        activity.resources.displayMetrics,
    ).toInt()
}

internal object WarningBannerFactory {
    fun create(activity: Activity, title: String?, message: String): View {
        val banner = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 16))
            background = GradientDrawable().apply {
                setColor(Color.argb(38, 255, 193, 7))
                cornerRadius = dp(activity, 20).toFloat()
                setStroke(dp(activity, 2), Color.argb(77, 255, 160, 0))
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(activity, 16)
                marginStart = dp(activity, 16)
                marginEnd = dp(activity, 16)
            }
        }
        banner.addView(TextView(activity).apply {
            text = "⚠️"
            textSize = 20f
            setPadding(0, 0, dp(activity, 16), 0)
        })
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(activity).apply {
            text = (title?.takeIf { it.isNotBlank() } ?: "License Attention").uppercase()
            setTextColor(Color.parseColor("#FFD54F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        })
        col.addView(TextView(activity).apply {
            text = message
            setTextColor(Color.argb(230, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(activity, 4), 0, 0)
        })
        banner.addView(col)
        return banner
    }

    private fun dp(activity: Activity, v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        v.toFloat(),
        activity.resources.displayMetrics,
    ).toInt()
}
