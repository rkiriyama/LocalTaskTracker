package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CategoryReorderHelper].
 *
 * All tests run on the JVM — no Android framework involved.
 *
 * Naming convention: `methodOrScenario_condition_expectedOutcome`
 */
class CategoryReorderHelperTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeTask(vararg categoryNames: String): Task {
        val task = Task(id = 1, title = "Test Task")
        categoryNames.forEachIndexed { i, name ->
            val cat = TaskCategory(id = i + 1, categoryName = name)
            task.categories.add(cat)
        }
        return task
    }

    private fun makeTaskWithSubtasks(): Task {
        val task = Task(id = 1, title = "Test Task")
        val cat1 = TaskCategory(id = 1, categoryName = "Cat1").apply {
            subTasks.add(SubTask(id = 10, subTaskName = "Sub1A"))
            subTasks.add(SubTask(id = 11, subTaskName = "Sub1B"))
        }
        val cat2 = TaskCategory(id = 2, categoryName = "Cat2").apply {
            subTasks.add(SubTask(id = 20, subTaskName = "Sub2A"))
        }
        val cat3 = TaskCategory(id = 3, categoryName = "Cat3").apply {
            subTasks.add(SubTask(id = 30, subTaskName = "Sub3A"))
            subTasks.add(SubTask(id = 31, subTaskName = "Sub3B"))
            subTasks.add(SubTask(id = 32, subTaskName = "Sub3C"))
        }
        task.categories.addAll(listOf(cat1, cat2, cat3))
        return task
    }

    // ── canDragCategory ───────────────────────────────────────────────────────

    @Test
    fun canDragCategory_singleCategory_returnsFalse() {
        val helper = CategoryReorderHelper(makeTask("OnlyOne"))
        assertFalse(helper.canDragCategory())
    }

    @Test
    fun canDragCategory_noCategories_returnsFalse() {
        val helper = CategoryReorderHelper(Task(id = 1, title = "Empty"))
        assertFalse(helper.canDragCategory())
    }

    @Test
    fun canDragCategory_twoCategories_returnsTrue() {
        val helper = CategoryReorderHelper(makeTask("A", "B"))
        assertTrue(helper.canDragCategory())
    }

    @Test
    fun canDragCategory_manyCategories_returnsTrue() {
        val helper = CategoryReorderHelper(makeTask("A", "B", "C", "D"))
        assertTrue(helper.canDragCategory())
    }

    // ── beginCategoryDrag ─────────────────────────────────────────────────────

    @Test
    fun beginCategoryDrag_populatesShadowListFromTaskCategories() {
        val task = makeTask("Alpha", "Beta", "Gamma")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()

        assertEquals(3, helper.shadowList.size)
        assertEquals("Alpha", helper.shadowList[0].categoryName)
        assertEquals("Beta",  helper.shadowList[1].categoryName)
        assertEquals("Gamma", helper.shadowList[2].categoryName)
    }

    @Test
    fun beginCategoryDrag_setsDraggingTrue() {
        val helper = CategoryReorderHelper(makeTask("A", "B"))
        assertFalse(helper.isDragging)
        helper.beginCategoryDrag()
        assertTrue(helper.isDragging)
    }

    @Test
    fun beginCategoryDrag_calledTwice_isIdempotent() {
        val task = makeTask("A", "B", "C")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()
        // Swap so the shadow list differs from original order
        helper.swapCategories(0, 2)
        // Calling begin again should NOT reset the shadow list
        helper.beginCategoryDrag()
        // Still dragging, shadow list unchanged
        assertTrue(helper.isDragging)
        assertEquals("C", helper.shadowList[0].categoryName)
    }

    // ── swapCategories ────────────────────────────────────────────────────────

    @Test
    fun swapCategories_adjacentCategories_swapsCorrectly() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()

        helper.swapCategories(0, 1)

        assertEquals("Cat2", helper.shadowList[0].categoryName)
        assertEquals("Cat1", helper.shadowList[1].categoryName)
        assertEquals("Cat3", helper.shadowList[2].categoryName)
    }

    @Test
    fun swapCategories_nonAdjacentCategories_swapsCorrectly() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()

        helper.swapCategories(0, 3)

        assertEquals("Cat4", helper.shadowList[0].categoryName)
        assertEquals("Cat2", helper.shadowList[1].categoryName)
        assertEquals("Cat3", helper.shadowList[2].categoryName)
        assertEquals("Cat1", helper.shadowList[3].categoryName)
    }

    @Test
    fun swapCategories_sameIndex_noChange() {
        val task = makeTask("A", "B", "C")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()

        helper.swapCategories(1, 1)

        assertEquals(listOf("A", "B", "C"), helper.shadowList.map { it.categoryName })
    }

    @Test
    fun swapCategories_keepsTaskCategoriesInSync() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()

        helper.swapCategories(0, 2)

        // task.categories must mirror the shadow list
        assertEquals("Cat3", task.categories[0].categoryName)
        assertEquals("Cat2", task.categories[1].categoryName)
        assertEquals("Cat1", task.categories[2].categoryName)
    }

    @Test
    fun swapCategories_calledWithoutBeginDrag_throwsIllegalState() {
        val helper = CategoryReorderHelper(makeTask("A", "B"))
        assertThrows(IllegalStateException::class.java) {
            helper.swapCategories(0, 1)
        }
    }

    @Test
    fun swapCategories_outOfBoundsIndex_throwsIllegalArgument() {
        val task = makeTask("A", "B")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()

        assertThrows(IllegalArgumentException::class.java) {
            helper.swapCategories(0, 5)
        }
    }

    // ── commitCategoryDrag ────────────────────────────────────────────────────

    @Test
    fun commitCategoryDrag_resetsDraggingAndClearsShadow() {
        val task = makeTask("A", "B", "C")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()
        helper.swapCategories(0, 2)
        helper.commitCategoryDrag()

        assertFalse(helper.isDragging)
        assertTrue(helper.shadowList.isEmpty())
    }

    @Test
    fun commitCategoryDrag_preservesFinalOrder() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()
        helper.swapCategories(0, 1)  // Cat2, Cat1, Cat3
        helper.swapCategories(1, 2)  // Cat2, Cat3, Cat1
        helper.commitCategoryDrag()

        assertEquals("Cat2", task.categories[0].categoryName)
        assertEquals("Cat3", task.categories[1].categoryName)
        assertEquals("Cat1", task.categories[2].categoryName)
    }

    // ── cancelCategoryDrag ────────────────────────────────────────────────────

    @Test
    fun cancelCategoryDrag_restoresOriginalOrder() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()
        helper.swapCategories(0, 2)  // order is now Cat3, Cat2, Cat1

        helper.cancelCategoryDrag()

        assertFalse(helper.isDragging)
        assertEquals("Cat1", task.categories[0].categoryName)
        assertEquals("Cat2", task.categories[1].categoryName)
        assertEquals("Cat3", task.categories[2].categoryName)
    }

    @Test
    fun cancelCategoryDrag_whenNotDragging_doesNothing() {
        val task = makeTask("A", "B")
        val helper = CategoryReorderHelper(task)
        // Should not throw
        helper.cancelCategoryDrag()
        assertFalse(helper.isDragging)
    }

    // ── Subtask integrity ─────────────────────────────────────────────────────

    @Test
    fun swapCategories_subtasksStayInsideTheirCategory() {
        val task = makeTaskWithSubtasks()
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()

        // Move Cat1 (index 0) to index 2
        helper.swapCategories(0, 2)
        helper.commitCategoryDrag()

        // After swap: order is Cat3, Cat2, Cat1
        val cat3 = task.categories[0]
        val cat2 = task.categories[1]
        val cat1 = task.categories[2]

        assertEquals("Cat3", cat3.categoryName)
        assertEquals(3, cat3.subTasks.size)
        assertEquals("Sub3A", cat3.subTasks[0].subTaskName)
        assertEquals("Sub3B", cat3.subTasks[1].subTaskName)
        assertEquals("Sub3C", cat3.subTasks[2].subTaskName)

        assertEquals("Cat2", cat2.categoryName)
        assertEquals(1, cat2.subTasks.size)
        assertEquals("Sub2A", cat2.subTasks[0].subTaskName)

        assertEquals("Cat1", cat1.categoryName)
        assertEquals(2, cat1.subTasks.size)
        assertEquals("Sub1A", cat1.subTasks[0].subTaskName)
        assertEquals("Sub1B", cat1.subTasks[1].subTaskName)
    }

    @Test
    fun swapCategories_subtaskCompletionStatePreserved() {
        val task = Task(id = 1, title = "T")
        val cat1 = TaskCategory(id = 1, categoryName = "Cat1").apply {
            subTasks.add(SubTask(id = 10, subTaskName = "Done", isCompleted = true))
            subTasks.add(SubTask(id = 11, subTaskName = "Pending", isCompleted = false))
        }
        val cat2 = TaskCategory(id = 2, categoryName = "Cat2").apply {
            subTasks.add(SubTask(id = 20, subTaskName = "AlsoDone", isCompleted = true))
        }
        task.categories.addAll(listOf(cat1, cat2))

        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()
        helper.swapCategories(0, 1)
        helper.commitCategoryDrag()

        val newCat1 = task.categories[0] // was Cat2
        assertTrue(newCat1.subTasks[0].isCompleted)

        val newCat2 = task.categories[1] // was Cat1
        assertTrue(newCat2.subTasks[0].isCompleted)
        assertFalse(newCat2.subTasks[1].isCompleted)
    }

    // ── Full drag simulation ──────────────────────────────────────────────────

    @Test
    fun fullDragSimulation_moveCat1AboveCat3_in4CatList() {
        // Start: Cat1, Cat2, Cat3, Cat4
        // Drag Cat1 (pos 0) down past Cat2 (→ pos 1), then past Cat3 (→ pos 2)
        // Expected final order: Cat2, Cat3, Cat1, Cat4
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        val helper = CategoryReorderHelper(task)

        helper.beginCategoryDrag()
        helper.swapCategories(0, 1)  // Cat2, Cat1, Cat3, Cat4
        helper.swapCategories(1, 2)  // Cat2, Cat3, Cat1, Cat4
        helper.commitCategoryDrag()

        assertEquals("Cat2", task.categories[0].categoryName)
        assertEquals("Cat3", task.categories[1].categoryName)
        assertEquals("Cat1", task.categories[2].categoryName)
        assertEquals("Cat4", task.categories[3].categoryName)
    }

    @Test
    fun fullDragSimulation_moveCat4ToCat1Position_in4CatList() {
        // Start: Cat1, Cat2, Cat3, Cat4
        // Drag Cat4 (pos 3) up to pos 0
        // Expected final order: Cat4, Cat1, Cat2, Cat3
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        val helper = CategoryReorderHelper(task)

        helper.beginCategoryDrag()
        helper.swapCategories(3, 2)  // Cat1, Cat2, Cat4, Cat3
        helper.swapCategories(2, 1)  // Cat1, Cat4, Cat2, Cat3
        helper.swapCategories(1, 0)  // Cat4, Cat1, Cat2, Cat3
        helper.commitCategoryDrag()

        assertEquals("Cat4", task.categories[0].categoryName)
        assertEquals("Cat1", task.categories[1].categoryName)
        assertEquals("Cat2", task.categories[2].categoryName)
        assertEquals("Cat3", task.categories[3].categoryName)
    }

    @Test
    fun fullDragSimulation_twoCategorySwap_withSubtasks() {
        // Start: Cat1 (Sub1A, Sub1B), Cat2 (Sub2A)
        // Drag Cat1 to position 1 → Cat2 takes position 0
        val task = makeTaskWithSubtasks().let {
            // Use just the first two categories for simplicity
            Task(id = 1, title = "T").also { t ->
                t.categories.add(it.categories[0]) // Cat1
                t.categories.add(it.categories[1]) // Cat2
            }
        }
        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()
        helper.swapCategories(0, 1)
        helper.commitCategoryDrag()

        assertEquals("Cat2", task.categories[0].categoryName)
        assertEquals("Cat1", task.categories[1].categoryName)
        assertEquals(1, task.categories[0].subTasks.size)
        assertEquals(2, task.categories[1].subTasks.size)
    }

    @Test
    fun fullDragSimulation_cancelMidDrag_restoresOriginalOrder() {
        // Start: Cat1, Cat2, Cat3
        // Drag Cat1 past Cat2 then cancel
        val task = makeTask("Cat1", "Cat2", "Cat3")
        val helper = CategoryReorderHelper(task)

        helper.beginCategoryDrag()
        helper.swapCategories(0, 1)  // Cat2, Cat1, Cat3
        helper.cancelCategoryDrag()  // should restore Cat1, Cat2, Cat3

        assertEquals("Cat1", task.categories[0].categoryName)
        assertEquals("Cat2", task.categories[1].categoryName)
        assertEquals("Cat3", task.categories[2].categoryName)
    }

    @Test
    fun fullDragSimulation_noMovement_taskOrderUnchanged() {
        // begin + immediate commit without any swaps should not alter order
        val task = makeTask("Alpha", "Beta", "Gamma")
        val helper = CategoryReorderHelper(task)

        helper.beginCategoryDrag()
        helper.commitCategoryDrag()

        assertEquals("Alpha", task.categories[0].categoryName)
        assertEquals("Beta",  task.categories[1].categoryName)
        assertEquals("Gamma", task.categories[2].categoryName)
    }

    @Test
    fun fullDragSimulation_categoryIdsArePreservedAfterSwap() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        val originalIds = task.categories.map { it.id }

        val helper = CategoryReorderHelper(task)
        helper.beginCategoryDrag()
        helper.swapCategories(0, 2)
        helper.commitCategoryDrag()

        // The same category objects should be there, just reordered
        val newIds = task.categories.map { it.id }
        assertEquals(originalIds.toSet(), newIds.toSet())
        // Specifically: id at pos 0 was originally at pos 2 and vice-versa
        assertEquals(originalIds[2], newIds[0])
        assertEquals(originalIds[1], newIds[1])
        assertEquals(originalIds[0], newIds[2])
    }
}
