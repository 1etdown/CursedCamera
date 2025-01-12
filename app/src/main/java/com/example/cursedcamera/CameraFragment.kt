package com.example.cursedcamera

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.cursedcamera.databinding.FragmentCameraBinding
import com.example.cursedcamera.utils.PermissionsUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageCapture: ImageCapture? = null
    private var recording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var mediaDir: File? = null

    // Zoom variables
    private var currentZoomRatio = 1f
    private var maxZoomRatio = 1f

    // Gesture detector for pinch-to-zoom
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // Flag for photo/video mode
    private var isPhotoMode = true

    // Handler and Runnable for video timer
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var elapsedSeconds = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize media directory
        val mediaDirs = ContextCompat.getExternalFilesDirs(requireContext(), null)
        mediaDir = mediaDirs.firstOrNull()?.let { File(it, "CursedCamera").apply { mkdirs() } }

        // Check permissions
        if (PermissionsUtils.hasCameraPermissions(requireContext())) {
            startCamera()
        } else {
            PermissionsUtils.requestCameraPermissions(this)
        }

        // Set up mode toggle button
        binding.buttonMode.setOnClickListener {
            if (isPhotoMode) {
                // In photo mode, take photo
                takePhoto()
            } else {
                // In video mode, start or stop recording
                toggleRecording()
            }
        }

        // Long press to switch mode
        binding.buttonMode.setOnLongClickListener {
            isPhotoMode = !isPhotoMode
            updateModeUI()
            true
        }

        // Switch camera button
        binding.buttonSwitchCamera.setOnClickListener {
            if (recording != null) {
                Toast.makeText(requireContext(), "Невозможно поменять режим камеры сейчас=(", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        // Gallery button
        binding.buttonGallery.setOnClickListener {
            findNavController().navigate(R.id.action_cameraFragment_to_galleryFragment)
        }

        // Initialize SeekBar for zoom
        binding.zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Calculate zoom ratio based on SeekBar progress
                if (maxZoomRatio > 1f) {
                    val ratio = 1f + (maxZoomRatio - 1f) * (progress / 100f)
                    camera?.cameraControl?.setZoomRatio(ratio)
                    currentZoomRatio = ratio
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Optional: Handle when user starts interacting with SeekBar
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Optional: Handle when user stops interacting with SeekBar
            }
        })

        // Initialize gesture detector for pinch-to-zoom
        scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                val newZoom = currentZoomRatio * scale
                val zoom = max(1f, min(newZoom, maxZoomRatio))
                camera?.cameraControl?.setZoomRatio(zoom)
                currentZoomRatio = zoom
                // Update SeekBar position based on new zoom
                val progress = ((zoom - 1f) / (maxZoomRatio - 1f) * 100).toInt()
                binding.zoomSeekBar.progress = progress
                return true
            }
        })

        // Set touch listener for pinch gestures
        binding.viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        // Set initial UI state
        updateModeUI()
    }

    /**
     * Обновляет UI в зависимости от текущего режима (фото/видео)
     */
    private fun updateModeUI() {
        if (isPhotoMode) {
            binding.buttonMode.setImageResource(android.R.drawable.ic_menu_camera) // Иконка камеры
            binding.buttonMode.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_photo_button)
        } else {
            binding.buttonMode.setImageResource(android.R.drawable.presence_offline) // Иконка видео
            binding.buttonMode.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_record_button)
        }
    }

    private var camera: Camera? = null

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // Recorder for video
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
            videoCapture = VideoCapture.withOutput(recorder)

            // ImageCapture for photos
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture,
                    imageCapture
                )

                // After binding, get the camera's maximum zoom ratio
                camera?.cameraInfo?.zoomState?.observe(viewLifecycleOwner) { zoomState ->
                    maxZoomRatio = zoomState.maxZoomRatio
                    binding.zoomSeekBar.max = 100
                    binding.zoomSeekBar.progress = ((currentZoomRatio - 1f) / (maxZoomRatio - 1f) * 100).toInt()
                }

            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = createFile(requireContext(), ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Показать анимацию вспышки перед съемкой
        showFlashAnimation()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(requireContext(), "Error saving photo", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                }
            }
        )
    }

    private fun toggleRecording() {
        val videoCapture = this.videoCapture ?: return
        val recording = this.recording

        if (recording != null) {
            // Stop recording
            recording.stop()
            this.recording = null
        } else {
            // Start recording
            // Hide gallery button
            binding.buttonGallery.visibility = View.GONE

            val videoFile = createFile(requireContext(), ".mp4")
            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            this.recording = videoCapture.output
                .prepareRecording(requireContext(), outputOptions)
                .apply {
                    if (PermissionsUtils.hasAudioPermission(requireContext()))
                        withAudioEnabled()
                }
                .start(ContextCompat.getMainExecutor(requireContext())) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            // Update UI on start
                            isPhotoMode = false
                            updateModeUI()


                            // Запустить таймер
                            startVideoTimer()
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (event.hasError()) {
                                Log.e("CameraFragment", "Video capture failed: ${event.error}")
                                Toast.makeText(requireContext(), "Error recording video", Toast.LENGTH_SHORT).show()
                            }
                            // Show gallery button
                            binding.buttonGallery.visibility = View.VISIBLE
                            // Update UI after recording
                            isPhotoMode = true
                            updateModeUI()

                            // Остановить таймер
                            stopVideoTimer()
                        }
                    }
                }
        }
    }

    private fun createFile(context: Context, extension: String = ".jpg"): File {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, "CursedCamera").apply { mkdirs() }
        }
        val outputDir = if (mediaDir != null && mediaDir.exists()) mediaDir else context.filesDir
        return File(
            outputDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + extension
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Остановить таймер перед установкой binding в null
        stopVideoTimer()
        _binding = null
        cameraExecutor.shutdown()
    }

    /**
     * Показывает анимацию вспышки экрана
     */
    private fun showFlashAnimation() {
        binding.flashView.apply {
            visibility = View.VISIBLE
            alpha = 1f

            animate()
                .alpha(0f)
                .setDuration(100) // Длительность анимации в миллисекундах
                .withEndAction { visibility = View.GONE }
        }
    }

    /**
     * Запускает таймер записи видео
     */
    private fun startVideoTimer() {
        elapsedSeconds = 0
        binding.videoTimer.text = "00:00"
        binding.videoTimer.visibility = View.VISIBLE

        runnable = object : Runnable {
            override fun run() {
                elapsedSeconds++
                binding.videoTimer.text = formatTime(elapsedSeconds)
                handler.postDelayed(this, 1000)
            }
        }

        handler.postDelayed(runnable!!, 1000)
    }

    /**
     * Останавливает таймер записи видео
     */
    private fun stopVideoTimer() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        _binding?.videoTimer?.visibility = View.GONE // Используем безопасный вызов
    }

    /**
     * Форматирует время в формате MM:SS
     */
    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
    }
}