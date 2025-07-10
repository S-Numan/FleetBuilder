package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import fleetBuilder.config.ModSettings.commandShuttleId
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.MemberSerialization.setMemberValuesFromJson
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
import java.util.*


object FleetSerialization {

    /**
     * Settings for [saveFleetToJson] and [getFleetFromJson].
     * @param includeCommanderSetFlagship whether to set the PersonAPI commander as the fleet's commander.
     *
     * If the commander is assigned as a captain of a ship in the fleet, that ship will also be set as the flagship.
     * @param includeCommanderAsOfficer whether to include the PersonAPI commander as an officer of the fleet, provided they are one.
     * @param includeIdleOfficers whether to include officers that are not assigned to a ship in the fleet.
     * @param includeAggression whether to include the aggression of the fleet in the JSON.
     *
     * On save: store the fleet aggression doctrine as "aggression_doctrine" in the JSON
     *
     * On load:
     * Provided the fleet is **not** the player's fleet, every default officer will have their personality assigned to the corresponding aggression based on the input "aggression_doctrine" like in the campaign.
     *
     * If this is the player's fleet, nothing happens. If you want something to happen, please set the aggression doctrine of the player's faction yourself.
     *
     * @param excludeMembersWithMissingHullSpec whether to exclude fleet members with missing hull specs from the fleet.
     * If true, no "error variants" will be added to the fleet.
     * @param excludeMembersWithID a set of fleet member IDs to exclude from the fleet. If a FleetMemberAPI.id is equal to this, it will be excluded.
     * @param excludeMembersWithHullID a set of hull IDs to exclude from the fleet. If a member has a hull with this ID, it will be excluded.
     * @param memberSettings a set of settings for loading the members of the fleet.
     */
    data class FleetSettings(
        var includeCommanderSetFlagship: Boolean = true,
        var includeCommanderAsOfficer: Boolean = true,
        var includeIdleOfficers: Boolean = false,
        var includeAggression: Boolean = true,
        var excludeMembersWithMissingHullSpec: Boolean = false,
        var excludeMembersWithID: MutableSet<String> = mutableSetOf(),
        var excludeMembersWithHullID: MutableSet<String> = mutableSetOf(),
        var memberSettings: MemberSerialization.MemberSettings = MemberSerialization.MemberSettings(),
    )

    @JvmOverloads
    fun getFleetFromJson(
        json: JSONObject,
        inputCampaignFleet: CampaignFleetAPI,
        settings: FleetSettings = FleetSettings(),
    ): MissingElements {
        return getFleetFromJson(
            json,
            inputCampaignFleet.fleetData,
            settings
        )
    }

    @JvmOverloads
    fun getFleetFromJson(
        json: JSONObject,
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings(),
    ): MissingElements {
        val campFleet: CampaignFleetAPI? = fleet.fleet

        val missingElements = MissingElements()
        getMissingFromModInfo(json, missingElements)

        campFleet?.name = json.optString("fleetName", "Nameless fleet")

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

        fun getMember(memberJson: JSONObject): FleetMemberAPI? {
            val variantId = memberJson.optString("variantId", "")

            val matchingVariant = fleetVariants.firstOrNull { it.hullVariantId == variantId } ?:
            //  if (Global.getSettings().doesVariantExist(variantId)) {
            //     Global.getSettings().getVariant(variantId)
            run {
                showError("Failed to find variant id of $variantId")
                createErrorVariant("VariantIDNotFound")
            }
            if (matchingVariant.hullSpec.hullId in settings.excludeMembersWithHullID)
                return null

            val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, matchingVariant.clone())
            setMemberValuesFromJson(memberJson, member)

            if (matchingVariant.hasTag("ERROR")) {
                if (settings.excludeMembersWithMissingHullSpec)
                    return null

                member.shipName = matchingVariant.displayName
            }

            fleet.addFleetMember(member)
            return member
        }

        fun legacyCommanderAssignment(
            commanderJson: JSONObject,
            commander: PersonAPI
        ) {
            commanderJson.optJSONObject("member")?.let { memberJson ->
                val member = getMember(memberJson)

                if (settings.includeCommanderAsOfficer) {
                    fleet.addOfficer(commander)
                    member?.captain = commander
                }

                if (member == null)
                    return@let

                //Ensure only one flagship. Also sets the flagship, because .setFlagship(true) does not work for some reason
                if (settings.includeCommanderSetFlagship) {
                    fleet.membersListWithFightersCopy.forEach {
                        it.isFlagship = false
                    }
                    member.isFlagship = true
                }

            }
        }

