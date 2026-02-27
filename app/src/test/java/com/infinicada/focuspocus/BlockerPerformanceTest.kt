package com.infinicada.focuspocus

import org.junit.Test
import kotlin.system.measureNanoTime

class BlockerPerformanceTest {

    @Test
    fun benchmarkBlockerLookup() {
        val appCount = 10000
        val lookupCount = 100000
        val apps = (1..appCount).map { "com.example.app$it" }
        val blocker = Blocker("Benchmark", BlockerMode.BLACKLIST, apps.toSet())

        // Warmup
        repeat(1000) {
            blocker.shouldBlock("com.example.app1")
        }

        val time = measureNanoTime {
            repeat(lookupCount) { i ->
                val packageName = if (i % 2 == 0) {
                    "com.example.app${(i % appCount) + 1}" // Hit
                } else {
                    "com.example.nonexistent$i" // Miss
                }
                blocker.shouldBlock(packageName)
            }
        }

        println("Time taken for $lookupCount lookups: ${time / 1_000_000} ms")
    }

}
