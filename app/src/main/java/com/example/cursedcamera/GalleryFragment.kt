// GalleryFragment.kt (фрагмент-галерея)

package com.example.cursedcamera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cursedcamera.databinding.FragmentGalleryBinding
import java.io.File

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var galleryAdapter: GalleryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Загружаем файлы из папки
        val filesList = getMediaFiles().toMutableList()

        // Инициализируем адаптер
        galleryAdapter = GalleryAdapter(filesList) { deletedFile ->
            // Коллбек удаления: можно обновить UI или выполнить другие действия
        }
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = galleryAdapter
    }

    private fun getMediaFiles(): List<File> {
        val mediaDir = requireContext().externalMediaDirs.firstOrNull()?.let {
            File(it, "CursedCamera").apply { mkdirs() }
        }
        return mediaDir?.listFiles()?.toList() ?: emptyList()
    }
    // Здесь можно добавить код для воспроизведения видео
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}