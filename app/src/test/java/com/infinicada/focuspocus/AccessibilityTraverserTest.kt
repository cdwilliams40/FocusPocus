package com.infinicada.focuspocus

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.system.measureNanoTime

class AccessibilityTraverserTest {

    @Before
    fun setUp() {
        UrlUtils.urlMatcher = { text -> text.contains("http") }
    }

    @After
    fun tearDown() {
        UrlUtils.urlMatcher = { false }
    }

    @Test
    fun benchmarkTraversal() {
        // Benchmark large tree traversal
        // Root -> 50 children -> each 5 -> each 2 = 1 + 50 + 250 + 500 = 801 nodes.
        val root = createRecursiveMockNode(0, listOf(50, 5, 2))
        val time = measureNanoTime {
            AccessibilityTraverser.findUrlInNodeTree(root, 0)
        }
        println("Benchmark time (ms): ${time / 1_000_000.0}")
    }

    @Test
    fun testFindUrlSuccessfully() {
        // Create a simple tree: Root -> Child0, Child1 -> GrandChild (URL)
        val root = mock<AccessibilityNodeInfo>()
        val child0 = mock<AccessibilityNodeInfo>()
        val child1 = mock<AccessibilityNodeInfo>()
        val grandChild = mock<AccessibilityNodeInfo>()

        // Root setup
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChild(0)).thenReturn(child0)
        whenever(root.getChild(1)).thenReturn(child1)
        whenever(root.isEditable).thenReturn(false)
        whenever(root.text).thenReturn("root")

        // Child0 setup (empty)
        whenever(child0.childCount).thenReturn(0)
        whenever(child0.isEditable).thenReturn(false)
        whenever(child0.text).thenReturn("child0")

        // Child1 setup (has grandchild)
        whenever(child1.childCount).thenReturn(1)
        whenever(child1.getChild(0)).thenReturn(grandChild)
        whenever(child1.isEditable).thenReturn(false)
        whenever(child1.text).thenReturn("child1")

        // Grandchild setup (is URL)
        whenever(grandChild.childCount).thenReturn(0)
        whenever(grandChild.text).thenReturn("https://example.com")
        whenever(grandChild.isEditable).thenReturn(true)

        val result = AccessibilityTraverser.findUrlInNodeTree(root, 0)
        assertEquals("https://example.com", result)
    }

    @Test
    fun testFindUrl_TooDeep_ReturnsNull() {
        // Create linear path deeper than MAX_TREE_DEPTH (10)
        // e.g. depth 12
        val root = createLinearPath(12, "https://example.com")
        val result = AccessibilityTraverser.findUrlInNodeTree(root, 0)
        assertNull(result)
    }

    @Test
    fun testFindUrl_WithinDepth_ReturnsUrl() {
        // Create linear path within limit (e.g. depth 9)
        val root = createLinearPath(9, "https://example.com")
        val result = AccessibilityTraverser.findUrlInNodeTree(root, 0)
        assertEquals("https://example.com", result)
    }

    @Test
    fun testFindUrl_TooWide_ReturnsNull() {
        // Root has 15 children. Max processed is 10.
        // We place the URL at index 12, so it should be skipped.
        val root = mock<AccessibilityNodeInfo>()
        whenever(root.childCount).thenReturn(15)
        whenever(root.isEditable).thenReturn(false)
        whenever(root.text).thenReturn("root")

        whenever(root.getChild(any())).thenAnswer { inv ->
            val index = inv.arguments[0] as Int
            val child = mock<AccessibilityNodeInfo>()
            whenever(child.childCount).thenReturn(0)

            if (index == 12) {
                whenever(child.text).thenReturn("https://example.com")
                whenever(child.isEditable).thenReturn(true)
            } else {
                 whenever(child.text).thenReturn("Not a URL")
                 whenever(child.isEditable).thenReturn(false)
            }
            child
        }

        val result = AccessibilityTraverser.findUrlInNodeTree(root, 0)
        assertNull("Should not find URL at index 12 because it exceeds width limit", result)
    }

    @Test
    fun testFindUrl_WithinWidth_ReturnsUrl() {
        // Root has 15 children. Max processed is 10.
        // We place the URL at index 2, so it should be found.
        val root = mock<AccessibilityNodeInfo>()
        whenever(root.childCount).thenReturn(15)
        whenever(root.isEditable).thenReturn(false)
        whenever(root.text).thenReturn("root")

        whenever(root.getChild(any())).thenAnswer { inv ->
            val index = inv.arguments[0] as Int
            val child = mock<AccessibilityNodeInfo>()
            whenever(child.childCount).thenReturn(0)

            if (index == 2) {
                whenever(child.text).thenReturn("https://example.com")
                whenever(child.isEditable).thenReturn(true)
            } else {
                 whenever(child.text).thenReturn("Not a URL")
                 whenever(child.isEditable).thenReturn(false)
            }
            child
        }

        val result = AccessibilityTraverser.findUrlInNodeTree(root, 0)
        assertEquals("https://example.com", result)
    }

    private fun createRecursiveMockNode(depth: Int, widths: List<Int>): AccessibilityNodeInfo {
        val node = mock<AccessibilityNodeInfo>()
        val width = if (depth < widths.size) widths[depth] else 0

        whenever(node.childCount).thenReturn(width)
        whenever(node.text).thenReturn("Not a URL")
        whenever(node.isEditable).thenReturn(false)

        // Only mock getChild if there are children
        if (width > 0) {
            whenever(node.getChild(any())).thenAnswer { invocation ->
                val index = invocation.arguments[0] as Int
                if (index < width) {
                    createRecursiveMockNode(depth + 1, widths)
                } else {
                    null
                }
            }
        }
        return node
    }

    private fun createLinearPath(depth: Int, url: String): AccessibilityNodeInfo {
        // Create root
        val root = mock<AccessibilityNodeInfo>()

        if (depth == 0) {
            whenever(root.text).thenReturn(url)
            whenever(root.isEditable).thenReturn(true)
            whenever(root.childCount).thenReturn(0)
            return root
        }

        whenever(root.childCount).thenReturn(1)
        whenever(root.text).thenReturn("root")
        whenever(root.isEditable).thenReturn(false)

        var currentNode = root

        // Create chain of nodes
        for (i in 1..depth) {
            val nextNode = mock<AccessibilityNodeInfo>()
            whenever(currentNode.getChild(0)).thenReturn(nextNode)

            if (i == depth) {
                // Leaf node (target)
                whenever(nextNode.childCount).thenReturn(0)
                whenever(nextNode.text).thenReturn(url)
                whenever(nextNode.isEditable).thenReturn(true)
            } else {
                // Intermediate node
                whenever(nextNode.childCount).thenReturn(1)
                whenever(nextNode.text).thenReturn("node_$i")
                whenever(nextNode.isEditable).thenReturn(false)
            }
            currentNode = nextNode
        }

        return root
    }
}
