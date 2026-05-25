package com.example.localtasktracker
import kotlin.math.roundToInt

data class TaskCategory(
    val id: Int,
    var categoryName: String,
    var isCompleted: Boolean = false,
    var completionProgress: Int = 0,
    val subTasks: MutableList<SubTask> = mutableListOf()
) {
    fun renameCategory(newCatName: String): Boolean {
        val trimmedName = newCatName.trim()

        if (trimmedName.isEmpty()) {
            return false
        }
        categoryName = trimmedName
        return true
    }

    fun changeCategoryStatus(status: Boolean): Boolean {
        val currentProgress = computeProgress()

        if ((status && (currentProgress  != 100)) || (!status && (currentProgress == 100))) {
            return false
        }
        isCompleted = status
        return true
    }

    fun getTasksCompleted(): Int {
        return subTasks.count { it.isCompleted }
    }

    fun computeProgress(): Int {
        if (subTasks.isEmpty()) {
            return 0
        }
        val progress = (getTasksCompleted().toDouble() / subTasks.size) * 100
        return progress.roundToInt()
    }

    fun addSubTask(newSubTask: SubTask): Boolean {
        if (newSubTask.subTaskName.trim().isEmpty()) {
            return false
        }
        subTasks.add(newSubTask)
        return true
    }

    fun deleteSubTask(taskID: Int): Boolean {
        if (subTasks.isEmpty()) {
            return false
        }
        return subTasks.removeAll { it.id == taskID }
    }
}

