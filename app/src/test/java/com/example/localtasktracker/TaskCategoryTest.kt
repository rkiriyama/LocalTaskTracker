package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TaskCategory].
 * Covers: construction, rename, subtask CRUD, computeProgress (plain subtasks,
 * parent subtasks with subitems, and mixed), data-integrity guarantees.
 */
class TaskCategoryTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun category(name: String = "Cat") = TaskCategory(id = 1, categoryName = name)

    private fun plainSubTask(id: Int, name: String, completed: Boolean = false) =
        SubTask(id = id, subTaskName = name, isCompleted = completed)

    private fun parentSubTask(id: Int, name: String, vararg subItems: Pair<Int, Boolean>): SubTask {
        val st = SubTask(id = id, subTaskName = name)
        subItems.forEach { (siId, done) ->
            st.subItems.add(SubItem(id = siId, subItemName = "SI$siId", isCompleted = done))
        }
        return st
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test fun constructor_setsFields() {
        val cat = TaskCategory(id = 3, categoryName = "Work")
        assertEquals(3, cat.id)
        assertEquals("Work", cat.categoryName)
        assertFalse(cat.isCompleted)
        assertTrue(cat.subTasks.isEmpty())
    }

    // ── renameCategory ────────────────────────────────────────────────────────

    @Test fun rename_valid_returnsTrue_updatesName() {
        val cat = category("Old")
        assertTrue(cat.renameCategory("New"))
        assertEquals("New", cat.categoryName)
    }

    @Test fun rename_trimsWhitespace() {
        val cat = category("Old")
        cat.renameCategory("  Trimmed  ")
        assertEquals("Trimmed", cat.categoryName)
    }

    @Test fun rename_empty_returnsFalse_nameUnchanged() {
        val cat = category("Original")
        assertFalse(cat.renameCategory(""))
        assertEquals("Original", cat.categoryName)
    }

    @Test fun rename_blank_returnsFalse_nameUnchanged() {
        val cat = category("Original")
        assertFalse(cat.renameCategory("   "))
        assertEquals("Original", cat.categoryName)
    }

    @Test fun rename_doesNotAffectSubTasks() {
        val cat = category("Cat")
        cat.subTasks.add(plainSubTask(1, "Sub"))
        cat.renameCategory("NewCat")
        assertEquals(1, cat.subTasks.size)
        assertEquals("Sub", cat.subTasks[0].subTaskName)
    }

    // ── addSubTask ────────────────────────────────────────────────────────────

    @Test fun addSubTask_valid_returnsTrue_appended() {
        val cat = category()
        assertTrue(cat.addSubTask(plainSubTask(1, "A")))
        assertEquals(1, cat.subTasks.size)
    }

    @Test fun addSubTask_multipleItems_preservesOrder() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A"))
        cat.addSubTask(plainSubTask(2, "B"))
        cat.addSubTask(plainSubTask(3, "C"))
        assertEquals(listOf("A", "B", "C"), cat.subTasks.map { it.subTaskName })
    }

    @Test fun addSubTask_emptyName_returnsFalse_notAdded() {
        val cat = category()
        assertFalse(cat.addSubTask(plainSubTask(1, "")))
        assertTrue(cat.subTasks.isEmpty())
    }

    @Test fun addSubTask_blankName_returnsFalse_notAdded() {
        val cat = category()
        assertFalse(cat.addSubTask(plainSubTask(1, "  ")))
        assertTrue(cat.subTasks.isEmpty())
    }

    // ── deleteSubTask ─────────────────────────────────────────────────────────

    @Test fun deleteSubTask_existingId_returnsTrue_removed() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A"))
        assertTrue(cat.deleteSubTask(1))
        assertTrue(cat.subTasks.isEmpty())
    }

    @Test fun deleteSubTask_nonExistingId_returnsFalse_listUnchanged() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A"))
        assertFalse(cat.deleteSubTask(99))
        assertEquals(1, cat.subTasks.size)
    }

    @Test fun deleteSubTask_emptyList_returnsFalse() {
        assertFalse(category().deleteSubTask(1))
    }

    @Test fun deleteSubTask_removesOnlyMatchingId() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "Keep"))
        cat.addSubTask(plainSubTask(2, "Delete"))
        cat.addSubTask(plainSubTask(3, "Keep"))
        cat.deleteSubTask(2)
        assertEquals(listOf("Keep", "Keep"), cat.subTasks.map { it.subTaskName })
    }

    @Test fun deleteSubTask_removesSubItemsToo() {
        // Deleting a parent subtask must not leave orphaned subitems accessible
        val cat = category()
        val st = parentSubTask(1, "Parent", 10 to true, 11 to false)
        cat.addSubTask(st)
        cat.deleteSubTask(1)
        assertTrue(cat.subTasks.isEmpty())
    }

    // ── computeProgress — plain subtasks ─────────────────────────────────────

    @Test fun computeProgress_noSubTasks_returnsZero() {
        assertEquals(0, category().computeProgress())
    }

    @Test fun computeProgress_allPlain_noneCompleted_returnsZero() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A", false))
        cat.addSubTask(plainSubTask(2, "B", false))
        assertEquals(0, cat.computeProgress())
    }

    @Test fun computeProgress_allPlain_allCompleted_returns100() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A", true))
        cat.addSubTask(plainSubTask(2, "B", true))
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_allPlain_halfCompleted_returns50() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A", true))
        cat.addSubTask(plainSubTask(2, "B", false))
        assertEquals(50, cat.computeProgress())
    }

    @Test fun computeProgress_allPlain_oneOfFour_returns25() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A", true))
        cat.addSubTask(plainSubTask(2, "B", false))
        cat.addSubTask(plainSubTask(3, "C", false))
        cat.addSubTask(plainSubTask(4, "D", false))
        assertEquals(25, cat.computeProgress())
    }

    // ── computeProgress — parent subtasks with subitems ───────────────────────

    @Test fun computeProgress_parentSubTask_allSubItemsDone_contributes100() {
        val cat = category()
        cat.addSubTask(parentSubTask(1, "Parent", 10 to true, 11 to true))
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_parentSubTask_noSubItemsDone_contributesZero() {
        val cat = category()
        cat.addSubTask(parentSubTask(1, "Parent", 10 to false, 11 to false))
        assertEquals(0, cat.computeProgress())
    }

    @Test fun computeProgress_parentSubTask_halfSubItemsDone_contributes50() {
        val cat = category()
        cat.addSubTask(parentSubTask(1, "Parent", 10 to true, 11 to false))
        assertEquals(50, cat.computeProgress())
    }

    // ── computeProgress — mixed plain and parent subtasks ────────────────────

    @Test fun computeProgress_mixed_plainCompleted_parentHalf() {
        // plain(100) + parent(50) → avg = 75
        val cat = category()
        cat.addSubTask(plainSubTask(1, "Plain", completed = true))
        cat.addSubTask(parentSubTask(2, "Parent", 10 to true, 11 to false))
        assertEquals(75, cat.computeProgress())
    }

    @Test fun computeProgress_mixed_plainIncomplete_parentFull() {
        // plain(0) + parent(100) → avg = 50
        val cat = category()
        cat.addSubTask(plainSubTask(1, "Plain", completed = false))
        cat.addSubTask(parentSubTask(2, "Parent", 10 to true, 11 to true))
        assertEquals(50, cat.computeProgress())
    }

    @Test fun computeProgress_mixed_threeSubTasks() {
        // plain(100) + plain(0) + parent(50) → avg ≈ 50
        val cat = category()
        cat.addSubTask(plainSubTask(1, "Done",    completed = true))
        cat.addSubTask(plainSubTask(2, "Pending", completed = false))
        cat.addSubTask(parentSubTask(3, "Parent", 10 to true, 11 to false))
        assertEquals(50, cat.computeProgress())
    }

    // ── computeProgress — live updates ───────────────────────────────────────

    @Test fun computeProgress_updatesWhenPlainSubTaskCompleted() {
        val cat = category()
        val st = plainSubTask(1, "A", false)
        cat.addSubTask(st)
        assertEquals(0, cat.computeProgress())
        st.changeSubTaskStatus(true)
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenSubItemCompleted() {
        val cat = category()
        val si = SubItem(id = 10, subItemName = "SI", isCompleted = false)
        val st = SubTask(id = 1, subTaskName = "Parent")
        st.subItems.add(si)
        cat.addSubTask(st)
        assertEquals(0, cat.computeProgress())
        si.changeSubItemStatus(true)
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenSubTaskDeleted() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "Done",    completed = true))
        cat.addSubTask(plainSubTask(2, "Pending", completed = false))
        assertEquals(50, cat.computeProgress())
        cat.deleteSubTask(2)
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenSubTaskAdded() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "Done", completed = true))
        assertEquals(100, cat.computeProgress())
        cat.addSubTask(plainSubTask(2, "New", completed = false))
        assertEquals(50, cat.computeProgress())
    }

    // ── getTasksCompleted ─────────────────────────────────────────────────────

    @Test fun getTasksCompleted_none_returnsZero() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A", false))
        assertEquals(0, cat.getTasksCompleted())
    }

    @Test fun getTasksCompleted_someCompleted_returnsCount() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A", true))
        cat.addSubTask(plainSubTask(2, "B", false))
        cat.addSubTask(plainSubTask(3, "C", true))
        assertEquals(2, cat.getTasksCompleted())
    }

    @Test fun getTasksCompleted_emptyList_returnsZero() {
        assertEquals(0, category().getTasksCompleted())
    }

    // ── Data integrity ────────────────────────────────────────────────────────

    @Test fun subTasksListIsIndependentAcrossCategories() {
        val cat1 = TaskCategory(id = 1, categoryName = "C1")
        val cat2 = TaskCategory(id = 2, categoryName = "C2")
        cat1.addSubTask(plainSubTask(1, "OnlyInC1"))
        assertTrue(cat2.subTasks.isEmpty())
    }

    @Test fun deleteSubTask_doesNotCorruptRemainingSubTasks() {
        val cat = category()
        cat.addSubTask(plainSubTask(1, "A"))
        cat.addSubTask(plainSubTask(2, "B"))
        cat.addSubTask(plainSubTask(3, "C"))
        cat.deleteSubTask(2)
        assertEquals(1, cat.subTasks[0].id)
        assertEquals(3, cat.subTasks[1].id)
    }

    @Test fun addSubTask_preservesExistingSubTaskData() {
        val cat = category()
        val existing = plainSubTask(1, "Existing", true)
        cat.addSubTask(existing)
        cat.addSubTask(plainSubTask(2, "New"))
        assertTrue(cat.subTasks[0].isCompleted)
        assertEquals("Existing", cat.subTasks[0].subTaskName)
    }
}
