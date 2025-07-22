package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import fleetBuilder.config.ModSettings.commandShuttleId
import fleetBuilder.persistence.FleetSerialization.saveFleetToJson
import fleetBuilder.persistence.MemberSerialization.buildMember
import fleetBuilder.persistence.MemberSerialization.filterParsedMemberData
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.MemberSerialization.validateAndCleanMemberData
import fleetBuilder.persistence.PersonSerialization.buildPerson
import fleetBuilder.persistence.PersonSerialization.extractPersonDataFromJson
import fleetBuilder.persistence.PersonSerialization.filterParsedPersonData
import fleetBuilder.persistence.PersonSerialization.savePersonToJson
import fleetBuilder.persistence.PersonSerialization.validateAndCleanPersonData
import fleetBuilder.persistence.VariantSerialization.addVariantSourceModsToJson
import fleetBuilder.persistence.VariantSerialization.extractVariantDataFromJson
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.util.FBMisc
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib.createErrorVariant
import fleetBuilder.variants.VariantLib.getHullIDSet
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random


object FleetSerialization {

    /**
     * Settings for [saveFleetToJson] and [getFleetFromJson].
     * @param includeCommanderSetFlagship whether to set the PersonAPI commander as the fleet's commander.
     *
     * If the commander is assigned as a captain of a ship in the fleet, that ship will be set as the flagship.
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
        var includeIdleOfficers: Boolean = true,
        var includeAggression: Boolean = true,
        var excludeMembersWithMissingHullSpec: Boolean = false,
        var excludeMembersWithID: MutableSet<String> = mutableSetOf(),
        var excludeMembersWithHullID: MutableSet<String> = mutableSetOf(),
        var memberSettings: MemberSerialization.MemberSettings = MemberSerialization.MemberSettings(),
    )

    data class ParsedFleetData(
        val fleetName: String,
        val commander: PersonSerialization.ParsedPersonData?,
        val members: List<MemberSerialization.ParsedMemberData>,
        val idleOfficers: List<PersonSerialization.ParsedPersonData>
    )

    fun extractFleetDataFromJson(json: JSONObject): ParsedFleetData {
        val fleetName = json.optString("fleetName", "Nameless fleet")

        val extractedVariants = mutableListOf<VariantSerialization.ParsedVariantData>()
        json.optJSONArray("variants")?.let { variantsArray ->
            for (i in 0 until variantsArray.length()) {
                variantsArray.optJSONObject(i)?.let {
                    extractedVariants.add(extractVariantDataFromJson(it))
                }
            }
        }

        val variantById = extractedVariants.associateBy { it.variantId }

        val members = mutableListOf<MemberSerialization.ParsedMemberData>()
        fun getMember(memberJson: JSONObject) {
            val variantId = memberJson.optString("variantId", "")
            val isFlagship = memberJson.optBoolean("isFlagship", false)

            val variantData = variantById[variantId]?.copy()
            val personData = memberJson.optJSONObject("officer")?.let { extractPersonDataFromJson(it) }

            members.add(
                MemberSerialization.ParsedMemberData(
                    variantData = variantData,
                    personData = personData,
                    shipName = memberJson.optString("name", ""),
                    cr = memberJson.optDouble("cr", 0.7).toFloat(),
                    isMothballed = memberJson.optBoolean("ismothballed", false),
                    isFlagship = isFlagship
                )
            )
        }

        json.optJSONArray("members")?.let { membersArray ->
            for (i in 0 until membersArray.length()) {
                val memberJson = membersArray.optJSONObject(i) ?: continue
                getMember(memberJson)
            }
        }

        val commander = json.optJSONObject("commander")?.let {
            //LEGACY BEHAVIOR
            if (it.has("member")) {
                it.optJSONObject("member")?.let { memberJson ->
                    memberJson.put("officer", it)
                    memberJson.put("isFlagship", true)
                    getMember(memberJson)
                }
                null
            } else {
                extractPersonDataFromJson(it)
            }
        }

        val idleOfficers = mutableListOf<PersonSerialization.ParsedPersonData>()
        json.optJSONArray("idleOfficers")?.let { officersArray ->
            for (i in 0 until officersArray.length()) {
                officersArray.optJSONObject(i)?.let {
                    idleOfficers.add(extractPersonDataFromJson(it))
                }
            }
        }

        return ParsedFleetData(
            fleetName = fleetName,
            commander = commander,
            members = members,
            idleOfficers = idleOfficers
        )
    }


    fun filterParsedFleetData(data: ParsedFleetData, settings: FleetSettings): ParsedFleetData {

        var filteredCommander = if (settings.includeCommanderSetFlagship)
            data.commander?.let { filterParsedPersonData(it, settings.memberSettings.personSettings) }
        else null

        val filteredMembers = data.members.mapNotNull { member ->
            val filtered = filterParsedMemberData(member, settings.memberSettings)
            if (filtered.variantData?.hullId in settings.excludeMembersWithHullID ||
                filtered.variantData?.variantId in settings.excludeMembersWithID
            ) {
                null
            } else if (filtered.isFlagship && (!settings.includeCommanderAsOfficer || !settings.memberSettings.includeOfficer)) {
                if (filteredCommander == null && settings.includeCommanderSetFlagship && member.personData != null) {
                    filteredCommander = filterParsedPersonData(member.personData, settings.memberSettings.personSettings)
                }
                filtered.copy(personData = null, isFlagship = false)
            } else if (!settings.memberSettings.includeOfficer) {
                filtered.copy(personData = null)
            } else if (filtered.isFlagship && !settings.includeCommanderSetFlagship) {
                filtered.copy(isFlagship = false)
            } else {
                filtered
            }
        }


        val filteredIdleOfficers = if (settings.includeIdleOfficers) {
            data.idleOfficers.map {
                filterParsedPersonData(it, settings.memberSettings.personSettings)
            }
        } else emptyList()

        return data.copy(
            members = filteredMembers,
            commander = filteredCommander,
            idleOfficers = filteredIdleOfficers
        )
    }

    fun validateAndCleanFleetData(
        data: ParsedFleetData,
        missing: MissingElements,
        settings: FleetSettings
    ): ParsedFleetData {
        val validatedMembers = data.members.mapNotNull { member ->
            val validated = validateAndCleanMemberData(member, missing)
            if (validated.variantData == null) {
                validated.copy(extractVariantDataFromJson(saveVariantToJson(createErrorVariant("NOVAR"))))
            } else if (!getHullIDSet().contains(validated.variantData.hullId)) {
                missing.hullIds.add(validated.variantData.hullId)
                if (!settings.excludeMembersWithMissingHullSpec)
                    validated.copy(extractVariantDataFromJson(saveVariantToJson(createErrorVariant("NOHUL:${validated.variantData.hullId}"))))
                else
                    null
            } else {
                validated
            }
        }

        val validatedCommander = data.commander?.let {
            validateAndCleanPersonData(it, missing)
        }

        val validatedIdleOfficers = data.idleOfficers.map {
            validateAndCleanPersonData(it, missing)
        }

        return data.copy(
            members = validatedMembers,
            commander = validatedCommander,
            idleOfficers = validatedIdleOfficers
        )
    }

    fun buildFleet(data: ParsedFleetData, fleet: FleetDataAPI, settings: FleetSettings): MissingElements {
        val missing = MissingElements()
        val campFleet = fleet.fleet
        campFleet?.name = data.fleetName

        data.members.forEach { parsed ->
            val (member, subMissing) = buildMember(parsed)
            missing.add(subMissing)
            fleet.addFleetMember(member)

            if (parsed.isFlagship) {
                if (!member.captain.isDefault)
                    campFleet?.commander = member.captain

                fleet.membersListCopy.forEach { it.isFlagship = false }
                member.isFlagship = true
            }

            member.captain?.let { officer ->
                if (!officer.isAICore) {

                    if (!officer.isDefault)
                        fleet.addOfficer(officer)

                    // Apply doctrinal aggression to default officers
                    if (settings.includeAggression && campFleet !== Global.getSector().playerFleet) { // Don't do this to the playerFleet, just set the player's faction aggression doctrine manually instead.
                        if (officer.isDefault) {
                            val aggression = campFleet?.faction?.doctrine?.aggression ?: 2
                            val personality = when (aggression) {
                                1 -> Personalities.CAUTIOUS
                                2 -> Personalities.STEADY
                                3 -> Personalities.AGGRESSIVE
                                4 -> if (Random().nextBoolean()) Personalities.AGGRESSIVE else Personalities.RECKLESS
                                5 -> Personalities.RECKLESS
                                else -> Personalities.STEADY
                            }

                            officer.setPersonality(personality)
                        }
                    }
                }
            }
        }

        data.commander?.let {
            campFleet?.commander = buildPerson(it)
        }

        data.idleOfficers.forEach {
            fleet.addOfficer(buildPerson(it))
        }

        fleet.syncIfNeeded()

        return missing
    }

    fun buildFleetFromParsed(
        parsed: ParsedFleetData,
        fleet: FleetDataAPI,
        settings: FleetSettings
    ): MissingElements {
        val filtered = filterParsedFleetData(parsed, settings)
        val validated = validateAndCleanFleetData(filtered, MissingElements(), settings)
        return buildFleet(validated, fleet, settings)
    }

    @JvmOverloads
    fun getFleetFromJson(
        json: JSONObject,
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings()
    ): MissingElements {
        val missing = MissingElements()
        FBMisc.getMissingFromModInfo(json, missing)

        val extracted = extractFleetDataFromJson(json)
        missing.add(buildFleetFromParsed(extracted, fleet, settings))

        return missing
    }

    @JvmOverloads
    fun getFleetFromJson(
        json: JSONObject,
        inputCampaignFleet: CampaignFleetAPI,
        settings: FleetSettings = FleetSettings()
    ): MissingElements {
        return getFleetFromJson(json, inputCampaignFleet.fleetData, settings)
    }


    /*
        fun getFleetFromJsonAll(
            json: JSONObject,
            fleet: FleetDataAPI
        ): MissingElements {
            val campFleet = fleet.fleet
            val missingElements = MissingElements()

            FBMisc.getMissingFromModInfo(json, missingElements)
            campFleet?.name = json.optString("fleetName", "Nameless fleet")

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

            fun getMember(memberJson: JSONObject, includeOfficer: Boolean = true): FleetMemberAPI {
                val variantId = memberJson.optString("variantId", "")
                val matchingVariant = fleetVariants.firstOrNull { it.hullVariantId == variantId } ?:
                //  if (Global.getSettings().doesVariantExist(variantId)) {
                //     Global.getSettings().getVariant(variantId) else
                VariantLib.createErrorVariant("VariantIDNotFound").also {
                    DisplayMessage.showError("Failed to find variant id of $variantId")
                }

                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, matchingVariant.clone())
                setMemberValuesFromJson(memberJson, member)
                fleet.addFleetMember(member)

                if (matchingVariant.hasTag("ERROR"))
                    member.shipName = matchingVariant.displayName

                if (includeOfficer) {
                    val officerJson = memberJson.optJSONObject("officer")
                    val isFlagship = memberJson.optBoolean("isFlagship", false)
                    val officer = officerJson?.let {
                        val (off, missing) = getPersonFromJsonWithMissing(it)
                        missingElements.add(missing)
                        off
                    }

                    if (officer != null) {
                        member.captain = officer
                        if (!officer.isAICore)
                            fleet.addOfficer(officer)
                        if (isFlagship) {
                            campFleet?.commander = officer
                            fleet.membersListCopy.forEach { it.isFlagship = false }
                            member.isFlagship = true
                        }
                    }
                }

                return member
            }

            // Commander
            json.optJSONObject("commander")?.let { commanderJson ->
                val (commander, personMissing) = getPersonFromJsonWithMissing(commanderJson)
                missingElements.add(personMissing)
                campFleet?.commander = commander

                // legacy assignment to a specific ship
                commanderJson.optJSONObject("member")?.let { memberJson ->
                    val member = getMember(memberJson, false)
                    member.captain = commander
                    fleet.addOfficer(commander)
                    fleet.membersListCopy.forEach { it.isFlagship = false }
                    member.isFlagship = true
                }
            }

            // Regular members
            json.optJSONArray("members")?.let { members ->
                for (i in 0 until members.length()) {
                    members.optJSONObject(i)?.let { memberJson ->
                        getMember(memberJson)
                    }
                }
            }

            // Idle officers
            json.optJSONArray("idleOfficers")?.let { officers ->
                for (i in 0 until officers.length()) {
                    officers.optJSONObject(i)?.let { officerJson ->
                        val (officer, missing) = getPersonFromJsonWithMissing(officerJson)
                        missingElements.add(missing)
                        fleet.addOfficer(officer)
                    }
                }
            }

            fleet.syncIfNeeded()
            return missingElements
        }

        fun applyFleetSettings(fleet: FleetDataAPI, settings: FleetSettings) {
            val campFleet = fleet.fleet

            // Remove unwanted members by ID or HullID
            val toRemove = fleet.membersListCopy.filter { member ->
                member.id in settings.excludeMembersWithID ||
                        member.hullSpec?.hullId in settings.excludeMembersWithHullID ||
                        (settings.excludeMembersWithMissingHullSpec && member.variant.hasTag("ERROR"))
            }
            toRemove.forEach { fleet.removeFleetMember(it) }

            if (!settings.memberSettings.includeOfficer) {
                fleet.membersListCopy.forEach { member ->
                    fleet.removeOfficer(member.captain)
                    member.captain = Global.getSettings().createPerson()
                }
            }

            // Handle officers
            if (!settings.includeIdleOfficers) {
                // Assume idle officers are those not assigned to a ship
                val activeCaptains = fleet.membersListCopy.mapNotNull { it.captain }.toSet()
                val idle = fleet.officersCopy.filterNot { it.person in activeCaptains }
                idle.forEach { fleet.removeOfficer(it.person) }
            }

            if (!settings.includeCommanderAsOfficer)
                fleet.membersListCopy.find { it.captain === campFleet.commander }?.captain = Global.getSettings().createPerson()

            if (settings.includeCommanderSetFlagship && campFleet?.commander != null) {
                fleet.membersListWithFightersCopy.forEach { it.isFlagship = false }

                fleet.membersListCopy.find { it.captain === campFleet.commander }?.isFlagship = true
            }
            if (!settings.includeCommanderSetFlagship) {
                fleet.membersListCopy.find { it.captain === campFleet.commander }?.isFlagship = false

                if (campFleet != null && !campFleet.commander.isPlayer)
                    campFleet.commander = campFleet.faction?.createRandomPerson()
            }

            // Apply doctrinal aggression to default officers
            if (settings.includeAggression && campFleet !== Global.getSector().playerFleet) {
                for (member in fleet.membersListCopy) {
                    val officer = member.captain ?: continue

                    if (officer.isDefault && !officer.isAICore) {
                        val aggression = campFleet?.faction?.doctrine?.aggression ?: 2
                        val personality = when (aggression) {
                            1 -> Personalities.CAUTIOUS
                            2 -> Personalities.STEADY
                            3 -> Personalities.AGGRESSIVE
                            4 -> if (Random().nextBoolean()) Personalities.AGGRESSIVE else Personalities.RECKLESS
                            5 -> Personalities.RECKLESS
                            else -> Personalities.STEADY
                        }

                        officer.setPersonality(personality)
                    }
                }
            }

            fleet.syncIfNeeded()
        }

        @JvmOverloads
        fun getFleetFromJson(
            json: JSONObject,
            fleet: FleetDataAPI,
            settings: FleetSettings = FleetSettings()
        ): MissingElements {
            val missing = getFleetFromJsonAll(json, fleet)
            applyFleetSettings(fleet, settings)
            return missing
        }

        @JvmOverloads
        fun getFleetFromJson(
            json: JSONObject,
            inputCampaignFleet: CampaignFleetAPI,
            settings: FleetSettings = FleetSettings()
        ): MissingElements {
            return getFleetFromJson(json, inputCampaignFleet.fleetData, settings)
        }
     */


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