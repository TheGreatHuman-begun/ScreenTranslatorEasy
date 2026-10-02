package com.example.screentranslator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private lateinit var panel: LinearLayout
    private lateinit var resultContainer: LinearLayout
    private lateinit var status: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var projection: MediaProjection
    private lateinit var reader: ImageReader
    private var vDisplay: VirtualDisplay? = null
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var recognizer: TextRecognizer
    private lateinit var translator: TranslationEngine
    private var sw = 0
    private var sh = 0
    private var busy = false
    private var modelReady = false
    private var auto = false
    private val cache = HashMap<String, String>()

    private val autoTick = object : Runnable {
        override fun run() {
            if (auto) {
                translateOnce()
                ui.postDelayed(this, 2800)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            1,
            buildNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, ui)

        val dm = resources.displayMetrics
        sw = dm.widthPixels
        sh = dm.heightPixels
        reader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        vDisplay = projection.createVirtualDisplay(
            "ScreenTranslator", sw, sh, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        translator = MlKitTranslationEngine(this)
        addBubble()
        addPanel()
        setStatus("Preparing offline translation model…")
        translator.prepare(
            { modelReady = true; setStatus("Ready • tap Translate") },
            { setStatus("Translation model could not be prepared") }
        )
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channel = "screen_translator"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(channel, "Screen Translator", NotificationManager.IMPORTANCE_LOW)
            )
        return Notification.Builder(this, channel)
            .setContentTitle("Screen Translator is active")
            .setContentText("Use the side panel to translate your screen")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun panelParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(350), WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.END
        x = 0
        y = 0
    }

    private fun addPanel() {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.argb(245, 22, 24, 30))
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f, dp(20).toFloat(), dp(20).toFloat())
            }
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "Screen Translator"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        val close = TextView(this).apply {
            text = "×"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(dp(16), 0, 0, 0)
            setOnClickListener { hidePanel() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(45), dp(52)))
        panel.addView(header)

        val language = TextView(this).apply {
            text = "AUTO DETECT  →  PERSIAN"
            textSize = 12f
            setTextColor(Color.rgb(160, 190, 255))
        }
        panel.addView(language, LinearLayout.LayoutParams(-1, dp(28)))

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(8))
        }
        panel.addView(status)

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val translate = Button(this).apply {
            text = "Translate screen"
            setOnClickListener { translateOnce() }
        }
        val clear = Button(this).apply {
            text = "Clear"
            setOnClickListener { resultContainer.removeAllViews(); setStatus("Cleared") }
        }
        actions.addView(translate, LinearLayout.LayoutParams(0, dp(50), 1f))
        actions.addView(clear, LinearLayout.LayoutParams(dp(90), dp(50)))
        panel.addView(actions)

        autoSwitch = Switch(this).apply {
            text = "Automatic translation"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                auto = checked
                if (checked) {
                    setStatus("Auto mode • scanning every few seconds")
                    ui.removeCallbacks(autoTick)
                    ui.post(autoTick)
                } else {
                    ui.removeCallbacks(autoTick)
                    setStatus("Auto mode off")
                }
            }
        }
        panel.addView(autoSwitch, LinearLayout.LayoutParams(-1, dp(56)))

        val hint = TextView(this).apply {
            text = "Tip: the panel stays on the side so it doesn't cover the app you're translating."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dp(12))
        }
        panel.addView(hint)

        val scroll = ScrollView(this)
        resultContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(resultContainer)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        wm.addView(panel, panelParams())
    }

    private fun addBubble() {
        bubble = TextView(this).apply {
            text = "文"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(63, 81, 181))
            }
            setOnClickListener { if (panel.visibility == View.VISIBLE) hidePanel() else showPanel() }
        }
        val lp = WindowManager.LayoutParams(
            dp(54), dp(54), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = sw - dp(54)
            y = sh / 3
        }
        wm.addView(bubble, lp)
    }

    private fun hidePanel() { panel.visibility = View.GONE }
    private fun showPanel() { panel.visibility = View.VISIBLE }

    private fun setStatus(s: String) { if (::status.isInitialized) ui.post { status.text = s } }

    private fun grab(): Bitmap? {
        val image = reader.acquireLatestImage() ?: return null
        try {
            val p = image.planes[0]
            val rowPad = p.rowStride - p.pixelStride * sw
            val bmp = Bitmap.createBitmap(sw + rowPad / p.pixelStride, sh, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(p.buffer)
            return Bitmap.createBitmap(bmp, 0, 0, sw, sh)
        } finally { image.close() }
    }

    private fun translateOnce() {
        if (busy || !modelReady) return
        busy = true
        setStatus("Capturing screen…")
        val oldPanel = panel.visibility
        panel.visibility = View.GONE
        bubble.visibility = View.GONE
        ui.postDelayed({
            val bmp = grab()
            bubble.visibility = View.VISIBLE
            panel.visibility = oldPanel
            if (bmp == null) { busy = false; setStatus("Could not capture the screen"); return@postDelayed }
            setStatus("Reading text…")
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks.flatMap { it.lines }.filter { it.text.isNotBlank() }
                    resultContainer.removeAllViews()
                    if (lines.isEmpty()) {
                        setStatus("No text detected")
                        busy = false
                        return@addOnSuccessListener
                    }
                    setStatus("Translating ${lines.size} text regions…")
                    var remaining = lines.size
                    for (line in lines) {
                        val original = line.text.trim()
                        val cached = cache[original]
                        if (cached != null) addResult(original, cached)
                        else translator.translate(original, { translated ->
                            cache[original] = translated
                            addResult(original, translated)
                            if (--remaining == 0) finishBatch()
                        }, {
                            if (--remaining == 0) finishBatch()
                        })
                        if (cached != null && --remaining == 0) finishBatch()
                    }
                    if (remaining == 0) finishBatch()
                }
                .addOnFailureListener {
                    setStatus("OCR failed: ${it.message ?: "unknown error"}")
                    busy = false
                }
        }, 180)
    }

    private fun addResult(original: String, translated: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(34, 37, 46))
                cornerRadius = dp(12).toFloat()
            }
        }
        val src = TextView(this).apply { text = original; textSize = 14f; setTextColor(Color.LTGRAY) }
        val dst = TextView(this).apply { text = translated; textSize = 17f; setTextColor(Color.WHITE) }
        card.addView(src)
        card.addView(dst)
        resultContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })
    }

    private fun finishBatch() {
        busy = false
        setStatus("Translation complete • ${resultContainer.childCount} results")
    }

    override fun onDestroy() {
        auto = false
        ui.removeCallbacksAndMessages(null)
        runCatching { if (::panel.isInitialized) wm.removeView(panel) }
        runCatching { if (::bubble.isInitialized) wm.removeView(bubble) }
        vDisplay?.release()
        runCatching { reader.close() }
        runCatching { projection.stop() }
        runCatching { recognizer.close() }
        runCatching { translator.close() }
        super.onDestroy()
    }
}
