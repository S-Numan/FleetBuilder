package fleetBuilder.util

import org.json.JSONObject
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.BufferedReader
import java.io.FileReader

object ClipboardUtil {
    fun getClipboardTextSafe(): String? {
        return try {
            val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)// systemClipboard.getContents is really slow
            if (contents?.isDataFlavorSupported(DataFlavor.stringFlavor) == true) {
                contents.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    const val CHAR_LIMIT = 1_000_000
    private fun readJSONContentsSafe(filePath: String): String? {
        try {
            val reader = BufferedReader(FileReader(filePath))
            val builder = StringBuilder()
            var totalCharsRead = 0
            var firstSignificantCharFound = false
            var insideComment = false

            // This is done char by char instead of line by line to avoid crashing the game from reading excessively large files
            var intChar = reader.read()
            while (intChar != -1) {
                val ch = intChar.toChar()

                // Enforce total character limit
                totalCharsRead++
                if (totalCharsRead > CHAR_LIMIT) {
                    reader.close()
                    return null
                }

                // Handle # comments (ignore until newline)
                if (insideComment) {
                    if (ch == '\n') {
                        insideComment = false
                        builder.append(ch)
                    }
                    intChar = reader.read()
                    continue
                }

                if (ch == '#') {
                    insideComment = true
                    intChar = reader.read()
                    continue
                }

                // Validate first significant char
                if (!firstSignificantCharFound && !ch.isWhitespace() && ch != '\n') {
                    if (ch != '{') {
                        reader.close()
                        return null
                    }
                    firstSignificantCharFound = true
                }

                builder.append(ch)
                intChar = reader.read()
            }

            reader.close()
            return builder.toString()
        } catch (_: Exception) {
            return null
        }
    }

    fun getClipboardFilePath(): String? {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard

        return try {
            val contents = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>
            contents?.firstNotNullOfOrNull { it?.toString() }
        } catch (_: Exception) {
            null
        }
    }

    fun getClipboardFileContents(): String? {
        val filePath = getClipboardFilePath()
        return filePath?.let { readJSONContentsSafe(it) }
    }

    fun setClipboardText(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(text)
            clipboard.setContents(selection, selection)
        } catch (_: Exception) {

        }
    }

    fun getClipboardJson(): JSONObject? {
        val contents = getClipboardFileContents()

        val clipboardText = contents ?: getClipboardTextSafe()

        if (clipboardText.isNullOrEmpty()) return null

        var json: JSONObject? = null
        try {
            json = JSONObject(clipboardText)
        } catch (_: Exception) {
            //Global.getLogger(this.javaClass).warn("Failed to convert clipboard to json")
        }
        return json
    }
}