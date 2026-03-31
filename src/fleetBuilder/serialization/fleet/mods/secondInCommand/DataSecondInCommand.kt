package fleetBuilder.serialization.fleet.mods.secondInCommand

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.otherMods.starficz.ReflectionUtils.set
import fleetBuilder.serialization.MissingContent
import fleetBuilder.serialization.person.DataPerson
import second_in_command.SCData
import second_in_command.SCUtils
import second_in_command.specs.SCOfficer
import second_in_command.specs.SCSpecStore
import java.util.*

object DataSecondInCommand {

    data class SecondInCommandData(
        val officers: MutableList<SecondInCommandOfficerData>
    )

    data class SecondInCommandOfficerData(
        var person: DataPerson.ParsedPersonData,
        val aptitudeId: String,
        val skillPoints: Int,
        val experiencePoints: Float,
        val activeSkillIDs: List<String>,
        val assignedSlot: Int? = null,
        val level: Int
    )

    fun getSecondInCommandDataFromFleet(
        campFleet: CampaignFleetAPI
    ): SecondInCommandData? {
        val storedData = campFleet.memoryWithoutUpdate.get(SCUtils.FLEET_DATA_KEY) as? SCData ?: return null

        val scData = SecondInCommandData(
            storedData.getOfficersInFleet().map { scOfficer ->
                SecondInCommandOfficerData(
                    person = DataPerson.getPersonDataFromPerson(scOfficer.person, filterParsed = false),
                    aptitudeId = scOfficer.aptitudeId,
                    skillPoints = scOfficer.skillPoints,
                    experiencePoints = scOfficer.getExperiencePoints(),
                    activeSkillIDs = scOfficer.activeSkillIDs.toList(),
                    assignedSlot = storedData.getOfficersAssignedSlot(scOfficer),
                    level = scOfficer.getCurrentLevel()
                )
            }.toMutableList()
        )

        return scData
    }

    fun validateSecondInCommandData(
        sicData: SecondInCommandData,
        missing: MissingContent,
        random: Random
    ): SecondInCommandData {
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

            sicOfficer.person = DataPerson.validateAndCleanPersonData(sicOfficer.person, missing, random)
        }

        return sicData
    }

    fun buildSecondInCommandData(sicData: SecondInCommandData, fleet: CampaignFleetAPI, random: Random) {
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

            val scOfficer = SCOfficer(DataPerson.buildPerson(person, random), aptitudeId)
            activeSkillIDs.forEach { scOfficer.addSkill(it) }
            scOfficer.increaseLevel(level - scOfficer.getCurrentLevel())
            scOfficer.skillPoints = skillPoints
            try {
                scOfficer.set("experiencePoints", experiencePoints)
            } catch (_: Exception) {
                DisplayMessage.showError("Failed to set Second In Command experience points")
            }

            storedData.addOfficerToFleet(scOfficer)
            if (assignedSlot != null)
                storedData.setOfficerInSlot(assignedSlot, scOfficer)
        }
    }
}