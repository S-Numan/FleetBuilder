package fleetBuilder.util

import org.json.JSONObject
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

object ClipboardUtil {
    fun getClipboardTextSafe(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null)
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                contents.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (e: Exception) {
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
        val clipboardText = getClipboardTextSafe()
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