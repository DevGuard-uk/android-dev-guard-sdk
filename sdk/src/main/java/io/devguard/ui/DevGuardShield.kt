package io.devguard.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.devguard.DevGuard
import io.devguard.models.GuardResponse
import io.devguard.models.LicenseStatus
import java.util.WeakHashMap

/**
 * Full DevGuard shield — lock, pending, warning banner, diagnostic FAB (Flutter/RN parity).
 */
internal object DevGuardShield {
    private val shields = WeakHashMap<Activity, ShieldHost>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(activity: Activity) {
        if (shields.containsKey(activity)) return
        val host = ShieldHost(activity)
        activity.window.addContentView(
            host.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        host.root.elevation = 100f
        host.root.translationZ = 100f
        shields[activity] = host
        render(activity, DevGuard.currentResponse.value)
    }

    fun detach(activity: Activity) {
        shields.remove(activity)?.root?.let { v ->
            (v.parent as? ViewGroup)?.removeView(v)
        }
    }

    fun refreshAll() {
        val run = Runnable {
            shields.keys.toList().forEach { activity ->
                if (!activity.isFinishing) render(activity, DevGuard.currentResponse.value)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else mainHandler.post(run)
    }

    private fun render(activity: Activity, response: GuardResponse?) {
        val host = shields[activity] ?: return
        val status = response?.status ?: LicenseStatus.PENDING
        host.clearLayers()

        when (status) {
            LicenseStatus.LOCKED, LicenseStatus.EXPIRED -> {
                host.root.visibility = View.VISIBLE
                host.root.isClickable = true
                host.root.isFocusable = true
                host.showFullScreen(
                    PremiumLockView(activity, response ?: GuardResponse(status = status), status).build(),
                )
            }
            LicenseStatus.PENDING -> {
                host.root.visibility = View.VISIBLE
                host.root.isClickable = true
                host.root.isFocusable = true
                host.showFullScreen(PendingViewFactory.create(activity, response?.message ?: ""))
            }
            LicenseStatus.WARNING -> {
                host.root.visibility = View.VISIBLE
                host.root.isClickable = false
                host.root.isFocusable = false
                host.showBanner(
                    WarningBannerFactory.create(activity, response?.title, response?.message ?: ""),
                )
            }
            else -> {
                host.root.visibility = View.GONE
                host.root.isClickable = false
                host.root.isFocusable = false
            }
        }

        if (response?.betaFeatures?.get("showDiagnosticLogs") == true && status != LicenseStatus.LOCKED && status != LicenseStatus.EXPIRED) {
            host.showDiagnosticFab {
                host.showFullScreen(buildDiagnosticPanel(activity, response))
            }
        }
    }

    private fun buildDiagnosticPanel(activity: Activity, response: GuardResponse): View {
        val pad = dp(activity, 24)
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(pad, pad, pad, pad)
        }
        panel.addView(TextView(activity).apply {
            text = "Diagnostic Logs"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(TextView(activity).apply {
            text = "Status: ${response.status}\nPing: ${response.pingInterval}m\nSync: ${response.syncPolicy}"
            setTextColor(Color.argb(200, 255, 255, 255))
            setPadding(0, dp(activity, 16), 0, dp(activity, 16))
        })
        panel.addView(TextView(activity).apply {
            text = "Close"
            setTextColor(Color.parseColor("#3B82F6"))
            setPadding(0, dp(activity, 8), 0, 0)
            setOnClickListener { refreshAll() }
        })
        return ScrollView(activity).apply {
            addView(panel)
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
    }

    private fun dp(activity: Activity, v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        v.toFloat(),
        activity.resources.displayMetrics,
    ).toInt()

    private class ShieldHost(private val activity: Activity) {
        val root = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            visibility = View.GONE
            isClickable = false
        }

        private val fullScreen = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        private val bannerSlot = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        init {
            root.addView(fullScreen)
            root.addView(bannerSlot)
        }

        fun clearLayers() {
            fullScreen.removeAllViews()
            fullScreen.visibility = View.GONE
            bannerSlot.removeAllViews()
            bannerSlot.visibility = View.GONE
            root.findViewWithTag<View>("dg_diag_fab")?.let { root.removeView(it) }
        }

        fun showFullScreen(view: View) {
            fullScreen.visibility = View.VISIBLE
            fullScreen.removeAllViews()
            fullScreen.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        fun showBanner(view: View) {
            bannerSlot.visibility = View.VISIBLE
            bannerSlot.removeAllViews()
            bannerSlot.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                ),
            )
        }

        fun showDiagnosticFab(onClick: () -> Unit) {
            val fab = TextView(activity).apply {
                tag = "dg_diag_fab"
                text = "🐞"
                textSize = 22f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(204, 255, 193, 7))
                }
                layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    bottomMargin = dp(24)
                    marginEnd = dp(24)
                }
                setOnClickListener { onClick() }
            }
            root.addView(fab)
        }

        private fun dp(v: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            activity.resources.displayMetrics,
        ).toInt()
    }
}
