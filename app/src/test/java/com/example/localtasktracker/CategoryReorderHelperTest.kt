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
            task.children.add(Node(id = i + 1, name = name, nodeType = NodeType.CATEGORY))
        }
        return task
    }

    private fun makeTaskWithChildren(): Task {
        val task = Task(id = 1, title = "Test Task")
        task.children.add(Node(1, "Cat1", NodeType.CATEGORY).apply {
            children.add(Node(10, "Child1A", NodeType.NODE))
            children.add(Node(11, "Child1B", NodeType.NODE))
        })
        task.children.add(Node(2, "Cat2", NodeType.CATEGORY).apply {
            children.add(Node(20, "Child2A", NodeType.NODE))
        })
        task.children.add(Node(3, "Cat3", NodeType.CATEGORY).apply {
            children.add(Node(30, "Child3A", NodeType.NODE))
            children.add(Node(31, "Child3B", NodeType.NODE))
            children.add(Node(32, "Child3C", NodeType.NODE))
        })
        return task
    }

    private fun categoryNames(task: Task) = task.children.map { it.name }

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

    // ── moveCategory ──────────────────────────────────────────────────────────

    @Test fun move_firstToLast() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        CategoryReorderHelper(task).moveCategory(0, 3)
        assertEquals(listOf("Cat2", "Cat3", "Cat4", "Cat1"), categoryNames(task))
    }

    @Test fun move_lastToFirst() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        CategoryReorderHelper(task).moveCategory(3, 0)
        assertEquals(listOf("Cat4", "Cat1", "Cat2", "Cat3"), categoryNames(task))
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

    // ── Children integrity ────────────────────────────────────────────────────

    @Test fun move_childrenStayInsideTheirCategory() {
        val task = makeTaskWithChildren()
        CategoryReorderHelper(task).moveCategory(0, 2)

        assertEquals("Cat2", task.children[0].name)
        assertEquals(1, task.children[0].children.size)

        assertEquals("Cat3", task.children[1].name)
        assertEquals(3, task.children[1].children.size)

        assertEquals("Cat1", task.children[2].name)
        assertEquals(2, task.children[2].children.size)
        assertEquals("Child1A", task.children[2].children[0].name)
        assertEquals("Child1B", task.children[2].children[1].name)
    }

    @Test fun move_childCompletionStatePreserved() {
        val task = Task(id = 1, title = "T")
        task.children.add(Node(1, "Cat1", NodeType.CATEGORY).apply {
            children.add(Node(10, "Done", NodeType.NODE, isCompleted = true))
            children.add(Node(11, "Pending", NodeType.NODE, isCompleted = false))
        })
        task.children.add(Node(2, "Cat2", NodeType.CATEGORY).apply {
            children.add(Node(20, "AlsoDone", NodeType.NODE, isCompleted = true))
        })

        CategoryReorderHelper(task).moveCategory(0, 1)

        assertEquals("Cat2", task.children[0].name)
        assertTrue(task.children[0].children[0].isCompleted)

        assertEquals("Cat1", task.children[1].name)
        assertTrue(task.children[1].children[0].isCompleted)
        assertFalse(task.children[1].children[1].isCompleted)
    }

    @Test fun move_categoryIdsPreserved() {
        val task = makeTask("Cat1", "Cat2", "Cat3")
        val originalIds = task.children.map { it.id }

        CategoryReorderHelper(task).moveCategory(0, 2)

        assertEquals(originalIds[1], task.children[0].id)
        assertEquals(originalIds[2], task.children[1].id)
        assertEquals(originalIds[0], task.children[2].id)
    }

    // ── Multi-step drag simulation ────────────────────────────────────────────

    @Test fun multiStep_dragDown() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        val h = CategoryReorderHelper(task)
        h.moveCategory(0, 1)
        h.moveCategory(1, 2)
        h.moveCategory(2, 3)
        assertEquals(listOf("Cat2", "Cat3", "Cat4", "Cat1"), categoryNames(task))
    }

    @Test fun multiStep_dragUp() {
        val task = makeTask("Cat1", "Cat2", "Cat3", "Cat4")
        val h = CategoryReorderHelper(task)
        h.moveCategory(3, 2)
        h.moveCategory(2, 1)
        h.moveCategory(1, 0)
        assertEquals(listOf("Cat4", "Cat1", "Cat2", "Cat3"), categoryNames(task))
    }
}
