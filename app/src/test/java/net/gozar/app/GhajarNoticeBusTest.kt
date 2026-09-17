package net.gozar.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GhajarNoticeBusTest {
    private fun notice(id: String, important: Boolean = false, service: Boolean = false) =
        GhajarNotice(id, id, "message", important, service)

    @After fun reset() = GhajarNoticeBus.reset()

    @Test fun noticesQueueInsteadOfOverwritingEachOther() {
        GhajarNoticeBus.publish("account", listOf(notice("general"), notice("quota", service = true), notice("important", important = true)), emptySet())
        assertEquals("important", GhajarNoticeBus.notice.value?.id)
        GhajarNoticeBus.dismiss("important")
        assertEquals("quota", GhajarNoticeBus.notice.value?.id)
        GhajarNoticeBus.dismiss("quota")
        assertEquals("general", GhajarNoticeBus.notice.value?.id)
    }

    @Test fun acknowledgementIsRespectedOnRefresh() {
        GhajarNoticeBus.publish("account", listOf(notice("read"), notice("new")), setOf("read"))
        assertEquals("new", GhajarNoticeBus.notice.value?.id)
    }

    @Test fun duplicateNoticeIsNotShownTwice() {
        GhajarNoticeBus.publish("account", listOf(notice("same"), notice("same")), emptySet())
        GhajarNoticeBus.dismiss("same")
        GhajarNoticeBus.publish("account", listOf(notice("same")), emptySet())
        assertNull(GhajarNoticeBus.notice.value)
    }

    @Test fun switchingAccountCannotKeepThePreviousMessage() {
        GhajarNoticeBus.publish("first", listOf(notice("private-first")), emptySet())
        GhajarNoticeBus.publish("second", emptyList(), emptySet())
        assertNull(GhajarNoticeBus.notice.value)
    }
}