        json.optJSONObject("commander")?.let { commanderJson ->
            val (commander, personMissing) = getPersonFromJsonWithMissing(commanderJson)
            missingElements.add(personMissing)

            if (settings.includeCommanderSetFlagship)
                campFleet?.commander = commander

            // Flagship/Commander's ship
            legacyCommanderAssignment(commanderJson, commander)
        }

        json.optJSONArray("members")?.let { members ->
            for (i in 0 until members.length()) {
                val memberJson = members.optJSONObject(i) ?: continue
                val member = getMember(memberJson)

                var isFlagship = memberJson.optBoolean("isFlagship", false)

                val officerJson = memberJson.optJSONObject("officer")
                val officer: PersonAPI? = officerJson?.let {
                    val (tempOfficer, personMissing) = getPersonFromJsonWithMissing(it)
                    missingElements.add(personMissing)
                    tempOfficer
                }

                if (officer != null) {
                    if (officer.isDefault && !officer.isAICore) {
                        if (settings.includeAggression//Assign doctrinal aggression to non officered ships
                            && Global.getSector().playerFleet !== fleet.fleet//Don't do this to the player's fleet. The player faction's aggression doctrine should be used instead.
                        ) {
                            val personality = when (aggressionDoctrine) {
                                1 -> Personalities.CAUTIOUS
                                2 -> Personalities.STEADY
                                3 -> Personalities.AGGRESSIVE
                                4 -> if (Random().nextBoolean()) Personalities.AGGRESSIVE else Personalities.RECKLESS
                                5 -> Personalities.RECKLESS
                                else -> Personalities.STEADY // fallback/default
                            }
                            officer.setPersonality(personality)
                        }
                    } else if (settings.memberSettings.includeOfficer && (!isFlagship || settings.includeCommanderAsOfficer)) {
                        member?.captain = officer
                        if (!officer.isAICore)
                            fleet.addOfficer(officer)
                    }

                    if (isFlagship && settings.includeCommanderSetFlagship) {
                        campFleet?.commander = officer

                        if (member != null) {
                            fleet.membersListWithFightersCopy.forEach { it.isFlagship = false }
                            member.isFlagship = true
                        }
                    }
                }


            }
        }

        // Idle officers
        if (settings.includeIdleOfficers) {
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

    @JvmOverloads
    fun saveFleetToJson(
        campaignFleet: CampaignFleetAPI,
        settings: FleetSettings = FleetSettings(),
        includeModInfo: Boolean = true,
    ): JSONObject {
        return saveFleetToJson(campaignFleet.fleetData, settings, includeModInfo)
    }

    @JvmOverloads
    fun saveFleetToJson(
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings(),
        includeModInfo: Boolean = true,
    ): JSONObject {
        val campFleet: CampaignFleetAPI? = fleet.fleet

        val fleetJson = JSONObject()

        val membersJson = JSONArray()
        val variantsJson = JSONArray()
        val usedVariantIds = mutableSetOf<String>()
        val variantIdMap = mutableMapOf<String, String>() // variant JSON string -> unique variantId

        var flagshipSet = false

        for (member in fleet.membersListCopy) {
            if (settings.excludeMembersWithID.contains(member.id) || member.variant.hasHullMod(commandShuttleId) || member.hullId in settings.excludeMembersWithHullID)
                continue

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

            if (settings.includeCommanderAsOfficer && settings.includeCommanderSetFlagship && isCommander) {
                memberJson.put("isFlagship", true)
                flagshipSet = true
            }

            membersJson.put(memberJson)

        }

        if (settings.includeCommanderSetFlagship && !flagshipSet && !fleet.commander.isDefault) {
            fleetJson.put(
                "commander", savePersonToJson(
                    fleet.commander,
                    settings.memberSettings.personSettings
                )
            )
        }


        fleetJson.put("members", membersJson)
        fleetJson.put("variants", variantsJson)
        if (settings.includeAggression && campFleet != null && campFleet.faction != null)
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

        if (campFleet != null)
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