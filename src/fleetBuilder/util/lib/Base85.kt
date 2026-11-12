package fleetBuilder.util.lib

import kotlin.math.pow

object Base85 {
    private data class Block(val a: UInt, val b: UInt, val c: UInt, val d: UInt, val e: UInt)

    fun encode(arr: ByteArray): String {
        var final = ""
        var i = 0
        while (i < arr.size) {
            // handle up to 4 bytes at a time
            val remaining = arr.size - i
            val b0 = if (remaining > 0) arr[i].toInt() and 0xFF else 0
            val b1 = if (remaining > 1) arr[i + 1].toInt() and 0xFF else 0
            val b2 = if (remaining > 2) arr[i + 2].toInt() and 0xFF else 0
            val b3 = if (remaining > 3) arr[i + 3].toInt() and 0xFF else 0

            val uInt = ((b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3).toUInt()
            val block = getBlock(uInt)

            // always produce 5 chars but later trim for padding
            val encoded = buildString {
                append(block.a.toInt().toChar())
                append(block.b.toInt().toChar())
                append(block.c.toInt().toChar())
                append(block.d.toInt().toChar())
                append(block.e.toInt().toChar())
            }

            // Ascii85 spec: if fewer than 4 bytes input, we output fewer than 5 chars
            val charsToTake = when (remaining) {
                1 -> 2
                2 -> 3
                3 -> 4
                else -> 5
            }
            final += encoded.take(charsToTake)

            i += 4
        }
        return final
    }

    fun decode(str: String): ByteArray {
        val out = mutableListOf<Byte>()
        var i = 0
        while (i < str.length) {
            // handle up to 5 chars at a time
            val remaining = str.length - i
            val c0 = if (remaining > 0) str[i].code - 33 else 84
            val c1 = if (remaining > 1) str[i + 1].code - 33 else 84
            val c2 = if (remaining > 2) str[i + 2].code - 33 else 84
            val c3 = if (remaining > 3) str[i + 3].code - 33 else 84
            val c4 = if (remaining > 4) str[i + 4].code - 33 else 84

            val v = (c0 * 85.0.pow(4)) + (c1 * 85.0.pow(3)) + (c2 * 85.0.pow(2)) + (c3 * 85.0) + c4
            val vStr = v.toLong().to32bitString()

            val bytesToTake = when (remaining) {
                2 -> 1
                3 -> 2
                4 -> 3
                else -> 4
            }
            for (j in 0 until bytesToTake) {
                val b = Integer.parseInt(vStr.substring(j * 8, (j + 1) * 8), 2).toByte()
                out.add(b)
            }

            i += 5
        }
        return out.toByteArray()
    }

    private fun getBlock(uInt: UInt): Block {
        var n = uInt
        val base = 85u
        val add = 33u
        val e = n % base + add
        n /= base
        val d = n % base + add
        n /= base
        val c = n % base + add
        n /= base
        val b = n % base + add
        n /= base
        val a = n % base + add
        return Block(a, b, c, d, e)
    }

    private fun Long.to32bitString(): String =
        java.lang.Long.toBinaryString(this).padStart(Int.SIZE_BITS, '0')
}