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

    fun getCatCompleted(): Int {
        return categories.count { it.isCompleted }
    }

    fun getUncategorizedTasksCompleted(): Int {
        return uncategorizedTasks.count { it.isCompleted }
    }

    fun computeProgress(): Int {
        if (categories.isEmpty() && uncategorizedTasks.isEmpty()) {
            return 0
        }
        val progress = ((getCatCompleted().toDouble() + getUncategorizedTasksCompleted().toDouble())
                / (categories.size + uncategorizedTasks.size)) * 100
        return progress.roundToInt()
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