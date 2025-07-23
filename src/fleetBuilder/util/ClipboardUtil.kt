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
            val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
            if (contents?.isDataFlavorSupported(DataFlavor.stringFlavor) == true) {
                contents.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readFileContentsJavaStyle(filePath: String): String? {
        try {
            val builder = StringBuilder()
            val reader = BufferedReader(FileReader(filePath))

            var line: String? = reader.readLine()
            while (line != null) {
                builder.append(line).append("\n")
                line = reader.readLine()
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
        return filePath?.let { readFileContentsJavaStyle(it) }
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
        } catch (e: Exception) {
            //Global.getLogger(this.javaClass).warn("Failed to convert clipboard to json")
        }
        return json
    }
}