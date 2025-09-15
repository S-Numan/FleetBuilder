package fleetBuilder.util.lib

import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

object CompressionUtil {
    const val BLOCK_SIZE = 1024

    fun compressString(input: String): String {
        val data = input.toByteArray(Charsets.UTF_8)
        val fullOutput = mutableListOf<ByteArray>()
        var result: ByteArray
        val compresser = Deflater()
        compresser.setInput(data)
        compresser.finish()
        var compressedDataLength: Int

        while (true) {
            result = ByteArray(BLOCK_SIZE)
            compressedDataLength = compresser.deflate(result)
            if (compressedDataLength == BLOCK_SIZE) {
                fullOutput.add(result)
            } else {
                break
            }
        }
        compresser.end()

        val output = ByteArray(compressedDataLength + BLOCK_SIZE * fullOutput.size)
        for ((index, chunk) in fullOutput.withIndex()) {
            System.arraycopy(chunk, 0, output, index * BLOCK_SIZE, BLOCK_SIZE)
        }
        System.arraycopy(result, 0, output, fullOutput.size * BLOCK_SIZE, compressedDataLength)

        val base64 = Base64.getEncoder().encodeToString(output)
        return base64
    }

    fun decompressString(input: String): String? {
        try {
            val compressed = Base64.getDecoder().decode(input)
            val fullOutput = mutableListOf<ByteArray>()
            var result: ByteArray
            val decompresser = Inflater()
            decompresser.setInput(compressed)
            var resultLength: Int

            while (true) {
                result = ByteArray(BLOCK_SIZE)
                resultLength = decompresser.inflate(result)
                if (resultLength == BLOCK_SIZE) {
                    fullOutput.add(result)
                } else {
                    break
                }
            }
            decompresser.end()

            val builder = StringBuilder()
            for (bytes in fullOutput) {
                builder.append(String(bytes, Charsets.UTF_8))
            }
            builder.append(String(result, 0, resultLength, Charsets.UTF_8))

            return builder.toString()
        } catch (_: Exception) {
            return null
        }
    }
}