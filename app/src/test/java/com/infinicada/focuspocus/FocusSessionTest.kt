package com.infinicada.focuspocus

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FocusSessionTest {

    @Test
    fun testSerializationAndDeserialization() {
        val originalSession = FocusSession(
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 16,
            blockerName = "Social Media",
            breaksUsed = 2
        )

        val json = AppJson.json.encodeToString(originalSession)
        val deserializedSession = AppJson.json.decodeFromString<FocusSession>(json)

        assertEquals(originalSession, deserializedSession)
        assertEquals(originalSession.startTimeMillis, deserializedSession.startTimeMillis)
        assertEquals(originalSession.endTimeMillis, deserializedSession.endTimeMillis)
        assertEquals(originalSession.durationMinutes, deserializedSession.durationMinutes)
        assertEquals(originalSession.blockerName, deserializedSession.blockerName)
        assertEquals(originalSession.breaksUsed, deserializedSession.breaksUsed)
    }

    @Test
    fun testSerializationStructure() {
        val session = FocusSession(
            startTimeMillis = 123456789L,
            endTimeMillis = 987654321L,
            durationMinutes = 60,
            blockerName = "Work",
            breaksUsed = 0
        )

        val json = AppJson.json.encodeToString(session)

        assert(json.contains("\"startTimeMillis\":123456789"))
        assert(json.contains("\"endTimeMillis\":987654321"))
        assert(json.contains("\"durationMinutes\":60"))
        assert(json.contains("\"blockerName\":\"Work\""))
        assert(json.contains("\"breaksUsed\":0"))
    }

    @Test
    fun testEdgeCases() {
        val session = FocusSession(
            startTimeMillis = Long.MAX_VALUE,
            endTimeMillis = Long.MIN_VALUE,
            durationMinutes = Int.MAX_VALUE,
            blockerName = "",
            breaksUsed = Int.MIN_VALUE
        )

        val json = AppJson.json.encodeToString(session)
        val deserializedSession = AppJson.json.decodeFromString<FocusSession>(json)

        assertEquals(session, deserializedSession)
        assertEquals(Long.MAX_VALUE, deserializedSession.startTimeMillis)
        assertEquals(Long.MIN_VALUE, deserializedSession.endTimeMillis)
        assertEquals(Int.MAX_VALUE, deserializedSession.durationMinutes)
        assertEquals("", deserializedSession.blockerName)
        assertEquals(Int.MIN_VALUE, deserializedSession.breaksUsed)
    }

    @Test
    fun testInequality() {
        val session1 = FocusSession(100L, 200L, 10, "A", 0)
        val session2 = FocusSession(100L, 200L, 10, "B", 0)
        assertNotEquals(session1, session2)
    }
}
