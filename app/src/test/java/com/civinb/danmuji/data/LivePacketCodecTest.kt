package com.civinb.danmuji.data

import com.civinb.danmuji.data.bili.live.LivePacketCodec
import com.civinb.danmuji.data.bili.live.LivePacketCodec.Packet
import org.brotli.dec.BrotliInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** 测试数据由 Node.js zlib（brotliCompressSync / deflateSync）按 blivedm 的包格式生成。 */
class LivePacketCodecTest {
    private val brotli = LivePacketCodec.BrotliDecoder { BrotliInputStream(it.inputStream()).use { s -> s.readBytes() } }
    private fun b64(s: String) = Base64.getDecoder().decode(s)

    @Test
    fun brotliPacketContainsTwoCommands() {
        val packets = LivePacketCodec.decode(b64("AAABLQAQAAMAAAAFAAAAARu4AQCchbGzIzTy2PwijEaqgwf+XEsf8MPRuK8qqQLM/qanJyuhyCYIL4qk2OxIHZY+KN3qlB69YiFXPmUblCI18vQuQDmPWLqcv92WQcVg11aswPQWreauZqn5woobzuNjKElyTDIko7r0Wv6wPK9AKl4pt6flZtb+j0/PxsTaH5BpNfVQG1NEb416xlqWTMozXyzJEgr7FOoRG41uBLKABawxHrf9ygCRAOY3EB5Mk1/VFeIRxl/CmtLyCCcFm2CZAxgrr4/H2musHTl+KEYVeCH5B3rnw/UCPDdieQU99gsq/1qy0ndcnyS9YjgtVbPTfGX6gXNlhBxwgh09mJ4vWyCGZBkQQ/A2Tv4nkSfdBCwcsMVWrAoyslZTXA=="), brotli)
        assertEquals(2, packets.size)
        assertTrue((packets[0] as Packet.Command).json.contains("你好世界"))
        assertTrue((packets[1] as Packet.Command).json.contains("LOG_IN_NOTICE"))
    }

    @Test
    fun deflatePacket() {
        val packets = LivePacketCodec.decode(b64("AAABZQAQAAIAAAAFAAAAAXicbZBLSwJRFMevi/Z9hv9SzuKOND7uIooKEVIXFQTDIKMzheADdIRimEW7KBct0h4YuUiMVkFE4oO+zDhDq75C3FFw0zlwOP/zO3AejEW22DqTtsYYizgoVU0I7G7nskeF7EFabAguYqFzEMq1kzqEpnFSKKaSEk8kEjFFJSWhJpN8aSQdRrEEWuYygeNiERxYZ3bDgFiUqnXTguCEVtNqQDholc1QF42mJXXNqFoQmE/70Shc19UJ3qw/H8y8UTfotEEapyVdDlTCNRQCdNJ0yTmlkvFkSuWETVVSSYCwQ9Jaq1IhB3YTYnULoWRD4BjuqicMUmlc113G2Pl//9vPpwuZXCGXP8zs7IFgGrYRnlK3yyWrUG2eQsAbjb3vJ//qJbh99S+/fh5vguHF77Tt996Ch8l81vHv+v5Hx38eBL1rb9L1xmP//jMYvsN1/wAg2o4W"), brotli)
        assertEquals(2, packets.size)
    }

    @Test
    fun heartbeatReply() {
        val packets = LivePacketCodec.decode(b64("AAAAFAAQAAEAAAADAAAAAQAAMDl7fQ=="), brotli)
        assertEquals(listOf(Packet.HeartbeatReply(12345)), packets)
    }

    @Test
    fun authReply() {
        val packets = LivePacketCodec.decode(b64("AAAAGgAQAAEAAAAIAAAAAXsiY29kZSI6MH0="), brotli)
        assertEquals(Packet.AuthReply("{\"code\":0}"), packets.single())
    }

    @Test
    fun encodeHeader() {
        val bytes = LivePacketCodec.encodeJson(LivePacketCodec.Op.AUTH, "{}")
        assertEquals(18, bytes.size)
        assertEquals(18, bytes[3].toInt())         // packLen
        assertEquals(16, bytes[5].toInt())         // headerLen
        assertEquals(1, bytes[7].toInt())          // ver
        assertEquals(7, bytes[11].toInt())         // op = AUTH
        assertEquals(1, bytes[15].toInt())         // seq
    }
}
