package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.notification.NativeNotificationBridge
import com.ningmengchang.codexcompanion.notification.ThreadStatusEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNotificationBridgeTest {
    @Test
    fun forwardsSupportedThreadStatusChanges() {
        val events = mutableListOf<ThreadStatusEvent>()
        val bridge = NativeNotificationBridge(events::add)

        bridge.threadStatusChanged("thread-1", "标品设计", "running")
        bridge.threadStatusChanged("thread-1", "标品设计", "completed")

        assertEquals(2, events.size)
        assertEquals("执行中", events[0].statusLabel)
        assertFalse(events[0].shouldAlert)
        assertEquals("已完成", events[1].statusLabel)
        assertTrue(events[1].shouldAlert)
    }

    @Test
    fun ignoresIdleUnknownAndMalformedEvents() {
        val events = mutableListOf<ThreadStatusEvent>()
        val bridge = NativeNotificationBridge(events::add)

        bridge.threadStatusChanged("thread-1", "会话", "idle")
        bridge.threadStatusChanged("thread-1", "会话", "unknown")
        bridge.threadStatusChanged("", "会话", "completed")

        assertTrue(events.isEmpty())
    }
}
