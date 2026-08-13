package com.hypershare.db

import com.hypershare.ui.chat.ChatMessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {

    @Test
    fun testChatMessageItemDataModel_orderingAndProperties() {
        val msg1 = ChatMessageItem("id-1", "local", "Hello Device B!", isOutgoing = true, timestamp = 1000L)
        val msg2 = ChatMessageItem("id-2", "peer-b", "Hi Alice!", isOutgoing = false, timestamp = 2000L)

        assertEquals("id-1", msg1.id)
        assertEquals("local", msg1.senderId)
        assertTrue(msg1.isOutgoing)

        assertEquals("id-2", msg2.id)
        assertEquals("peer-b", msg2.senderId)
        assertFalse(msg2.isOutgoing)
        assertTrue(msg2.timestamp > msg1.timestamp)
    }
}
