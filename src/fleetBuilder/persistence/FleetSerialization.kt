package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings.commandShuttleId
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.MemberSerialization.setMemberOfficerFromJson
import fleetBuilder.persistence.MemberSerialization.setMemberValuesFromJson
import fleetBuilder.persistence.PersonSerialization.getPersonFromJson
import fleetBuilder.persistence.PersonSerialization.getPersonFromJsonWithMissing
import fleetBuilder.persistence.PersonSerialization.savePersonToJson
import fleetBuilder.persistence.VariantSerialization.addVariantSourceModsToJson
import fleetBuilder.persistence.VariantSerialization.getVariantFromJsonWithMissing
import fleetBuilder.util.MISC.createErrorVariant
import fleetBuilder.util.MISC.getMissingFromModInfo
import fleetBuilder.util.MISC.showError
import fleetBuilder.variants.MissingElements
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random


object FleetSerialization {

    //Adds the fleet to the inputted fleet
    @JvmOverloads
    fun getFleetFromJson(
        json: JSONObject,
        campFleet: CampaignFleetAPI,
        includeOfficers: Boolean = true,
        includeCommander: Boolean = true,
        includeNoOfficerPersonality: Boolean = true,
        includeIdleOfficers: Boolean = false,
        setFlagship: Boolean = true,
    ): MissingElements {
        val missingElements = MissingElements()
        getMissingFromModInfo(json, missingElements)

        val fleet = campFleet.fleetData
        if (fleet == null) {
            showError("A campaign fleet's fleetData was null when getting a fleet from json.")
            return missingElements
        }

        campFleet.name = json.optString("fleetName", "Nameless fleet")

        val aggressionDoctrine = json.optInt("aggression_doctrine", 2)

        val fleetVariants = mutableListOf<ShipVariantAPI>()

        json.optJSONArray("variants")?.let { variants ->
            for (i in 0 until variants.length()) {
                variants.optJSONObject(i)?.let { variantJson ->
                    val (variant, missing) = getVariantFromJsonWithMissing(variantJson)

                    fleetVariants.add(variant)
                    missingElements.add(missing)
                }
            }
        }

        json.optJSONObject("commander")?.let { commanderJson ->
            val (commander, personMissing) = getPersonFromJsonWithMissing(commanderJson)
            missingElements.add(personMissing)

            if (includeCommander)
                campFleet.commander = commander

            // Flagship/Commander's ship
            commanderJson.optJSONObject("member")?.let { memberJson ->
                val commanderVariantId = memberJson.optString("variantId", "")

                val matchingVariant = fleetVariants.firstOrNull { it.hullVariantId == commanderVariantId }
                    ?: if (Global.getSettings().doesVariantExist(commanderVariantId)) {
                        Global.getSettings().getVariant(commanderVariantId)
                    } else {
                        showError("Failed to find variant id of $commanderVariantId")
                        createErrorVariant("VariantIDNotFound")
                    }

                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, matchingVariant.clone())
                setMemberValuesFromJson(memberJson, member)

                fleet.addFleetMember(member)

                if (includeOfficers) {
                    fleet.addOfficer(commander)
                    member.captain = commander
                }
                //Ensure only one flagship. Also sets the flagship, because .setFlagship(true) does not work for some reason
                if (setFlagship) {
                    fleet.membersListWithFightersCopy.forEach {
                        it.isFlagship = (it.captain === member.captain)
                    }
                }

            }
        }


