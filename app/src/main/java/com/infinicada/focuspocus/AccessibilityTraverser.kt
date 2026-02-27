package com.infinicada.focuspocus

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object AccessibilityTraverser {

    private const val MAX_TREE_DEPTH = 10
    private const val MAX_VISITED_NODES = 80
    private const val MAX_CHILDREN_TO_PROCESS = 10

    private data class NodeState(val node: AccessibilityNodeInfo, var nextChildIndex: Int, val depth: Int)

    fun findUrlInNodeTree(rootNode: AccessibilityNodeInfo, initialDepth: Int): String? {
        val stack = ArrayDeque<NodeState>()
        stack.push(NodeState(rootNode, 0, initialDepth))

        var visitedCount = 0

        while (stack.isNotEmpty()) {
            // Safety break if we traverse too many nodes
            if (visitedCount >= MAX_VISITED_NODES) {
                // Recycle remaining nodes in stack
                while (stack.isNotEmpty()) {
                    val s = stack.pop()
                    if (s.node != rootNode) s.node.recycle()
                }
                break
            }

            val state = stack.peek() ?: break
            val node = state.node
            val depth = state.depth

            // Check node itself (only once, when nextChildIndex == 0)
            if (state.nextChildIndex == 0) {
                visitedCount++

                if (depth > MAX_TREE_DEPTH) {
                    stack.pop()
                    if (node != rootNode) node.recycle()
                    continue
                }

                val text = node.text?.toString()
                if (text != null && node.isEditable && UrlUtils.looksLikeUrl(text)) {
                    val result = text
                    // Cleanup stack: recycle all nodes except rootNode
                    while (stack.isNotEmpty()) {
                        val s = stack.pop()
                        if (s.node != rootNode) s.node.recycle()
                    }
                    return result
                }
            }

            // Get next child
            // Limit width: only process up to MAX_CHILDREN_TO_PROCESS children
            if (state.nextChildIndex < node.childCount && state.nextChildIndex < MAX_CHILDREN_TO_PROCESS) {
                val i = state.nextChildIndex
                state.nextChildIndex++

                val child = node.getChild(i)
                if (child != null) {
                    stack.push(NodeState(child, 0, depth + 1))
                }
            } else {
                // Done with this node
                stack.pop()
                if (node != rootNode) node.recycle()
            }
        }
        return null
    }
}
