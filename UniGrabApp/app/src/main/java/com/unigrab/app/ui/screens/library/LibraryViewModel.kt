package com.unigrab.app.ui.screens.library

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.unigrab.app.data.local.entity.LibraryItem
import com.unigrab.app.data.repository.UniGrabRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(
    private val repository: UniGrabRepository
) : ViewModel() {

    val libraryItems: StateFlow<List<LibraryItem>> = repository.allLibraryItemsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun playMedia(context: Context, item: LibraryItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File no longer exists on disk", Toast.LENGTH_SHORT).show()
            // Cleanup database record if file missing
            viewModelScope.launch {
                repository.deleteLibraryItem(item)
            }
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "com.unigrab.app.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Play with"))
        } catch (e: Exception) {
            Toast.makeText(context, "No compatible player found: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun deleteItem(item: LibraryItem) {
        viewModelScope.launch {
            // Delete file from disk
            val file = File(item.filePath)
            if (file.exists()) {
                file.delete()
            }
            // Delete DB record
            repository.deleteLibraryItem(item)
        }
    }

    class Factory(private val repository: UniGrabRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(repository) as T
        }
    }
}
