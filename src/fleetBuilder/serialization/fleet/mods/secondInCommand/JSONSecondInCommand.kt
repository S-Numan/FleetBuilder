package fleetBuilder.serialization.fleet.mods.secondInCommand

import fleetBuilder.serialization.person.JSONPerson
import fleetBuilder.util.kotlin.roundToDecimals
import org.json.JSONArray
import org.json.JSONObject

object JSONSecondInCommand {
    fun saveSecondInCommandData(data: DataSecondInCommand.SecondInCommandData, fleetJson: JSONObject) {
        val sicJson = JSONObject()

        val officerJsonArray = JSONArray()
        data.officers.forEach {
            val scOfficerJson = JSONObject()
            scOfficerJson.put("person", JSONPerson.savePersonToJson(it.person))
            scOfficerJson.put("level", it.level)
            scOfficerJson.put("aptitudeId", it.aptitudeId)
            scOfficerJson.put("skillPoints", it.skillPoints)
            scOfficerJson.put("experiencePoints", it.experiencePoints.roundToDecimals(2))
            scOfficerJson.put("activeSkillIDs", JSONArray(it.activeSkillIDs))
            if (it.assignedSlot != null)
                scOfficerJson.put("assignedSlot", it.assignedSlot)

            officerJsonArray.put(scOfficerJson)
        }
        sicJson.put("executiveOfficers", officerJsonArray)

        fleetJson.put("second_in_command", sicJson)
    }

    fun extractSecondInCommandData(sicJson: JSONObject): DataSecondInCommand.SecondInCommandData {
        val officers = mutableListOf<DataSecondInCommand.SecondInCommandOfficerData>()

        val executiveArray = sicJson.optJSONArray("executiveOfficers")
            ?: return DataSecondInCommand.SecondInCommandData(officers)

        for (i in 0 until executiveArray.length()) {
            val obj = executiveArray.optJSONObject(i) ?: continue

            val personJson = obj.optJSONObject("person") ?: continue
            val person = JSONPerson.extractPersonDataFromJson(personJson)

            val aptitudeId = obj.optString("aptitudeId", null) ?: continue
            val skillPoints = obj.optInt("skillPoints", 0)
            val experiencePoints = obj.optDouble("experiencePoints", 0.0).toFloat()

            val skillArray = obj.optJSONArray("activeSkillIDs") ?: JSONArray()
            val activeSkillIDs = mutableListOf<String>()

            val level = obj.optInt("level", 1)

            for (j in 0 until skillArray.length()) {
                val skillId = skillArray.optString(j, null)
                if (!skillId.isNullOrEmpty()) {
                    activeSkillIDs.add(skillId)
                }
            }

            var assignedSlot: Int? = obj.optInt("assignedSlot", -1)
            if (assignedSlot == -1) {
                assignedSlot = null
            }

            officers.add(
                DataSecondInCommand.SecondInCommandOfficerData(
                    person = person,
                    aptitudeId = aptitudeId,
                    skillPoints = skillPoints,
                    experiencePoints = experiencePoints,
                    activeSkillIDs = activeSkillIDs,
                    assignedSlot = assignedSlot,
                    level = level
                )
            )
        }

        return DataSecondInCommand.SecondInCommandData(officers)
    }
}