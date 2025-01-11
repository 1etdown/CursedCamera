// GalleryAdapter.kt

package com.example.cursedcamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cursedcamera.databinding.ItemGalleryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryAdapter(
    private val mediaFiles: MutableList<File>, // Изменяемый список для удаления
    private val onFileDeleted: (File) -> Unit  // Коллбек при удалении файла
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    // Сет для хранения выбранных файлов
    private val selectedFiles = mutableSetOf<File>()

    class GalleryViewHolder(val binding: ItemGalleryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemGalleryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val file = mediaFiles[position]

        // Определяем тип контента по расширению файла
        val extension = file.extension.lowercase(Locale.getDefault())
        val contentType = when {
            extension in listOf("jpg", "jpeg", "png") -> "Фото"
            extension in listOf("mp4", "mov", "avi", "mkv") -> "Видео"
            else -> "Неизвестно"
        }

        // Получаем дату последнего изменения файла как дату создания
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(file.lastModified()))

        // Загружаем изображение или превью видео через Glide
        Glide.with(holder.itemView.context)
            .load(file)
            .into(holder.binding.imageView)

        // Отображаем тип контента и дату
        holder.binding.contentInfoText.text = "$contentType • $formattedDate"

        // Обновляем состояние выделения
        if (selectedFiles.contains(file)) {
            holder.binding.selectionOverlay.visibility = View.VISIBLE
            holder.binding.deleteButton.visibility = View.VISIBLE
        } else {
            holder.binding.selectionOverlay.visibility = View.GONE
            holder.binding.deleteButton.visibility = View.GONE
        }

        // Обработчик нажатия для просмотра файла
        holder.itemView.setOnClickListener {
            if (selectedFiles.isNotEmpty()) {
                toggleSelection(file)
            } else {
                openFile(holder, file, contentType)
            }
        }

        // Обработчик долгого нажатия для выделения файла
        holder.itemView.setOnLongClickListener {
            toggleSelection(file)
            true
        }

        // Обработчик нажатия на кнопку удаления
        holder.binding.deleteButton.setOnClickListener {
            deleteFile(holder, file)
        }
    }

    override fun getItemCount(): Int = mediaFiles.size

    /**
     * Переключает состояние выбора файла.
     */
    private fun toggleSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        notifyDataSetChanged()
    }

    /**
     * Удаляет файл из файловой системы и списка.
     */
    private fun deleteFile(holder: GalleryViewHolder, file: File) {
        val context = holder.itemView.context

        if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                Toast.makeText(
                    context,
                    "Файл удалён: ${file.name}",
                    Toast.LENGTH_SHORT
                ).show()
                // Убираем файл из списка и уведомляем адаптер
                mediaFiles.remove(file)
                selectedFiles.remove(file)
                notifyDataSetChanged()
                // Сообщаем наружу, что файл удалён
                onFileDeleted(file)
            } else {
                Toast.makeText(
                    context,
                    "Не удалось удалить файл: ${file.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Открывает файл во внешнем просмотрщике.
     */
    private fun openFile(holder: GalleryViewHolder, file: File, contentType: String) {
        val context = holder.itemView.context

        // Передаём путь к файлу полноэкранному фрагменту через Navigation
        val navController = (holder.itemView.context as MainActivity)
            .findNavController(R.id.nav_host_fragment)

        // Сформируем Bundle с путем к файлу
        val bundle = Bundle().apply {
            putString("filePath", file.absolutePath)
        }

        // Открываем FullScreenMediaFragment
        navController.navigate(R.id.action_galleryFragment_to_fullScreenMediaFragment, bundle)
    }


}