package mx.lumenforge.photo

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val captureWorker = Executors.newSingleThreadExecutor()
    private val processingWorker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var cameraView: PreviewView
    private lateinit var photoView: ImageView
    private lateinit var cameraBar: LinearLayout
    private lateinit var editBar: LinearLayout
    private lateinit var status: TextView
    private lateinit var save: Button
    private lateinit var noise: SeekBar
    private lateinit var sharp: SeekBar
    private lateinit var saturation: SeekBar
    private lateinit var warmth: SeekBar
    private var provider: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null
    private var front = false
    private var editing = false
    private var saving = false
    private var original: Bitmap? = null
    private var previewSource: Bitmap? = null
    private var previewOutput: Bitmap? = null
    private var generation = 0
    private var pending: Runnable? = null

    private val askCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera() else message("Sin cámara: usa Galería")
    }
    private val pick = registerForActivityResult(
        ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) openPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.rgb(7, 16, 24)
        val panelColor = Color.rgb(16, 28, 43)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "   LUMENFORGE  /  COMPUTATIONAL CAMERA"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(panelColor)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(55)))
        val frame = FrameLayout(this)
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        cameraView = PreviewView(this)
        photoView = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(bg)
        }
        frame.addView(cameraView, FrameLayout.LayoutParams(-1, -1))
        frame.addView(photoView, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            text = "LumenForge listo"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xBB101C2B.toInt())
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        frame.addView(status, FrameLayout.LayoutParams(-2, -2,
            android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL))
        cameraBar = LinearLayout(this).apply {
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(panelColor)
        }
        root.addView(cameraBar, LinearLayout.LayoutParams(-1, -2))
        action(cameraBar, "Galería", 1f) { pick.launch("image/*") }
        action(cameraBar, "● FOTO", 1.3f) { takePhoto() }
        action(cameraBar, "Girar", 1f) {
            front = !front
            bindCamera()
        }
        editBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(panelColor)
            visibility = View.GONE
        }
        val scroll = ScrollView(this)
        scroll.addView(editBar)
        root.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        noise = slider("Ruido", 20)
        sharp = slider("Nitidez", 25)
        saturation = slider("Saturación", 50)
        warmth = slider("Calidez", 50)
        val buttons = LinearLayout(this)
        editBar.addView(buttons)
        val compare = action(buttons, "Original", 1f) { }
        @Suppress("ClickableViewAccessibility")
        compare.setOnTouchListener { _, event ->
            if (!editing) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    photoView.setImageBitmap(previewSource); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    photoView.setImageBitmap(previewOutput ?: previewSource); true
                }
                else -> true
            }
        }
        action(buttons, "Reset", 1f) {
            noise.progress = 20; sharp.progress = 25
            saturation.progress = 50; warmth.progress = 50
            scheduleProcessing()
        }
        save = action(buttons, "Guardar", 1f) { savePhoto() }
        action(editBar, "← Nueva foto / Volver a cámara", 1f) { showCamera() }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editing) showCamera() else finish()
            }
        })
        ensurePermission()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun action(container: LinearLayout, text: String, weight: Float,
        click: () -> Unit): Button {
        val b = Button(this).apply { this.text = text; setAllCaps(false); setOnClickListener { click() } }
        container.addView(b, if (container.orientation == LinearLayout.HORIZONTAL)
            LinearLayout.LayoutParams(0, -2, weight)
        else LinearLayout.LayoutParams(-1, -2))
        return b
    }
    private fun slider(label: String, value: Int): SeekBar {
        val heading = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
        }
        editBar.addView(heading)
        val bar = SeekBar(this).apply { max = 100; progress = value }
        editBar.addView(bar, LinearLayout.LayoutParams(-1, dp(37)))
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, v: Int, user: Boolean) {
                heading.text = label + ": " + v
                if (editing) scheduleProcessing()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        return bar
    }

    private fun ensurePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) bindCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
    }
    private fun bindCamera() {
        if (editing || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isDestroyed || editing) return@addListener
            try {
                val cp = future.get()
                provider = cp
                val preview = Preview.Builder().build()
                preview.surfaceProvider = cameraView.surfaceProvider
                val shot = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                cp.unbindAll()
                cp.bindToLifecycle(this,
                    CameraSelector.Builder().requireLensFacing(if (front)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK).build(),
                    preview, shot)
                capture = shot
                message("Cámara lista")
            } catch (e: Exception) {
                capture = null
                message("No se pudo abrir cámara: " + e.message)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun filename(prefix: String) = prefix + System.currentTimeMillis() + ".jpg"
    private fun takePhoto() {
        val shot = capture ?: return message("Cámara no disponible")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename("LF_ORIGINAL_"))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_DCIM + "/LumenForge/Originales")
        }
        val options = ImageCapture.OutputFileOptions.Builder(contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        message("Capturando...")
        shot.takePicture(options, captureWorker, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                runOnUiThread {
                    val uri = output.savedUri
                    if (uri != null) openPhoto(uri) else message("Foto guardada en DCIM")
                }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { message("Error de cámara: " + exception.message) }
            }
        })
    }

    private fun openPhoto(uri: Uri) {
        val gen = ++generation
        message("Cargando foto...")
        processingWorker.execute {
            try {
                val source = ImageDecoder.createSource(contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val max = maxOf(info.size.width, info.size.height)
                    if (max > 3072) {
                        val scale = 3072.0 / max
                        decoder.setTargetSize(
                            maxOf(1, (info.size.width * scale).toInt()),
                            maxOf(1, (info.size.height * scale).toInt()))
                    }
                }
                val edge = maxOf(bitmap.width, bitmap.height)
                val thumb = if (edge > 1280) {
                    val scale = 1280.0 / edge
                    Bitmap.createScaledBitmap(bitmap,
                        maxOf(1, (bitmap.width * scale).toInt()),
                        maxOf(1, (bitmap.height * scale).toInt()), true)
                } else bitmap
                runOnUiThread {
                    if (gen != generation || isDestroyed) return@runOnUiThread
                    original = bitmap
                    previewSource = thumb
                    previewOutput = thumb
                    editing = true
                    provider?.unbindAll()
                    capture = null
                    cameraView.visibility = View.GONE
                    photoView.visibility = View.VISIBLE
                    photoView.setImageBitmap(thumb)
                    cameraBar.visibility = View.GONE
                    editBar.visibility = View.VISIBLE
                    message("Editando: máximo 3072 px")
                    scheduleProcessing()
                }
            } catch (e: Exception) {
                runOnUiThread { message("Error leyendo foto: " + e.message) }
            }
        }
    }
    private fun params() = ProcessingParams(
        noise.progress, sharp.progress, saturation.progress, warmth.progress)
    private fun scheduleProcessing() {
        if (!editing) return
        val gen = ++generation
        pending?.let { mainHandler.removeCallbacks(it) }
        val source = previewSource ?: return
        val p = params()
        val r = Runnable {
            processingWorker.execute {
                try {
                    val enhanced = PhotoProcessor.apply(source, p)
                    runOnUiThread {
                        if (editing && gen == generation && !isDestroyed) {
                            previewOutput = enhanced
                            photoView.setImageBitmap(enhanced)
                            message("Ajustes aplicados")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { message("Error al procesar: " + e.message) }
                }
            }
        }
        pending = r
        mainHandler.postDelayed(r, 180)
    }
    private fun showCamera() {
        generation++
        pending?.let { mainHandler.removeCallbacks(it) }
        editing = false
        original = null
        previewSource = null
        previewOutput = null
        photoView.setImageDrawable(null)
        photoView.visibility = View.GONE
        cameraView.visibility = View.VISIBLE
        cameraBar.visibility = View.VISIBLE
        editBar.visibility = View.GONE
        ensurePermission()
    }
    private fun savePhoto() {
        if (saving) return
        val bitmap = original ?: return
        val p = params()
        saving = true
        save.isEnabled = false
        message("Guardando...")
        processingWorker.execute {
            try {
                val output = PhotoProcessor.apply(bitmap, p)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename("LF_EDITADA_"))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/LumenForge/Procesadas")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo crear archivo")
                try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        if (!output.compress(Bitmap.CompressFormat.JPEG, 96, stream))
                            error("JPEG falló")
                    } ?: error("No se pudo escribir archivo")
                    contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }, null, null)
                    runOnUiThread { message("Foto guardada en Pictures/LumenForge") }
                } catch (e: Exception) {
                    contentResolver.delete(uri, null, null)
                    throw e
                }
            } catch (e: Exception) {
                runOnUiThread { message("Error guardando: " + e.message) }
            } finally {
                runOnUiThread { saving = false; save.isEnabled = true }
            }
        }
    }
    private fun message(s: String) { status.text = s }
    override fun onDestroy() {
        pending?.let { mainHandler.removeCallbacks(it) }
        super.onDestroy()
        captureWorker.shutdown()
        processingWorker.shutdown()
    }
}

object PhotoProcessor {
    fun apply(bitmap: Bitmap, p: ProcessingParams): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val out = PhotoAlgorithms.process(pix, w, h, p)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, w, 0, 0, w, h)
        }
    }
}
