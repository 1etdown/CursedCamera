package com.example.cursedcamera

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.cursedcamera.databinding.FragmentCameraBinding
import com.example.cursedcamera.utils.PermissionsUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.navigation.fragment.findNavController
class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageCapture: ImageCapture? = null
    private var recording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var mediaDir: File? = null

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

        // Правильная инициализация папки здесь, где можно вызывать requireContext()
        val mediaDirs = ContextCompat.getExternalFilesDirs(requireContext(), null)
        mediaDir = mediaDirs.firstOrNull()?.let { File(it, "CursedCamera").apply { mkdirs() } }

        if (PermissionsUtils.hasCameraPermissions(requireContext())) {
            startCamera()
        } else {
            PermissionsUtils.requestCameraPermissions(this)
        }

        binding.buttonCapture.setOnClickListener { takePhoto() }
        binding.buttonRecord.setOnClickListener { toggleRecording() }
        binding.buttonSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        binding.buttonGallery.setOnClickListener {
            findNavController().navigate(R.id.action_cameraFragment_to_galleryFragment)
        }
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val recorder = Recorder.Builder().setExecutor(cameraExecutor).build()
            videoCapture = VideoCapture.withOutput(recorder)
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = createFile(requireContext(), ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(requireContext(), "Photo saved: ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun toggleRecording() {
        val videoCapture = this.videoCapture ?: return
        val recording = this.recording

        if (recording != null) {
            recording.stop()
            this.recording = null
            binding.buttonRecord.text = "Start Recording"
            Toast.makeText(requireContext(), "Recording stopped", Toast.LENGTH_SHORT).show()
        } else {
            val videoFile = createFile(requireContext(), ".mp4")
            val outputOptions = FileOutputOptions.Builder(videoFile).build()
            this.recording = videoCapture.output
                .prepareRecording(requireContext(), outputOptions)
                .apply { if (PermissionsUtils.hasAudioPermission(requireContext())) withAudioEnabled() }
                .start(ContextCompat.getMainExecutor(requireContext())) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            binding.buttonRecord.text = "Stop Recording"
                            Toast.makeText(requireContext(), "Recording started", Toast.LENGTH_SHORT).show()
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (event.hasError()) {
                                Log.e("CameraFragment", "Video capture failed: ${event.error}")
                            } else {
                                Toast.makeText(requireContext(), "Video saved: ${videoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                            }
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
        _binding = null
        cameraExecutor.shutdown()
    }
}