        // Other fleet members
        json.optJSONArray("members")?.let { members ->
            for (i in 0 until members.length()) {
                members.optJSONObject(i)?.let { memberJson ->
                    val variantId = memberJson.optString("variantId", "")

                    val matchingVariant = fleetVariants.firstOrNull { it.hullVariantId == variantId }
                        ?: if (Global.getSettings().doesVariantExist(variantId)) {
                            Global.getSettings().getVariant(variantId)
                        } else {
                            showError("Failed to find variant id of $variantId")
                            createErrorVariant("VariantIDNotFound")
                        }

                    val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, matchingVariant.clone())
                    setMemberValuesFromJson(memberJson, member)

                    fleet.addFleetMember(member)

                    if (includeOfficers)
                        setMemberOfficerFromJson(memberJson, member, missingElements)

                    member.captain?.let {
                        if (!it.isAICore) {
                            if (it.isDefault) {
                                if (includeNoOfficerPersonality//Assign doctrinal aggression to non officered ships
                                    && Global.getSector().playerFleet !== fleet.fleet//Don't do this to the player's fleet. The player faction's aggresion doctrine should be used instead.
                                ) {
                                    val personality = when (aggressionDoctrine) {
                                        1 -> Personalities.CAUTIOUS
                                        2 -> Personalities.STEADY
                                        3 -> Personalities.AGGRESSIVE
                                        4 -> if (Random().nextBoolean()) Personalities.AGGRESSIVE else Personalities.RECKLESS
                                        5 -> Personalities.RECKLESS
                                        else -> Personalities.STEADY // fallback/default
                                    }
                                    it.setPersonality(personality)
                                }
                            } else if (includeOfficers) {
                                fleet.addOfficer(it)
                            }
                        }
                    }
                }
            }
        }

        // Idle officers
        if (includeIdleOfficers) {
            json.optJSONArray("idleOfficers")?.let { officers ->
                for (i in 0 until officers.length()) {
                    officers.optJSONObject(i)?.let { officerJson ->
                        try {
                            val (officer, personMissing) = getPersonFromJsonWithMissing(officerJson)
                            missingElements.add(personMissing)

                            fleet.addOfficer(officer)
                        } catch (e: Exception) {
                            showError("Error parsing idle officer at index $i", e)
                        }
                    }
                }
            }
        }

        fleet.syncIfNeeded()

        return missingElements
    }


    data class FleetSettings(
        var includeCommander: Boolean = true, // If a PersonAPI is set as a commander of the fleet.
        var includeCommanderAsOfficer: Boolean = true, // If a PersonAPI is set as an officer in the fleet.
        var includeIdleOfficers: Boolean = false,
        var memberSettings: MemberSerialization.MemberSettings = MemberSerialization.MemberSettings(),
    )

    @JvmOverloads
    fun saveFleetToJson(
        campFleet: CampaignFleetAPI,
        settings: FleetSettings = FleetSettings(),
        includeModInfo: Boolean = true,
    ): JSONObject {

        val fleetJson = JSONObject()

        val fleet = campFleet.fleetData
        if (fleet == null) {
            showError("A campaign fleet's fleetData was null when saving a fleet from json.")
            return fleetJson
        }

        val membersJson = JSONArray()
        val variantsJson = JSONArray()
        val usedVariantIds = mutableSetOf<String>()
        val variantIdMap = mutableMapOf<String, String>() // variant JSON string -> unique variantId

        var commanderJson: JSONObject? = null

        for (member in fleet.membersListCopy) {
            if (includeModInfo)
                addVariantSourceModsToJson(member.variant, fleetJson, settings.memberSettings.variantSettings)

            val isCommander = member.captain?.id == fleet.commander?.id

            val includeOfficer = when {
                !settings.memberSettings.includeOfficer -> false
                isCommander && !settings.includeCommanderAsOfficer -> false//Don't include officer if the officer is the commander and we aren't including the commander as an officer
                else -> true
            }

            val memberJson = saveMemberToJson(
                member,
                settings.memberSettings.copy().apply { this.includeOfficer = includeOfficer },
                includeModInfo = false
            )

            val variant = memberJson.optJSONObject("variant")
                ?: throw Exception("Failed to read variant from json after just creating it")


            // Base variantId before any modification
            val baseVariantId = variant.optString("variantId", "variant")

            // Remove the "variantId" field before stringifying the variant for comparison
            val variantWithoutId = variant.apply { remove("variantId") }
            val variantString = variantWithoutId.toString()


            // Check if this variant (excluding 'variantId') has been encountered before
            val uniqueVariantId = variantIdMap.getOrPut(variantString) {
                // This block only runs if it's a new variant (i.e., the variant is not in the map)
                var newId = baseVariantId
                var counter = 1
                // Ensure that we create a unique ID for the variant
                while (!usedVariantIds.add(newId)) {
                    newId = "${baseVariantId}_$counter"
                    counter++
                }

                // Set the new unique variantId
                variant.put("variantId", newId)
                // Add this variant to the variantsJson array
                variantsJson.put(variant)
                newId
            }

            // Remove the "variant" object from the memberJson and set the unique variantId
            memberJson.remove("variant")
            memberJson.put("variantId", uniqueVariantId)

            if (isCommander && settings.includeCommander && settings.includeCommanderAsOfficer) {
                commanderJson = savePersonToJson(
                    fleet.commander,
                    settings.memberSettings.personSettings
                )

                if (!member.variant.hasHullMod(commandShuttleId))
                    commanderJson.put("member", memberJson)

            } else {
                membersJson.put(memberJson)
            }
        }
        if (settings.includeCommander) {
            if (commanderJson == null) {
                commanderJson = savePersonToJson(
                    fleet.commander,
                    settings.memberSettings.personSettings
                )
            }

            fleetJson.put("commander", commanderJson)
        }


        fleetJson.put("members", membersJson)
        fleetJson.put("variants", variantsJson)
        fleetJson.put("aggression_doctrine", campFleet.faction.doctrine.aggression)

        if (settings.includeIdleOfficers) {
            val idleOfficers = fleet.officersCopy.mapNotNull { officerData ->
                val person = officerData.person
                if (!person.isDefault && person.id != fleet.commander.id && fleet.getMemberWithCaptain(person) == null) {
                    savePersonToJson(person, settings.memberSettings.personSettings)
                } else null
            }
            fleetJson.put("idleOfficers", JSONArray(idleOfficers))
        }

        fleetJson.put("fleetName", campFleet.name)

        return fleetJson
    }

    /*
    @JvmOverloads
    fun saveFleetToJson(fleet: FleetDataAPI, includeOfficers: Boolean = true, includeCommander: Boolean = true, includeOfficerLevelingStats: Boolean = true, includeIdleOfficers: Boolean = false): JSONObject {
        val memberList = mutableListOf<JSONObject>()
        val fleetJson = JSONObject()

        var commanderJson: JSONObject? = null
        if(includeCommander) {
            commanderJson = saveOfficerToJson(fleet.commander, storeLevelingStats = includeOfficerLevelingStats)
        }

        fleet.membersListCopy.forEach { member ->
            if(commanderJson != null && member.captain.id == fleet.commander.id) {
                commanderJson.put("ship", saveMemberToJson(member, includeOfficer = false, includeOfficerLevelingStats = includeOfficerLevelingStats))
            } else {
                val memberJson = saveMemberToJson(member, includeOfficer = includeOfficers, includeOfficerLevelingStats = includeOfficerLevelingStats)
                memberList.add(memberJson)
            }
        }
        if(commanderJson != null)
            fleetJson.put("commander", commanderJson)


        fleetJson.put("aggression", fleet.fleet.faction.doctrine.aggression)

        if(includeIdleOfficers) {
            val idleOfficerList = mutableListOf<JSONObject>()

            for (officerData in fleet.officersCopy) {
                val person = officerData.person
                if (person.isDefault || person.id == fleet.commander.id) continue

                val member = fleet.getMemberWithCaptain(person)//This iteration's officer
                if (member == null) {//This officer is most likely not assigned to a ship if member was null
                        idleOfficerList.add(saveOfficerToJson(person))
                }
            }

            val idleOfficerJson = JSONArray(idleOfficerList)
            fleetJson.put("idleOfficers", idleOfficerJson)
        }

        val membersJson = JSONArray()
        val variantsJson = JSONArray()
        val usedVariantIds = mutableSetOf<String>()
        val variantIdMap = mutableMapOf<String, String>() // variant JSON string -> unique variantId

        memberList.forEach { member ->
            val variant = member.optJSONObject("variant") ?: return@forEach
            val baseVariantId = variant.optString("variantId", "variant")
            val variantString = variant.toString()

            // Skip if already handled
            val uniqueVariantId = variantIdMap.getOrPut(variantString) {
                // Ensure unique variantId
                var newId = baseVariantId
                var counter = 1
                while (usedVariantIds.contains(newId)) {
                    newId = "${baseVariantId}_$counter"
                    counter++
                }
                usedVariantIds.add(newId)

                variant.put("variantId", newId)
                variantsJson.put(variant)
                newId
            }

            member.remove("variant")
            member.put("variantId", uniqueVariantId)
            membersJson.put(member)
        }

        fleetJson.put("members", membersJson)
        fleetJson.put("variants", variantsJson)
        return fleetJson
    }*/

}