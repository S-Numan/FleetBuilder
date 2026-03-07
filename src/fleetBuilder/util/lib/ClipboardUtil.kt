package fleetBuilder.util.lib

import fleetBuilder.serialization.SerializationUtils.getJSONFromStringSafe
import org.json.JSONObject
import org.lwjgl.Sys
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.BufferedReader
import java.io.FileReader

object ClipboardUtil {
    fun getClipboardTextSafe(): String? {
        return try {
            /*val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)// systemClipboard.getContents is really slow
            if (contents?.isDataFlavorSupported(DataFlavor.stringFlavor) == true) {
                contents.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }*/
            Sys.getClipboard()
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
            var insideDoubleQuotes = false
            var prevCharWasEscape = false

            var intChar = reader.read()
            while (intChar != -1) {
                val ch = intChar.toChar()

                // Enforce total character limit
                totalCharsRead++
                if (totalCharsRead > CHAR_LIMIT) {
                    reader.close()
                    return null
                }

                // Handle comment mode
                if (insideComment) {
                    if (ch == '\n') {
                        insideComment = false
                        builder.append(ch)
                    }
                    intChar = reader.read()
                    continue
                }

                // Toggle double quote state
                if (ch == '"' && !prevCharWasEscape) {
                    insideDoubleQuotes = !insideDoubleQuotes
                }

                // Only start comment if not inside quotes
                if (!insideDoubleQuotes && ch == '#') {
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

                prevCharWasEscape = (ch == '\\' && !prevCharWasEscape)
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

    fun setClipboardText(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(text)
            clipboard.setContents(selection, selection)
        } catch (_: Exception) {

        }
    }

    fun getClipboardJson(): JSONObject? {
        val filePath = getClipboardFilePath()
        val contents = filePath?.let { readJSONContentsSafe(it) }

        val clipboardText = contents ?: getClipboardTextSafe() ?: return null
        return getJSONFromStringSafe(clipboardText)
    }
}