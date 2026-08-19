package com.pranav.drsti

import com.pranav.drsti.util.HashUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HashUtilTest {
    @Test
    fun `same input produces same hash`() {
        assertEquals(HashUtil.sha256("hello"), HashUtil.sha256("hello"))
    }

    @Test
    fun `different input produces different hash`() {
        assertNotEquals(HashUtil.sha256("hello"), HashUtil.sha256("world"))
    }

    @Test
    fun `hash is 64 hex characters`() {
        assertEquals(64, HashUtil.sha256("dristi").length)
    }
}
