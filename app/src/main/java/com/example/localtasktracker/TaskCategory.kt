package com.example.localtasktracker
import kotlin.math.roundToInt

data class TaskCategory(
    val id: Int,
    var categoryName: String,
    var isCompleted: Boolean = false,
    var completionProgress: Int = 0,
    var subTasksCount: Int = 0,
    val subTasks: MutableList<SubTask> = mutableListOf(),
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
        if ((status && (completionProgress != 100)) or (!status && (completionProgress == 100))) {
            return false
        }
        isCompleted = status
        return true
    }

    fun getTasksCompleted(): Int {
        return subTasks.count { it.isCompleted }
    }

    fun computeProgress(): Int {
        if (subTasksCount == 0) {
            return -1
        }
        val progress = (getTasksCompleted().toDouble() / subTasksCount) * 100
        return progress.roundToInt()
    }

    fun addSubTask(newSubTask: SubTask): Boolean {
        subTasks.add(newSubTask)
        subTasksCount++
        return true
    }

    fun deleteSubTask(taskID: Int): Boolean {
        if (subTasksCount == 0) {
            return false
        }
        return subTasks.removeAll { it.id == taskID }
    }
}

