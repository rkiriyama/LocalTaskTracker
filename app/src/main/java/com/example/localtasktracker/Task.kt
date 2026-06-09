package com.example.localtasktracker

import kotlin.math.roundToInt

data class Task(
    val id: Int,
    var title: String,
    var isCompleted: Boolean = false,
    var completionProgress: Int = 0,
    val categories: MutableList<TaskCategory> = mutableListOf(),
    val uncategorizedTasks: MutableList<SubTask> = mutableListOf()
) {
    fun renameTask(newTaskName: String): Boolean {
        val trimmedName = newTaskName.trim()

        if (trimmedName.isEmpty()) {
            return false
        }
        title = trimmedName
        return true
    }

    fun changeTaskStatus(status: Boolean): Boolean {
        val currentProgress = computeProgress()
        if ((status && (currentProgress  != 100)) || (!status && (currentProgress  == 100))) {
            return false
        }
        isCompleted = status
        return true
    }

    fun getUncategorizedTasksCompleted(): Int {
        return uncategorizedTasks.count { it.isCompleted }
    }

    fun computeProgress(): Int {
        // Collect progress values from all sources:
        // - each category contributes its own computeProgress() (0‥100)
        // - each uncategorized subtask contributes 0 or 100
        val values = mutableListOf<Int>()
        categories.forEach { values.add(it.computeProgress()) }
        uncategorizedTasks.forEach { values.add(if (it.isCompleted) 100 else 0) }
        if (values.isEmpty()) return 0
        return values.average().roundToInt()
    }

    fun addCategory(newCategory: TaskCategory): Boolean {
        if (newCategory.categoryName.trim().isEmpty()) {
            return false
        }
        categories.add(newCategory)
        return true
    }

    fun addUncategorizedSubTask(newSubtask: SubTask): Boolean {
        if (newSubtask.subTaskName.trim().isEmpty()) {
            return false
        }
        uncategorizedTasks.add(newSubtask)
        return true
    }

    fun deleteCategory(categoryID: Int): Boolean {
        if (categories.isEmpty()) {
            return false
        }
        return categories.removeAll { it.id == categoryID }
    }

    fun deleteUncategorizedSubTask(subTaskID: Int): Boolean {
        if (uncategorizedTasks.isEmpty()) {
            return false
        }
        return uncategorizedTasks.removeAll { it.id == subTaskID }
    }
}