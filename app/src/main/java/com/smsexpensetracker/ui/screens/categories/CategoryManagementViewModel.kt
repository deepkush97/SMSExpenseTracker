package com.smsexpensetracker.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsexpensetracker.domain.model.Category
import com.smsexpensetracker.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val repository: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, icon: String, color: Int) {
        viewModelScope.launch {
            repository.insert(Category(id = 0, name = name.trim(), icon = icon, color = color, isDefault = false))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.update(category)
        }
    }

    fun deleteCategory(category: Category) {
        if (category.isDefault) return
        viewModelScope.launch {
            repository.delete(category)
        }
    }
}
