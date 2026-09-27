package com.civinb.danmuji.data.bili.proto

/**
 * 极简 protobuf 解码器：只解析 wire 格式，不需要 .proto 文件和代码生成。
 * B 站把部分直播消息（INTERACT_WORD_V2、SEND_GIFT_V2）以 base64 protobuf 形式放在 JSON 的 `pb` 字段里，
 * 视频弹幕分段接口也返回 protobuf，都用这个解析。
 */
class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {

    /** varint 字段（int32/int64/uint/bool/enum） */
    fun long(field: Int, default: Long = 0): Long = (fields[field]?.firstOrNull() as? Long) ?: default

    fun int(field: Int, default: Int = 0): Int = long(field, default.toLong()).toInt()

    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray

    fun string(field: Int, default: String = ""): String = bytes(field)?.toString(Charsets.UTF_8) ?: default

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    /** repeated 的嵌套消息 */
    fun messages(field: Int): List<ProtoMessage> =
        fields[field].orEmpty().filterIsInstance<ByteArray>().map { parse(it) }

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = HashMap<Int, MutableList<Any>>()
            var pos = 0
            while (pos < data.size) {
                val (tag, p1) = readVarint(data, pos)
                pos = p1
                val field = (tag ushr 3).toInt()
                when ((tag and 7L).toInt()) {
                    0 -> {
                        val (v, p2) = readVarint(data, pos)
                        pos = p2
                        fields.getOrPut(field) { ArrayList() }.add(v)
                    }
                    1 -> { // 64 位定长，这里用不到，跳过
                        pos += 8
                    }
                    2 -> {
                        val (len, p2) = readVarint(data, pos)
                        val l = len.toInt()
                        require(l >= 0 && p2 + l <= data.size) { "bad length" }
                        fields.getOrPut(field) { ArrayList() }.add(data.copyOfRange(p2, p2 + l))
                        pos = p2 + l
                    }
                    5 -> { // 32 位定长，跳过
                        pos += 4
                    }
                    else -> throw IllegalArgumentException("unsupported wire type")
                }
            }
            return ProtoMessage(fields)
        }

        private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var pos = start
            while (true) {
                require(pos < data.size && shift < 64) { "truncated varint" }
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result to pos
        }
    }
}
