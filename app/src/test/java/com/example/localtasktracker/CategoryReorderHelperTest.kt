package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [CategoryReorderHelper].
 * All tests run on the JVM — no Android framework required.
 */
class CategoryReorderHelperTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeTask(vararg categoryNames: String): Task {
        val task = Task(id = 1, title = "Test Task")
        categoryNames.forEachIndexed { i, name ->
            task.categories.add(TaskCategory(id = i + 1, categoryName = name))
        }
        return task
    }

    private fun makeTaskWithSubtasks(): Task {
        val task = Task(id = 1, title = "Test Task")
        task.categories.add(TaskCategory(id = 1, categoryName = "Cat1").apply {
            subTasks.add(SubTask(id = 10, subTaskName = "Sub1A"))
            subTasks.add(SubTask(id = 11, subTaskName = "Sub1B"))
        })
        task.categories.add(TaskCategory(id = 2, categoryName = "Cat2").apply {
            subTasks.add(SubTask(id = 20, subTaskName = "Sub2A"))
        })
        task.categories.add(TaskCategory(id = 3, categoryName = "Cat3").apply {
            subTasks.add(SubTask(id = 30, subTaskName = "Sub3A"))
            subTasks.add(SubTask(id = 31, subTaskName = "Sub3B"))
            subTasks.add(SubTask(id = 32, subTaskName = "Sub3C"))
        })
        return task
    }

    private fun categoryNames(task: Task) = task.categories.map { it.categoryName }

    // ── canDragCategory ───────────────────────────────────────────────────────

    @Test fun canDrag_noCategories_false() {
        assertFalse(CategoryReorderHelper(Task(1, "empty")).canDragCategory())
    }

    @Test fun canDrag_oneCategory_false() {
        assertFalse(CategoryReorderHelper(makeTask("Only")).canDragCategory())
    }

    @Test fun canDrag_twoCategories_true() {
        assertTrue(CategoryReorderHelper(makeTask("A", "B")).canDragCategory())
    }

    @Test fun canDrag_manyCategories_true() {
        assertTrue(CategoryReorderHelper(makeTask("A", "B", "C", "D")).canDragCategory())
    }

    // ── moveCategory ──────────────────────────────────────────────────────────

    @Test fun move_firstToLast_shiftedCorrectly() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        CategoryReorderHelper(task).moveCategory(0, 3)
        assertEquals(listOf("Cat2", "Cat3", "Cat4", "Cat1"), categoryNames(task))
    }

    @Test fun move_lastToFirst_shiftedCorrectly() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        CategoryReorderHelper(task).moveCategory(3, 0)
        assertEquals(listOf("Cat4", "Cat1", "Cat2", "Cat3"), categoryNames(task))
    }

    @Test fun move_adjacent_downward() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        CategoryReorderHelper(task).moveCategory(0, 1)
        assertEquals(listOf("Cat2", "Cat1", "Cat3"), categoryNames(task))
    }

    @Test fun move_adjacent_upward() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        CategoryReorderHelper(task).moveCategory(2, 1)
        assertEquals(listOf("Cat1", "Cat3", "Cat2"), categoryNames(task))
    }

    @Test fun move_sameIndex_noChange() {
        val task = makeTask("A", "B", "C")
        CategoryReorderHelper(task).moveCategory(1, 1)
        assertEquals(listOf("A", "B", "C"), categoryNames(task))
    }

    @Test fun move_outOfBounds_from_throws() {
        val task = makeTask("A", "B")
        assertThrows(IllegalArgumentException::class.java) {
            CategoryReorderHelper(task).moveCategory(5, 0)
        }
    }

    @Test fun move_outOfBounds_to_throws() {
        val task = makeTask("A", "B")
        assertThrows(IllegalArgumentException::class.java) {
            CategoryReorderHelper(task).moveCategory(0, 5)
        }
    }

    // ── Subtask integrity ─────────────────────────────────────────────────────

    @Test fun move_subtasksStayInsideTheirCategory() {
        val task = makeTaskWithSubtasks()
        CategoryReorderHelper(task).moveCategory(0, 2) // Cat1 → end

        assertEquals("Cat2", task.categories[0].categoryName)
        assertEquals(1, task.categories[0].subTasks.size)
        assertEquals("Sub2A", task.categories[0].subTasks[0].subTaskName)

        assertEquals("Cat3", task.categories[1].categoryName)
        assertEquals(3, task.categories[1].subTasks.size)

        assertEquals("Cat1", task.categories[2].categoryName)
        assertEquals(2, task.categories[2].subTasks.size)
        assertEquals("Sub1A", task.categories[2].subTasks[0].subTaskName)
        assertEquals("Sub1B", task.categories[2].subTasks[1].subTaskName)
    }

    @Test fun move_subtaskCompletionStatePreserved() {
        val task = Task(id = 1, title = "T")
        task.categories.add(TaskCategory(id = 1, categoryName = "Cat1").apply {
            subTasks.add(SubTask(id = 10, subTaskName = "Done",    isCompleted = true))
            subTasks.add(SubTask(id = 11, subTaskName = "Pending", isCompleted = false))
        })
        task.categories.add(TaskCategory(id = 2, categoryName = "Cat2").apply {
            subTasks.add(SubTask(id = 20, subTaskName = "AlsoDone", isCompleted = true))
        })

        CategoryReorderHelper(task).moveCategory(0, 1)

        assertEquals("Cat2", task.categories[0].categoryName)
        assertTrue(task.categories[0].subTasks[0].isCompleted)

        assertEquals("Cat1", task.categories[1].categoryName)
        assertTrue(task.categories[1].subTasks[0].isCompleted)
        assertFalse(task.categories[1].subTasks[1].isCompleted)
    }

    @Test fun move_categoryIdsPreserved() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        val originalIds = task.categories.map { it.id }

        CategoryReorderHelper(task).moveCategory(0, 2)

        assertEquals(originalIds[1], task.categories[0].id)
        assertEquals(originalIds[2], task.categories[1].id)
        assertEquals(originalIds[0], task.categories[2].id)
    }

    // ── Multi-step drag simulation ────────────────────────────────────────────
    // Simulate ItemTouchHelper calling moveCategory once per row crossed,
    // exactly as it fires onItemMoved during a live gesture.

    @Test fun multiStep_dragCat1DownToPosition3_in4CatList() {
        // Cat1 at pos 0 dragged to pos 3 via 3 incremental steps
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        val h = CategoryReorderHelper(task)
        h.moveCategory(0, 1)  // Cat2, Cat1, Cat3, Cat4
        h.moveCategory(1, 2)  // Cat2, Cat3, Cat1, Cat4
        h.moveCategory(2, 3)  // Cat2, Cat3, Cat4, Cat1
        assertEquals(listOf("Cat2", "Cat3", "Cat4", "Cat1"), categoryNames(task))
    }

    @Test fun multiStep_dragCat4UpToPosition0_in4CatList() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        val h = CategoryReorderHelper(task)
        h.moveCategory(3, 2)  // Cat1, Cat2, Cat4, Cat3
        h.moveCategory(2, 1)  // Cat1, Cat4, Cat2, Cat3
        h.moveCategory(1, 0)  // Cat4, Cat1, Cat2, Cat3
        assertEquals(listOf("Cat4", "Cat1", "Cat2", "Cat3"), categoryNames(task))
    }

    @Test fun multiStep_dragMiddleCatToTop() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4", "Cat5")
        val h = CategoryReorderHelper(task)
        // Move Cat3 (pos 2) up to pos 0
        h.moveCategory(2, 1)  // Cat1, Cat3, Cat2, Cat4, Cat5
        h.moveCategory(1, 0)  // Cat3, Cat1, Cat2, Cat4, Cat5
        assertEquals(listOf("Cat3", "Cat1", "Cat2", "Cat4", "Cat5"), categoryNames(task))
    }

    @Test fun multiStep_dragWithSubtasks_subtasksUnchanged() {
        val task = makeTaskWithSubtasks() // Cat1(2 subs), Cat2(1 sub), Cat3(3 subs)
        val h = CategoryReorderHelper(task)
        // Drag Cat1 all the way to position 2
        h.moveCategory(0, 1)
        h.moveCategory(1, 2)
        // Expected: Cat2, Cat3, Cat1
        assertEquals("Cat2", task.categories[0].categoryName)
        assertEquals(1, task.categories[0].subTasks.size)
        assertEquals("Cat3", task.categories[1].categoryName)
        assertEquals(3, task.categories[1].subTasks.size)
        assertEquals("Cat1", task.categories[2].categoryName)
        assertEquals(2, task.categories[2].subTasks.size)
    }

    @Test fun multiStep_twoCategoryList_singleMove() {
        val task = makeTask("A", "B")
        CategoryReorderHelper(task).moveCategory(0, 1)
        assertEquals(listOf("B", "A"), categoryNames(task))
    }

    @Test fun multiStep_noMoves_orderUnchanged() {
        val task = makeTask("Alpha", "Beta", "Gamma")
        // No moves — order must be identical
        assertEquals(listOf("Alpha", "Beta", "Gamma"), categoryNames(task))
    }
}
