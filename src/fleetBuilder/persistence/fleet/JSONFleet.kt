package fleetBuilder.persistence.fleet

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.persistence.fleet.DataFleet.buildFleetFull
import fleetBuilder.persistence.fleet.DataFleet.createCampaignFleetFromData
import fleetBuilder.persistence.fleet.DataFleet.getFleetDataFromFleet
import fleetBuilder.persistence.fleet.SecondInCommandSerialization.extractSecondInCommandData
import fleetBuilder.persistence.fleet.SecondInCommandSerialization.saveSecondInCommandData
import fleetBuilder.persistence.member.DataMember
import fleetBuilder.persistence.member.JSONMember.extractMemberDataFromJson
import fleetBuilder.persistence.member.JSONMember.saveMemberToJson
import fleetBuilder.persistence.person.DataPerson
import fleetBuilder.persistence.person.JSONPerson.extractPersonDataFromJson
import fleetBuilder.persistence.person.JSONPerson.savePersonToJson
import fleetBuilder.persistence.variant.DataVariant
import fleetBuilder.persistence.variant.JSONVariant.addVariantSourceModsToJson
import fleetBuilder.persistence.variant.JSONVariant.extractVariantDataFromJson
import fleetBuilder.util.FBMisc
import fleetBuilder.variants.MissingElements
import org.json.JSONArray
import org.json.JSONObject

object JSONFleet {
    @JvmOverloads
    fun extractFleetDataFromJson(
        json: JSONObject,
        missing: MissingElements = MissingElements()
    ): DataFleet.ParsedFleetData {
        val fleetName = json.optString("fleetName", "Nameless fleet")

        val extractedVariants = mutableListOf<DataVariant.ParsedVariantData>()
        json.optJSONArray("variants")?.let { variantsArray ->
            for (i in 0 until variantsArray.length()) {
                variantsArray.optJSONObject(i)?.let {
                    extractedVariants.add(extractVariantDataFromJson(it, missing))
                }
            }
        }

        val variantById = extractedVariants.associateBy { it.variantId }

        val members = mutableListOf<DataMember.ParsedMemberData>()
        fun getMember(memberJson: JSONObject) {
            val variantId = memberJson.optString("variantId", "")
            val variantData = variantById[variantId]?.copy()

            val memberData = extractMemberDataFromJson(memberJson, missing).copy(variantData = variantData)

            members.add(
                memberData
            )
        }

        val commander = json.optJSONObject("commander")?.let {
            if (it.has("member")) {//LEGACY BEHAVIOR
                it.optJSONObject("member")?.let { memberJson ->
                    memberJson.put("officer", it)
                    memberJson.put("isFlagship", true)
                    getMember(memberJson)
                }
                null
            } else {
                extractPersonDataFromJson(it, missing)
            }
        }

        json.optJSONArray("members")?.let { membersArray ->
            for (i in 0 until membersArray.length()) {
                val memberJson = membersArray.optJSONObject(i) ?: continue
                getMember(memberJson)
            }
        }

        val idleOfficers = mutableListOf<DataPerson.ParsedPersonData>()
        json.optJSONArray("idleOfficers")?.let { officersArray ->
            for (i in 0 until officersArray.length()) {
                officersArray.optJSONObject(i)?.let {
                    idleOfficers.add(extractPersonDataFromJson(it, missing))
                }
            }
        }


        val sicData =
            if (Global.getSettings().modManager.isModEnabled("second_in_command")) {
                json.optJSONObject("second_in_command")?.let {
                    extractSecondInCommandData(it)
                }
            } else null

        missing.gameMods.addAll(FBMisc.getModInfosFromJson(json))

        val aggression = json.optInt("aggression_doctrine", -1)

        val factionID = json.optString("factionID", "")

        return DataFleet.ParsedFleetData(
            fleetName = fleetName,
            aggression = aggression,
            factionID = factionID,
            commander = commander,
            members = members,
            idleOfficers = idleOfficers,
            secondInCommandData = sicData,
        )
    }

    @JvmOverloads
    fun getFleetFromJson(
        json: JSONObject,
        fleet: CampaignFleetAPI,
        settings: FleetSettings = FleetSettings(),
        missing: MissingElements = MissingElements()
    ) {
        return getFleetFromJson(json, fleet.fleetData, settings, missing)
    }

    @JvmOverloads
    fun getFleetFromJson(
        json: JSONObject,
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings(),
        missing: MissingElements = MissingElements()
    ) {
        val extracted = extractFleetDataFromJson(json)

        buildFleetFull(extracted, fleet, settings, missing)
    }

    @JvmOverloads
    fun getFleetFromJson(
        json: JSONObject,
        aiMode: Boolean,
        settings: FleetSettings = FleetSettings(),
        missing: MissingElements = MissingElements()
    ): CampaignFleetAPI {
        val extracted = extractFleetDataFromJson(json)

        return createCampaignFleetFromData(extracted, aiMode, settings, missing)
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
        return saveFleetToJson(
            getFleetDataFromFleet(fleet, settings),
            includeModInfo
        )
    }

    @JvmOverloads
    fun saveFleetToJson(
        data: DataFleet.ParsedFleetData,
        includeModInfo: Boolean = true,
    ): JSONObject {
        val fleetJson = JSONObject()

        val membersJson = JSONArray()
        val variantsJson = JSONArray()
        val usedVariantIds = mutableSetOf<String>()
        val variantIdMap = mutableMapOf<String, String>() // variant JSON string -> unique variantId

        var flagshipSet = false

        for (member in data.members) {
            if (member.variantData == null)
                continue

            if (includeModInfo)
                addVariantSourceModsToJson(member.variantData, fleetJson)

            val memberJson = saveMemberToJson(
                member,
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

            if (member.isFlagship) {
                memberJson.put("isFlagship", true)
                flagshipSet = true
            }

            membersJson.put(memberJson)
        }

        if (!flagshipSet && data.commander != null) {
            fleetJson.put(
                "commander", savePersonToJson(data.commander)
            )
        }

        fleetJson.put("members", membersJson)
        fleetJson.put("variants", variantsJson)
        if (data.aggression != -1)
            fleetJson.put("aggression_doctrine", data.aggression)

        if (data.idleOfficers.isNotEmpty()) {
            val idleOfficers = data.idleOfficers.map { officerData ->
                savePersonToJson(officerData)
            }
            if (idleOfficers.isNotEmpty())
                fleetJson.put("idleOfficers", JSONArray(idleOfficers))
        }

        if (data.fleetName != null) {
            fleetJson.put("fleetName", data.fleetName)

            if (data.secondInCommandData != null && Global.getSettings().modManager.isModEnabled("second_in_command"))
                saveSecondInCommandData(data.secondInCommandData, fleetJson)

            if (data.factionID != null)
                fleetJson.put("factionID", data.factionID)
        }

        return fleetJson
    }
}