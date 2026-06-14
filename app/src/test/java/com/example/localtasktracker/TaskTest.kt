package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [Task].
 * Covers: construction, rename, category CRUD, computeProgress (full
 * hierarchy roll-up through categories → subtasks → subitems), and
 * data-integrity guarantees.
 */
class TaskTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun task(title: String = "My Task") = Task(id = 1, title = title)

    private fun category(id: Int, name: String) = TaskCategory(id = id, categoryName = name)

    private fun plainSubTask(id: Int, name: String, completed: Boolean = false) =
        SubTask(id = id, subTaskName = name, isCompleted = completed)

    private fun parentSubTask(id: Int, name: String, vararg subItems: Pair<Int, Boolean>): SubTask {
        val st = SubTask(id = id, subTaskName = name)
        subItems.forEach { (siId, done) ->
            st.subItems.add(SubItem(id = siId, subItemName = "SI$siId", isCompleted = done))
        }
        return st
    }

    /** Build a category pre-loaded with plain subtasks. */
    private fun categoryWithPlain(
        catId: Int, catName: String, vararg subtasks: Pair<Int, Boolean>
    ): TaskCategory {
        val cat = category(catId, catName)
        subtasks.forEachIndexed { i, (stId, done) ->
            cat.addSubTask(plainSubTask(stId, "Sub${catId}_$i", done))
        }
        return cat
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test fun constructor_setsFields() {
        val t = Task(id = 7, title = "Shopping")
        assertEquals(7, t.id)
        assertEquals("Shopping", t.title)
        assertFalse(t.isCompleted)
        assertTrue(t.categories.isEmpty())
        assertTrue(t.uncategorizedTasks.isEmpty())
    }

    // ── renameTask ────────────────────────────────────────────────────────────

    @Test fun rename_valid_returnsTrue_updatesTitle() {
        val t = task("Old")
        assertTrue(t.renameTask("New"))
        assertEquals("New", t.title)
    }

    @Test fun rename_trimsWhitespace() {
        val t = task("Old")
        t.renameTask("  Trimmed  ")
        assertEquals("Trimmed", t.title)
    }

    @Test fun rename_empty_returnsFalse_titleUnchanged() {
        val t = task("Original")
        assertFalse(t.renameTask(""))
        assertEquals("Original", t.title)
    }

    @Test fun rename_blank_returnsFalse_titleUnchanged() {
        val t = task("Original")
        assertFalse(t.renameTask("   "))
        assertEquals("Original", t.title)
    }

    @Test fun rename_doesNotAffectCategories() {
        val t = task("T")
        t.addCategory(category(1, "C"))
        t.renameTask("NewT")
        assertEquals(1, t.categories.size)
    }

    // ── addCategory ───────────────────────────────────────────────────────────

    @Test fun addCategory_valid_returnsTrue_appended() {
        val t = task()
        assertTrue(t.addCategory(category(1, "Cat")))
        assertEquals(1, t.categories.size)
    }

    @Test fun addCategory_multipleCategories_preservesOrder() {
        val t = task()
        t.addCategory(category(1, "A"))
        t.addCategory(category(2, "B"))
        t.addCategory(category(3, "C"))
        assertEquals(listOf("A", "B", "C"), t.categories.map { it.categoryName })
    }

    @Test fun addCategory_emptyName_returnsFalse_notAdded() {
        val t = task()
        assertFalse(t.addCategory(category(1, "")))
        assertTrue(t.categories.isEmpty())
    }

    @Test fun addCategory_blankName_returnsFalse_notAdded() {
        val t = task()
        assertFalse(t.addCategory(category(1, "  ")))
        assertTrue(t.categories.isEmpty())
    }

    // ── deleteCategory ────────────────────────────────────────────────────────

    @Test fun deleteCategory_existingId_returnsTrue_removed() {
        val t = task()
        t.addCategory(category(1, "Cat"))
        assertTrue(t.deleteCategory(1))
        assertTrue(t.categories.isEmpty())
    }

    @Test fun deleteCategory_nonExistingId_returnsFalse_listUnchanged() {
        val t = task()
        t.addCategory(category(1, "Cat"))
        assertFalse(t.deleteCategory(99))
        assertEquals(1, t.categories.size)
    }

    @Test fun deleteCategory_emptyList_returnsFalse() {
        assertFalse(task().deleteCategory(1))
    }

    @Test fun deleteCategory_removesOnlyMatchingId() {
        val t = task()
        t.addCategory(category(1, "Keep"))
        t.addCategory(category(2, "Delete"))
        t.addCategory(category(3, "Keep"))
        t.deleteCategory(2)
        assertEquals(listOf("Keep", "Keep"), t.categories.map { it.categoryName })
    }

    // ── computeProgress — empty ───────────────────────────────────────────────

    @Test fun computeProgress_noCategories_noUncategorized_returnsZero() {
        assertEquals(0, task().computeProgress())
    }

    @Test fun computeProgress_emptyCategoryOnly_returnsZero() {
        val t = task()
        t.addCategory(category(1, "Empty"))
        assertEquals(0, t.computeProgress())
    }

    // ── computeProgress — single category, plain subtasks ─────────────────────

    @Test fun computeProgress_oneCategory_allComplete_returns100() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C", 1 to true, 2 to true))
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_oneCategory_noneComplete_returnsZero() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C", 1 to false, 2 to false))
        assertEquals(0, t.computeProgress())
    }

    @Test fun computeProgress_oneCategory_halfComplete_returns50() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C", 1 to true, 2 to false))
        assertEquals(50, t.computeProgress())
    }

    // ── computeProgress — multiple categories ────────────────────────────────

    @Test fun computeProgress_twoCategories_average() {
        // Cat1 = 100%, Cat2 = 0% → avg = 50
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        t.addCategory(categoryWithPlain(2, "C2", 2 to false))
        assertEquals(50, t.computeProgress())
    }

    @Test fun computeProgress_threeCategories_average() {
        // Cat1=100, Cat2=50, Cat3=0 → avg ≈ 50
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        t.addCategory(categoryWithPlain(2, "C2", 2 to true, 3 to false))
        t.addCategory(categoryWithPlain(3, "C3", 4 to false))
        assertEquals(50, t.computeProgress())
    }

    // ── computeProgress — subitems roll up through hierarchy ──────────────────

    @Test fun computeProgress_subItems_rollUpThroughCategoryToTask() {
        // subtask has 1 of 2 subitems done → category progress = 50 → task = 50
        val t = task()
        val cat = category(1, "C")
        cat.addSubTask(parentSubTask(1, "Parent", 10 to true, 11 to false))
        t.addCategory(cat)
        assertEquals(50, t.computeProgress())
    }

    @Test fun computeProgress_allSubItemsDone_returns100() {
        val t = task()
        val cat = category(1, "C")
        cat.addSubTask(parentSubTask(1, "Parent", 10 to true, 11 to true))
        t.addCategory(cat)
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_noSubItemsDone_returnsZero() {
        val t = task()
        val cat = category(1, "C")
        cat.addSubTask(parentSubTask(1, "Parent", 10 to false, 11 to false))
        t.addCategory(cat)
        assertEquals(0, t.computeProgress())
    }

    @Test fun computeProgress_mixedPlainAndParentSubTasks_inOneCategory() {
        // plain(100) + parent(50) → category = 75 → task = 75
        val t = task()
        val cat = category(1, "C")
        cat.addSubTask(plainSubTask(1, "Plain", completed = true))
        cat.addSubTask(parentSubTask(2, "Parent", 10 to true, 11 to false))
        t.addCategory(cat)
        assertEquals(75, t.computeProgress())
    }

    @Test fun computeProgress_subItemsAcrossMultipleCategories() {
        // Cat1: parent subtask 1/2 done → 50%
        // Cat2: plain subtask done → 100%
        // Task avg = 75
        val t = task()
        val cat1 = category(1, "C1")
        cat1.addSubTask(parentSubTask(1, "P1", 10 to true, 11 to false))
        val cat2 = category(2, "C2")
        cat2.addSubTask(plainSubTask(2, "Plain", completed = true))
        t.addCategory(cat1)
        t.addCategory(cat2)
        assertEquals(75, t.computeProgress())
    }

    // ── computeProgress — live updates ───────────────────────────────────────

    @Test fun computeProgress_updatesWhenSubTaskCompleted() {
        val t = task()
        val st = plainSubTask(1, "A", false)
        val cat = category(1, "C")
        cat.addSubTask(st)
        t.addCategory(cat)
        assertEquals(0, t.computeProgress())
        st.changeSubTaskStatus(true)
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_updatesWhenSubItemCompleted() {
        val t = task()
        val si = SubItem(id = 10, subItemName = "SI", isCompleted = false)
        val st = SubTask(id = 1, subTaskName = "Parent")
        st.subItems.add(si)
        val cat = category(1, "C")
        cat.addSubTask(st)
        t.addCategory(cat)
        assertEquals(0, t.computeProgress())
        si.changeSubItemStatus(true)
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_updatesWhenCategoryDeleted() {
        // Cat1=100, Cat2=0 → 50. Delete Cat2 → 100.
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        t.addCategory(categoryWithPlain(2, "C2", 2 to false))
        assertEquals(50, t.computeProgress())
        t.deleteCategory(2)
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_updatesWhenCategoryAdded() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        assertEquals(100, t.computeProgress())
        t.addCategory(categoryWithPlain(2, "C2", 2 to false))
        assertEquals(50, t.computeProgress())
    }

    // ── uncategorized subtasks ────────────────────────────────────────────────

    @Test fun addUncategorizedSubTask_valid_returnsTrue() {
        val t = task()
        assertTrue(t.addUncategorizedSubTask(plainSubTask(1, "Free")))
        assertEquals(1, t.uncategorizedTasks.size)
    }

    @Test fun addUncategorizedSubTask_empty_returnsFalse() {
        val t = task()
        assertFalse(t.addUncategorizedSubTask(plainSubTask(1, "")))
        assertTrue(t.uncategorizedTasks.isEmpty())
    }

    @Test fun deleteUncategorizedSubTask_existingId_returnsTrue() {
        val t = task()
        t.addUncategorizedSubTask(plainSubTask(1, "Free"))
        assertTrue(t.deleteUncategorizedSubTask(1))
        assertTrue(t.uncategorizedTasks.isEmpty())
    }

    @Test fun deleteUncategorizedSubTask_nonExistingId_returnsFalse() {
        val t = task()
        t.addUncategorizedSubTask(plainSubTask(1, "Free"))
        assertFalse(t.deleteUncategorizedSubTask(99))
        assertEquals(1, t.uncategorizedTasks.size)
    }

    @Test fun computeProgress_uncategorizedPlain_countsCorrectly() {
        val t = task()
        t.addUncategorizedSubTask(plainSubTask(1, "Done",    completed = true))
        t.addUncategorizedSubTask(plainSubTask(2, "Pending", completed = false))
        assertEquals(50, t.computeProgress())
    }

    @Test fun computeProgress_uncategorizedParent_usesSubItemProgress() {
        // uncategorized parent subtask: 1 of 2 subitems done → 50%
        val t = task()
        val st = parentSubTask(1, "Parent", 10 to true, 11 to false)
        t.addUncategorizedSubTask(st)
        assertEquals(50, t.computeProgress())
    }

    @Test fun computeProgress_mixedCategorizedAndUncategorized() {
        // category(100) + uncategorized plain(0) → avg = 50
        val t = task()
        t.addCategory(categoryWithPlain(1, "C", 1 to true))
        t.addUncategorizedSubTask(plainSubTask(2, "Free", completed = false))
        assertEquals(50, t.computeProgress())
    }

    @Test fun getUncategorizedTasksCompleted_countsOnlyCompleted() {
        val t = task()
        t.addUncategorizedSubTask(plainSubTask(1, "A", completed = true))
        t.addUncategorizedSubTask(plainSubTask(2, "B", completed = false))
        t.addUncategorizedSubTask(plainSubTask(3, "C", completed = true))
        assertEquals(2, t.getUncategorizedTasksCompleted())
    }

    // ── Data integrity ────────────────────────────────────────────────────────

    @Test fun categoriesListIsIndependentAcrossTasks() {
        val t1 = Task(id = 1, title = "T1")
        val t2 = Task(id = 2, title = "T2")
        t1.addCategory(category(1, "OnlyInT1"))
        assertTrue(t2.categories.isEmpty())
    }

    @Test fun deleteCategory_doesNotCorruptRemainingCategories() {
        val t = task()
        t.addCategory(category(1, "A"))
        t.addCategory(category(2, "B"))
        t.addCategory(category(3, "C"))
        t.deleteCategory(2)
        assertEquals(1, t.categories[0].id)
        assertEquals(3, t.categories[1].id)
    }

    @Test fun deleteCategory_withSubTasksAndSubItems_leavesTaskIntact() {
        val t = task()
        val cat = category(1, "Rich")
        val st  = parentSubTask(1, "Parent", 10 to true, 11 to false)
        cat.addSubTask(st)
        t.addCategory(cat)
        t.addCategory(category(2, "Safe"))
        t.deleteCategory(1)
        assertEquals(1, t.categories.size)
        assertEquals("Safe", t.categories[0].categoryName)
    }

    @Test fun addCategory_preservesExistingCategoryData() {
        val t = task()
        val existing = category(1, "Existing").also {
            it.addSubTask(plainSubTask(1, "Sub", completed = true))
        }
        t.addCategory(existing)
        t.addCategory(category(2, "New"))
        // First category data must be unchanged
        assertEquals(1, t.categories[0].subTasks.size)
        assertTrue(t.categories[0].subTasks[0].isCompleted)
    }
}
