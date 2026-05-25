package com.example.localtasktracker

data class SubTask(
    val id: Int,
    var subTaskName: String,
    var isCompleted: Boolean = false
) {
    fun renameSubTask(newTaskName: String): Boolean {
        val trimmedTitle = newTaskName.trim()

        if (trimmedTitle.isEmpty()) {
            return false
        }
        subTaskName = trimmedTitle
        return true
    }

    fun changeSubTaskStatus(status: Boolean) {
        isCompleted = status
    }

    private fun getID(): Int {
        return id
    }
}
