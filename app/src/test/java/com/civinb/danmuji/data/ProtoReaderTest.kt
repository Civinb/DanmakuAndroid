package com.civinb.danmuji.data

import com.civinb.danmuji.data.bili.proto.ProtoMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class ProtoReaderTest {
    /** 真实的 INTERACT_WORD_V2 data.pb 样本（bilibili-API-collect 存档 message_stream.md） */
    private val sample = "CJTwwNEBEgpTdGFyU2VhMjQ2IgIDASgBMNWgITispaTDBkDUubHe/jJKLAiv8CkQEhoG55Sf5oCBIKS6ngYopLqeBjCkup4GOKS6ngZAAWDVoCFo9JQRYgB4gZ/v1tmc1qcYmgEAsgHPAQiU8MDRARJYCgpTdGFyU2VhMjQ2EkpodHRwczovL2kwLmhkc2xiLmNvbS9iZnMvZmFjZS8xMDliNzg3YzVmMTEzYzRhM2M3NDE1YmI5YmY2YjgyYmMzM2JjNGUyLmpwZxpnCgbnlJ/mgIEQEhikup4GIKS6ngYopLqeBjCkup4GOP/hAUgBUK/wKWD0lBF6CSNEQzZCNkI5OYIBCSNEQzZCNkI5OYoBCSNEQzZCNkI5OZIBCSNGRkZGRkZGRpoBCSM4MTAwMUY5OSICCAkyALoBAA=="

    @Test
    fun decodeInteractWordV2() {
        val msg = ProtoMessage.parse(Base64.getDecoder().decode(sample))
        assertEquals("StarSea246", msg.string(2))
        assertEquals(1, msg.int(5)) // 1 = 进入直播间
        assertEquals(true, msg.long(1) > 0)
        assertEquals(true, msg.long(7) > 1_600_000_000L) // timestamp
    }
}
