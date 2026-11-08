package com.example.kesehatanbaterai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private val errors = mutableListOf<String>()

    private val bg = Color.parseColor("#101614")
    private val card = Color.parseColor("#1a2421")
    private val fg = Color.parseColor("#e6efec")
    private val muted = Color.parseColor("#93a8a3")
    private val accent = Color.parseColor("#5fd0b0")
    private val warn = Color.parseColor("#f0a070")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val tick = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    private fun <T> safe(name: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Throwable) {
            errors.add(name + ": " + e.javaClass.simpleName + " " + (e.message ?: ""))
            null
        }

    private fun render() {
        errors.clear()
        try {
            draw()
        } catch (e: Throwable) {
            content.removeAllViews()
            content.addView(text("Terjadi error", 22f, warn, bold = true))
            content.addView(
                text(
                    e.toString() + "\n" + e.stackTrace.take(8).joinToString("\n"),
                    12f, muted
                )
            )
        }
    }

    private fun draw() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = safe("intent") {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }

        var soh: Int? = null
        var cycles: Int? = null
        var made: String? = null
        var firstUse: String? = null
        if (Build.VERSION.SDK_INT >= 34) {
            soh = safe("kondisi") {
                bm.getIntProperty(10)
            }?.takeIf { it in 1..100 }
            cycles = safe("siklus") {
                intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
            }?.takeIf { it > 0 }
            made = safe("tanggal pembuatan") {
                formatDate(bm.getLongProperty(8))
            }
            firstUse = safe("penggunaan pertama") {
                formatDate(bm.getLongProperty(9))
            }
        }

        val counter = safe("muatan") {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } ?: 0
        val percent = safe("persen") {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } ?: -1
        val estimateMah: Int? =
            if (counter > 0 && percent in 20..100) (counter / 1000.0 * 100.0 / percent).toInt() else null

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val volt = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "-"
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        content.removeAllViews()

        content.addView(text("Kesehatan Baterai", 26f, fg, bold = true))
        content.addView(spacer(16))

        val main = box()
        main.addView(text("Kondisi baterai", 14f, muted))
        main.addView(
            text(
                if (soh != null) "$soh%" else "Tidak tersedia",
                if (soh != null) 52f else 24f,
                accent,
                bold = true
            )
        )
        main.addView(
            text(
                if (soh != null) "Dibaca langsung dari sistem HP."
                else "HP atau versi Android ini tidak melaporkan kondisi baterai ke aplikasi. Lihat perkiraan di bawah.",
                13f, muted
            )
        )
        content.addView(main)
        content.addView(spacer(12))

        val info = box()
        info.addView(row("Siklus", cycles?.toString() ?: "Tidak dilaporkan"))
        info.addView(row("Tanggal pembuatan", made ?: "Tidak dilaporkan"))
        info.addView(row("Penggunaan pertama", firstUse ?: "Tidak dilaporkan"))
        info.addView(
            row(
                "Kapasitas penuh (perkiraan)",
                if (estimateMah != null) "$estimateMah mAh" else "Butuh isi 20% ke atas"
            )
        )
        content.addView(info)
        content.addView(spacer(12))

        val now = box()
        now.addView(text("Sekarang", 14f, muted))
        now.addView(row("Isi baterai", if (level >= 0) "$level%" else "-"))
        now.addView(row("Status", statusText(status, plugged)))
        now.addView(row("Kondisi (Android)", healthText(health)))
        now.addView(row("Suhu", String.format(Locale.US, "%.1f °C", temp)))
        now.addView(row("Tegangan", "$volt mV"))
        now.addView(row("Teknologi", tech))
        content.addView(now)
        content.addView(spacer(12))

        content.addView(
            text(
                "Perkiraan kapasitas dihitung dari penghitung muatan dan bisa meleset. Sistem di HP-mu tetap sumber yang paling akurat.",
                12f, muted
            )
        )

        if (errors.isNotEmpty()) {
            content.addView(spacer(12))
            content.addView(text("Catatan error:\n" + errors.joinToString("\n"), 12f, warn))
        }
    }

    private fun formatDate(seconds: Long): String? {
        if (seconds <= 0L || seconds == Long.MIN_VALUE) return null
        return SimpleDateFormat("MMM yyyy", Locale("id", "ID")).format(Date(seconds * 1000))
    }

    private fun statusText(s: Int, plugged: Int): String {
        val base = when (s) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Mengisi"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Tidak mengisi"
            BatteryManager.BATTERY_STATUS_FULL -> "Penuh"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Tidak mengisi"
            else -> "Tidak diketahui"
        }
        val src = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> " (adaptor)"
            BatteryManager.BATTERY_PLUGGED_USB -> " (USB)"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> " (nirkabel)"
            else -> ""
        }
        return base + src
    }

    private fun healthText(h: Int): String = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Baik"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Terlalu panas"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Rusak"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Tegangan berlebih"
        BatteryManager.BATTERY_HEALTH_COLD -> "Terlalu dingin"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Gangguan"
        else -> "Tidak diketahui"
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun spacer(h: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun box() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(card)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun row(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, dp(8))
        addView(TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(fg)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply {
            text = value
            textSize = 15f
            gravity = Gravity.END
            setTextColor(muted)
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }
}
