package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.Global
import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.containsString(value: String): Boolean {
    for (i in 0 until this.length()) {
        if (this.optString(i) == value) return true
    }
    return false
}

fun JSONObject.optJSONArrayToStringList(fieldName: String): List<String> {
    val array = optJSONArray(fieldName) ?: return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until array.length()) {
        val value = array.optString(i, null)
        if (value != null) {
            list.add(value)
        } else {
            Global.getLogger(javaClass).warn("Invalid string at index $i in '$fieldName'")
        }
    }
    return list
}