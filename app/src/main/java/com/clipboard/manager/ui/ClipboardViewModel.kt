package com.clipboard.manager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.clipboard.manager.database.ClipboardDatabase
import com.clipboard.manager.database.ClipboardEntry
import com.clipboard.manager.database.ClipboardRepository
import kotlinx.coroutines.launch

class ClipboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClipboardRepository
    val allEntries: LiveData<List<ClipboardEntry>>

    private val _searchQuery = MutableLiveData<String>("")
    val searchResults: LiveData<List<ClipboardEntry>>

    init {
        val database = ClipboardDatabase.getDatabase(application)
        repository = ClipboardRepository(database.clipboardDao())
        allEntries = repository.allEntries
        searchResults = _searchQuery.switchMap { query ->
            if (query.isNullOrEmpty()) {
                allEntries
            } else {
                repository.searchEntries(query)
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun insert(entry: ClipboardEntry) = viewModelScope.launch {
        repository.insert(entry)
    }

    fun updateFavorite(entry: ClipboardEntry) = viewModelScope.launch {
        repository.updateFavorite(entry.id, !entry.isFavorite)
    }

    fun updateNote(entry: ClipboardEntry, note: String) = viewModelScope.launch {
        repository.updateNote(entry.id, note)
    }

    fun delete(entry: ClipboardEntry) = viewModelScope.launch {
        repository.delete(entry)
    }

    fun deleteAll() = viewModelScope.launch {
        repository.deleteAll()
    }
}
