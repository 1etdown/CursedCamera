package com.example.cursedcamera

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.cursedcamera.databinding.FragmentFullScreenMediaBinding
import java.io.File
import java.util.Locale

class FullScreenMediaFragment : Fragment() {

    private var _binding: FragmentFullScreenMediaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFullScreenMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * В этом методе мы получаем путь к выбранному файлу и решаем,
     * как его отображать — как фото или видео.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем путь к файлу, переданный через аргументы
        val filePath = arguments?.getString("filePath") ?: return
        val file = File(filePath)

        // Устанавливаем заголовок (необязательно)
        requireActivity().title = file.name

        // Проверяем расширение файла
        val extension = file.extension.lowercase(Locale.getDefault())

        // Скрываем оба вида отображения, чтобы показать нужный
        binding.imageViewFullScreen.visibility = View.GONE
        binding.videoViewFullScreen.visibility = View.GONE

        if (extension in listOf("jpg", "jpeg", "png")) {
            // Показать фото
            binding.imageViewFullScreen.visibility = View.VISIBLE
            Glide.with(this)
                .load(file)
                .into(binding.imageViewFullScreen)

        } else if (extension in listOf("mp4", "mov", "avi", "mkv")) {
            // Показать видео
            binding.videoViewFullScreen.visibility = View.VISIBLE

            val fileUri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            // MediaController для удобного управления воспроизведением
            val mediaController = MediaController(requireContext())
            mediaController.setAnchorView(binding.videoViewFullScreen)
            binding.videoViewFullScreen.setMediaController(mediaController)

            // Устанавливаем видео URI и стартуем воспроизведение
            binding.videoViewFullScreen.setVideoURI(fileUri)
            binding.videoViewFullScreen.start()

        } else {
            // Если формат неизвестен — выводим сообщение или обрабатываем иначе
            // Здесь можно добавить логику под ваши нужды
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}