package fleetBuilder.persistence.fleet

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import fleetBuilder.persistence.person.PersonSerialization
import fleetBuilder.persistence.person.PersonSerialization.buildPerson
import fleetBuilder.persistence.person.PersonSerialization.validateAndCleanPersonData
import fleetBuilder.variants.MissingElements
import org.json.JSONArray
import org.json.JSONObject
import second_in_command.SCData
import second_in_command.SCUtils
import second_in_command.specs.SCOfficer
import second_in_command.specs.SCSpecStore
import starficz.ReflectionUtils.set

object SecondInCommandSerialization {

    data class SecondInCommandData(
        val officers: MutableList<SecondInCommandOfficerData>
    )

    data class SecondInCommandOfficerData(
        var person: PersonSerialization.ParsedPersonData,
        val aptitudeId: String,
        val skillPoints: Int,
        val experiencePoints: Float,
        val activeSkillIDs: List<String>,
        val assignedSlot: Int? = null,
        val level: Int
    )

    fun saveSecondInCommandData(campFleet: CampaignFleetAPI, fleetJson: JSONObject) {
        val storedData = campFleet.memoryWithoutUpdate.get(SCUtils.FLEET_DATA_KEY) as? SCData
        if (storedData != null) {
            val sicJson = JSONObject()

            val officerJsonArray = JSONArray()
            storedData.getOfficersInFleet().forEach {
                val assignedSlot = storedData.getOfficersAssignedSlot(it)

                val scOfficerJson = JSONObject()
                scOfficerJson.put("person", PersonSerialization.savePersonToJson(it.person))
                scOfficerJson.put("level", it.getCurrentLevel())
                scOfficerJson.put("aptitudeId", it.aptitudeId)
                scOfficerJson.put("skillPoints", it.skillPoints)
                scOfficerJson.put("experiencePoints", it.getExperiencePoints())
                scOfficerJson.put("activeSkillIDs", JSONArray(it.activeSkillIDs))
                if (assignedSlot != null)
                    scOfficerJson.put("assignedSlot", assignedSlot)

                officerJsonArray.put(scOfficerJson)
            }
            sicJson.put("executiveOfficers", officerJsonArray)

            fleetJson.put("second_in_command", sicJson)
        }
    }

    fun extractSecondInCommandData(sicJson: JSONObject): SecondInCommandData {
        val officers = mutableListOf<SecondInCommandOfficerData>()

        val executiveArray = sicJson.optJSONArray("executiveOfficers") ?: return SecondInCommandData(officers)

        for (i in 0 until executiveArray.length()) {
            val obj = executiveArray.optJSONObject(i) ?: continue

            val personJson = obj.optJSONObject("person") ?: continue
            val person = PersonSerialization.extractPersonDataFromJson(personJson)

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
                SecondInCommandOfficerData(
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

        return SecondInCommandData(officers)
    }

    fun validateSecondInCommandData(sicData: SecondInCommandData, missing: MissingElements): SecondInCommandData {
        sicData.officers.toList().forEach { sicOfficer ->

            if (SCSpecStore.getAptitudeSpec(sicOfficer.aptitudeId) == null) {
                missing.skillIds.add(sicOfficer.aptitudeId)
                sicData.officers.remove(sicOfficer)
                return@forEach
            }

            sicOfficer.activeSkillIDs.forEach { skillId ->
                if (SCSpecStore.getSkillSpec(skillId) == null) {
                    missing.skillIds.add(skillId)
                    sicData.officers.remove(sicOfficer)
                    return@forEach
                }
            }

            sicOfficer.person = validateAndCleanPersonData(sicOfficer.person, missing)
        }

        return sicData
    }

    fun buildSecondInCommandData(sicData: SecondInCommandData, fleet: CampaignFleetAPI) {
        val storedData = fleet.memoryWithoutUpdate.get(SCUtils.FLEET_DATA_KEY) as? SCData ?: return

        storedData.getOfficersInFleet().toList().forEach { storedOfficer ->
            storedData.removeOfficerFromFleet(storedOfficer)
        }

        sicData.officers.forEach { sicOfficer ->
            val person = sicOfficer.person
            val aptitudeId = sicOfficer.aptitudeId
            val skillPoints = sicOfficer.skillPoints
            val level = sicOfficer.level
            val experiencePoints = sicOfficer.experiencePoints
            val activeSkillIDs = sicOfficer.activeSkillIDs
            val assignedSlot = sicOfficer.assignedSlot

            val scOfficer = SCOfficer(buildPerson(person), aptitudeId)
            activeSkillIDs.forEach { scOfficer.addSkill(it) }
            scOfficer.increaseLevel(level - scOfficer.getCurrentLevel())
            scOfficer.skillPoints = skillPoints
            scOfficer.set("experiencePoints", experiencePoints)


            storedData.addOfficerToFleet(scOfficer)
            if (assignedSlot != null)
                storedData.setOfficerInSlot(assignedSlot, scOfficer)
        }
    }
}