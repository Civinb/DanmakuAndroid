package com.civinb.danmuji.data.bili.live

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Inflater

/**
 * 直播信息流 WebSocket 数据包编解码（纯 Kotlin，无网络）。
 *
 * 包头 16 字节，大端：
 *   packLen(4) headerLen(2) ver(2) op(4) seq(4)
 * 依据：blivedm `blivedm/clients/ws_base.py`（2026-08），PiliPlus `lib/tcp/live.dart`。
 */
object LivePacketCodec {

    const val HEADER_SIZE = 16

    object Op {
        const val HEARTBEAT = 2
        const val HEARTBEAT_REPLY = 3
        const val SEND_MSG_REPLY = 5
        const val AUTH = 7
        const val AUTH_REPLY = 8
    }

    object Ver {
        const val NORMAL = 0
        const val HEARTBEAT = 1
        const val DEFLATE = 2
        const val BROTLI = 3
    }

    sealed interface Packet {
        /** 业务消息，JSON 文本（含 cmd 字段） */
        data class Command(val json: String) : Packet

        /** 认证回复，JSON 文本（含 code 字段） */
        data class AuthReply(val json: String) : Packet

        /** 心跳回复，前 4 字节为人气值（已废弃） */
        data class HeartbeatReply(val popularity: Long) : Packet

        data class Unknown(val op: Int, val ver: Int) : Packet
    }

    /** brotli 解压由调用方注入（Android 上用 org.brotli.dec），便于单元测试。 */
    fun interface BrotliDecoder {
        fun decode(input: ByteArray): ByteArray
    }

    fun encode(op: Int, body: ByteArray, ver: Int = Ver.HEARTBEAT, seq: Int = 1): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + body.size)
        buf.putInt(HEADER_SIZE + body.size)
        buf.putShort(HEADER_SIZE.toShort())
        buf.putShort(ver.toShort())
        buf.putInt(op)
        buf.putInt(seq)
        buf.put(body)
        return buf.array()
    }

    fun encodeJson(op: Int, json: String): ByteArray = encode(op, json.toByteArray(Charsets.UTF_8))

    /** 解码一条 WebSocket 二进制消息（可能包含多个包，可能被压缩）。 */
    fun decode(data: ByteArray, brotli: BrotliDecoder): List<Packet> {
        val out = ArrayList<Packet>()
        decodeInto(data, brotli, out, depth = 0)
        return out
    }

    private fun decodeInto(data: ByteArray, brotli: BrotliDecoder, out: MutableList<Packet>, depth: Int) {
        require(depth < 4) { "nested too deep" }
        var offset = 0
        while (offset + HEADER_SIZE <= data.size) {
            val bb = ByteBuffer.wrap(data, offset, HEADER_SIZE)
            val packLen = bb.int
            val headerLen = bb.short.toInt() and 0xFFFF
            val ver = bb.short.toInt() and 0xFFFF
            val op = bb.int
            if (packLen < headerLen || headerLen < HEADER_SIZE) break

            if (op == Op.HEARTBEAT_REPLY) {
                // blivedm 注释：服务器心跳回复的 packLen 不包括客户端心跳内容，按前 4 字节取人气值后结束
                val start = offset + headerLen
                val pop = if (start + 4 <= data.size) {
                    ByteBuffer.wrap(data, start, 4).int.toLong() and 0xFFFFFFFFL
                } else {
                    0L
                }
                out += Packet.HeartbeatReply(pop)
                return
            }

            val end = offset + packLen
            if (end > data.size) break
            val body = data.copyOfRange(offset + headerLen, end)
            when (op) {
                Op.SEND_MSG_REPLY -> when (ver) {
                    Ver.BROTLI -> decodeInto(brotli.decode(body), brotli, out, depth + 1)
                    Ver.DEFLATE -> decodeInto(inflate(body), brotli, out, depth + 1)
                    Ver.NORMAL -> if (body.isNotEmpty()) out += Packet.Command(String(body, Charsets.UTF_8))
                    else -> out += Packet.Unknown(op, ver)
                }
                Op.AUTH_REPLY -> out += Packet.AuthReply(String(body, Charsets.UTF_8))
                else -> out += Packet.Unknown(op, ver)
            }
            offset = end
        }
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(input)
            val bos = ByteArrayOutputStream(input.size * 4)
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                bos.write(buf, 0, n)
            }
            return bos.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
