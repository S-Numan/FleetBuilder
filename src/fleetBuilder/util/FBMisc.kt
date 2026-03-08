package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.HullModItemManager
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.ModSettings
import fleetBuilder.core.ModSettings.randomPastedCosmetics
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.core.displayMessage.DisplayMessage.showMessage
import fleetBuilder.core.shipDirectory.ShipDirectoryService.doesLoadoutExist
import fleetBuilder.features.hotkeyHandler.HotkeyHandlerDialogs
import fleetBuilder.serialization.ClipboardMisc
import fleetBuilder.serialization.GameModInfo
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.DataFleet.getFleetDataFromFleet
import fleetBuilder.serialization.fleet.DataFleet.validateAndCleanFleetData
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.member.DataMember.buildMemberFull
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.serialization.person.DataPerson.buildPersonFull
import fleetBuilder.serialization.reportMissingElementsIfAny
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.serialization.variant.DataVariant.buildVariantFull
import fleetBuilder.util.ReflectionMisc.getViewedFleetInFleetPanel
import fleetBuilder.util.ReflectionMisc.updateFleetPanelContents
import fleetBuilder.util.api.MemberUtils.getMaxSMods
import fleetBuilder.util.api.MemberUtils.randomizeMemberCosmetics
import fleetBuilder.util.api.PersonUtils
import fleetBuilder.util.api.VariantUtils.getHullModBuildInBonusXP
import org.json.JSONObject
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.getOPCost
import java.awt.Color
import kotlin.math.min


internal object FBMisc {
    fun startStencilWithYPad(panel: CustomPanelAPI, yPad: Float) {
        GL11.glClearStencil(0)
        GL11.glStencilMask(0xff)
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)

        GL11.glColorMask(false, false, false, false)
        GL11.glEnable(GL11.GL_STENCIL_TEST)

        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xff)
        GL11.glStencilMask(0xff)
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE)

        GL11.glBegin(GL11.GL_POLYGON)
        val position = panel.getPosition()
        val x = position.getX() - 5
        val y = position.getY()
        val width = position.getWidth() + 10
        val height = position.getHeight()

        // Define the rectangle
        GL11.glVertex2f(x, y)
        GL11.glVertex2f(x + width, y)
        GL11.glVertex2f(x + width, y + height - yPad)
        GL11.glVertex2f(x, y + height - yPad)
        GL11.glEnd()

        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
        GL11.glColorMask(true, true, true, true)

        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
    }

    fun startStencilWithXPad(panel: CustomPanelAPI, xPad: Float) {
        GL11.glClearStencil(0)
        GL11.glStencilMask(0xff)
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)

        GL11.glColorMask(false, false, false, false)
        GL11.glEnable(GL11.GL_STENCIL_TEST)

        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xff)
        GL11.glStencilMask(0xff)
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE)

        GL11.glBegin(GL11.GL_POLYGON)
        val position = panel.getPosition()
        val x = position.getX() - 5
        val y = position.getY() - 10
        val width = position.getWidth() + 10
        val height = position.getHeight() + 20

        // Define the rectangle
        GL11.glVertex2f(x, y)
        GL11.glVertex2f(x + width - xPad, y)
        GL11.glVertex2f(x + width - xPad, y + height)
        GL11.glVertex2f(x, y + height)
        GL11.glEnd()

        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
        GL11.glColorMask(true, true, true, true)

        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
    }

    fun endStencil() {
        GL11.glDisable(GL11.GL_STENCIL_TEST)
    }

    fun renderTiledTexture(
        textureId: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tileWidth: Float,
        tileHeight: Float,
        alphaMult: Float,
        color: Color
    ) {
        if (textureId == 0) {
            DisplayMessage.showError("Error: Invalid texture ID.")
            return
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)

        // Enable blending for alpha transparency
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        // Set the texture to repeat
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)

        // Set nearest neighbor filtering to preserve the lines
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)

        // Calculate texture repeat factors based on the panel's size and the texture's tile size
        val uMax: Float = width / tileWidth // Repeat in the X direction
        val vMax: Float = height / tileHeight // Repeat in the Y direction

        // Set color with alpha transparency
        GL11.glColor4f(color.red.toFloat(), color.green.toFloat(), color.blue.toFloat(), alphaMult)

        // Render the panel with tiling
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(x, y) // Bottom-left
        GL11.glTexCoord2f(uMax, 0f)
        GL11.glVertex2f(x + width, y) // Bottom-right
        GL11.glTexCoord2f(uMax, vMax)
        GL11.glVertex2f(x + width, y + height) // Top-right
        GL11.glTexCoord2f(0f, vMax)
        GL11.glVertex2f(x, y + height) // Top-left
        GL11.glEnd()

        // Reset color to fully opaque to avoid affecting other renders
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)

        // Disable blending and textures
        GL11.glDisable(GL11.GL_BLEND)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
    }

    fun handleRefitCopy(isShiftDown: Boolean): Boolean {
        val baseVariant = ReflectionMisc.getCurrentVariantInRefitTab() ?: return false

        ClipboardMisc.saveVariantToClipboard(baseVariant, isShiftDown)

        return true
    }

    fun handleRefitPaste(): Boolean {
        var data = ClipboardMisc.extractDataFromClipboard() ?: return false

        if (data is DataMember.ParsedMemberData && data.variantData != null) {
            data = data.variantData
        }
        if (data !is DataVariant.ParsedVariantData) {
            DisplayMessage.showMessage(FBTxt.txt("data_valid_but_no_variant"), Color.YELLOW)
            return true
        }

        val missing = MissingElements()
        val variant = buildVariantFull(data, missing = missing)

        if (missing.hullIds.isNotEmpty()) {
            DisplayMessage.showMessage(
                FBTxt.txt("failed_to_import_loadout", missing.hullIds.first()),
                Color.YELLOW
            )
            return true
        }


        val loadoutExists = doesLoadoutExist(ModSettings.defaultPrefix, variant)

        if (!loadoutExists) {
            HotkeyHandlerDialogs.createImportLoadoutDialog(variant, missing)
        } else {
            DisplayMessage.showMessage(
                FBTxt.txt("loadout_already_exists", variant.hullSpec.hullId),
                variant.hullSpec.hullName,
                Misc.getHighlightColor()
            )
        }

        return true
    }

    fun sModHandlerTemp(
        ship: ShipAPI,
        baseVariant: ShipVariantAPI,
        loadout: ShipVariantAPI
    ): Pair<List<String>, Float> {
        val coreUI = ReflectionMisc.getCoreUI() as? CoreUIAPI ?: return emptyList<String>() to 0f

        val playerSPLeft = Global.getSector().playerStats.storyPoints

        var maxSMods = getMaxSMods(ship.mutableStats)
        val currentSMods = (baseVariant.sMods + baseVariant.sModdedBuiltIns).toSet()
        val newSMods = (loadout.sMods + loadout.sModdedBuiltIns).toSet()
        var sModsToApply = newSMods.filter { it !in currentSMods }

        // Filter out SMods that are sModdedBuiltIns in the loadout, but not a built-in hullmod in the baseVariant. See Mad Rockpiper MIDAS from Roider Union for why this is done. Also, sometimes variant skins have built in hullmods that can be sModded.
        sModsToApply = sModsToApply.filterNot { it in loadout.sModdedBuiltIns && !baseVariant.hullSpec.builtInMods.contains(it) }
        if (currentSMods.count { it !in baseVariant.hullSpec.builtInMods } + sModsToApply.count { it !in loadout.hullSpec.builtInMods } > maxSMods) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_build_in_slots"), Color.YELLOW)
            return emptyList<String>() to 0f
        }
        maxSMods = min(maxSMods, playerSPLeft)

        if (sModsToApply.size > maxSMods) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_story_point"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        var canApplySMods: List<String> = sModsToApply.filter { Global.getSector().playerFaction.knowsHullMod(it) }

        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_knowledge"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        /*val requiredItems = sModsToApply.mapNotNull { Global.getSettings().getHullModSpec(it).effect.requiredItem }
        val numAvailable = requiredItems.sumOf { HullModItemManager.getInstance().getNumAvailableMinusUnconfirmed(it, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket) }
        if (numAvailable < sModsToApply.size) {
            DisplayMessage.showMessage("Cannot apply some SMods. Lacking required items ...", Color.YELLOW)
            return emptyList<String>() to 0f
        }

        sModsToApply.forEach { modID ->
            val modSpec = Global.getSettings().getHullModSpec(modID)
            if (modSpec.effect.requiredItem == null) return@forEach
            modSpec.effect.requiredItem
            val numAvailable = HullModItemManager.getInstance().getNumAvailableMinusUnconfirmed(modSpec.effect.requiredItem, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket)
        }*/

        canApplySMods = sModsToApply.filter { HullModItemManager.getInstance().isRequiredItemAvailable(it, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket) }
        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_item"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        canApplySMods = sModsToApply.filter { Global.getSettings().getHullModSpec(it).effect.canBeAddedOrRemovedNow(ship, Global.getSector().currentlyOpenMarket, coreUI.tradeMode) }

        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_dock"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        sModsToApply.forEach { modID ->
            if (baseVariant.hullSpec.getOrdnancePoints(null) < Global.getSettings().getHullModSpec(modID).getOPCost(baseVariant.hullSize)) {
                DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_op"), Color.YELLOW)
                return emptyList<String>() to 0f
            }
        }

        var bonusXpToGrant = 0f
        sModsToApply.forEach { modID ->
            bonusXpToGrant += getHullModBuildInBonusXP(baseVariant, modID)
        }

        return sModsToApply to bonusXpToGrant
    }

    fun campaignPaste(
        sector: SectorAPI,
        data: Any
    ): Boolean {
        var newData = data
        if (newData !is DataFleet.ParsedFleetData) {
            fun hackTogetherFleet(member: FleetMemberAPI) {
                val fleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, FleetTypes.TASK_FORCE, true)
                fleet.fleetData.addFleetMember(member)
                if (!member.captain.isDefault) {
                    fleet.fleetData.addOfficer(member.captain)
                    fleet.commander = member.captain
                }
                //fleet.fleetData.setFlagship(member)

                newData = getFleetDataFromFleet(fleet)
            }
            if (newData is DataMember.ParsedMemberData) {
                val member = buildMemberFull(newData as DataMember.ParsedMemberData)
                hackTogetherFleet(member)
            } else if (newData is DataVariant.ParsedVariantData) {
                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, buildVariantFull(newData as DataVariant.ParsedVariantData))
                hackTogetherFleet(member)
            } else if (newData is DataPerson.ParsedPersonData) {
                DisplayMessage.showMessage(FBTxt.txt("campaign_officer_spawn"), Color.YELLOW)
                return false
            } else {
                DisplayMessage.showMessage(FBTxt.txt("data_valid_but_no_campaign_paste"), Color.YELLOW)
                return false
            }
        }

        val missing = MissingElements()
        val validatedData = validateAndCleanFleetData(newData as DataFleet.ParsedFleetData, settings = FleetSettings(), missing = missing)

        if (validatedData.members.isEmpty()) {
            reportMissingElementsIfAny(missing, FBTxt.txt("fleet_was_empty_when_pasting"))
            return false
        }

        HotkeyHandlerDialogs.spawnFleetInCampaignDialog(sector, newData as DataFleet.ParsedFleetData, validatedData)

        return true
    }

    fun fleetPaste(
        sector: SectorAPI,
        data: Any
    ) {
        val playerFleet = sector.playerFleet.fleetData

        var uiShowsSubmarketFleet = false

        val fleetToAddTo = getViewedFleetInFleetPanel() ?: playerFleet
        if (fleetToAddTo !== playerFleet)
            uiShowsSubmarketFleet = true

        val missing = MissingElements()

        when (data) {
            is DataPerson.ParsedPersonData -> {
                // Officer
                val person = buildPersonFull(data, missing = missing)

                if (randomPastedCosmetics) {
                    PersonUtils.randomizePersonCosmetics(person, playerFleet.fleet.faction)
                }
                playerFleet.addOfficer(person)
                showMessage(FBTxt.txt("added_officer_to_fleet"))
            }

            is DataVariant.ParsedVariantData -> {
                // Variant
                val variant = buildVariantFull(data, missing = missing)

                if (missing.hullIds.size > 1) {
                    reportMissingElementsIfAny(missing, FBTxt.txt("could_not_find_hullid_when_variant", missing.hullIds.first()))
                    return
                }

                val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant)

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)

                val shipName = variant.hullSpec.hullName

                val message = if (uiShowsSubmarketFleet)
                    FBTxt.txt("added_ship_to_submarket", shipName)
                else
                    FBTxt.txt("added_ship_to_fleet", shipName)

                showMessage(message, shipName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is DataMember.ParsedMemberData -> {
                // Fleet member
                val member = buildMemberFull(data, missing = missing)

                if (missing.hullIds.size > 1) {
                    reportMissingElementsIfAny(missing, FBTxt.txt("could_not_find_hullid_when_member", missing.hullIds.first()))
                    return
                }

                if (randomPastedCosmetics)
                    randomizeMemberCosmetics(member, fleetToAddTo)

                fleetToAddTo.addFleetMember(member)
                if (!member.captain.isDefault && !member.captain.isAICore && !uiShowsSubmarketFleet)
                    fleetToAddTo.addOfficer(member.captain)

                val shipName = member.hullSpec.hullName
                val message = if (uiShowsSubmarketFleet) {
                    if (member.captain.isDefault) {
                        FBTxt.txt("added_ship_to_submarket", shipName)
                    } else {
                        if (member.captain.faction.id != "tahlan_allmother") {
                            member.captain.memoryWithoutUpdate.set(ModSettings.storedOfficerTag, true)
                            member.captain.memoryWithoutUpdate.set(Misc.CAPTAIN_UNREMOVABLE, true)
                        }
                        FBTxt.txt("added_ship_to_submarket_with_officer", shipName)
                    }
                } else {
                    if (member.captain.isDefault) {
                        FBTxt.txt("added_ship_to_fleet", shipName)
                    } else {
                        FBTxt.txt("added_ship_to_fleet_with_officer", shipName)
                    }
                }

                showMessage(message, shipName, Misc.getHighlightColor())

                updateFleetPanelContents()
            }

            is DataFleet.ParsedFleetData -> {
                // Fleet
                val subMissing = MissingElements()
                val validatedFleet = validateAndCleanFleetData(data, missing = subMissing)

                if (validatedFleet.members.isEmpty()) {
                    reportMissingElementsIfAny(subMissing, FBTxt.txt("fleet_was_empty_when_pasting"))
                    return
                }

                HotkeyHandlerDialogs.pasteFleetIntoPlayerFleetDialog(data, validatedFleet)
            }

            else -> {
                DisplayMessage.showMessage(FBTxt.txt("data_valid_but_not_fleet_member_variant_person"), Color.YELLOW)
            }
        }

        reportMissingElementsIfAny(missing)
    }

    fun getModInfosFromJson(json: JSONObject, onlyMissing: Boolean = false): MutableSet<GameModInfo> {
        val gameMods = mutableSetOf<GameModInfo>()
        json.optJSONArray("mod_info")?.let {
            repeat(it.length()) { i ->
                val modSpecJson = it.optJSONObject(i)
                val modSpecId = modSpecJson.optString("mod_id")
                val modSpecName = modSpecJson.optString("mod_name")
                val modSpecVersion = modSpecJson.optString("mod_version")

                var hasMod = false
                for (modSpecAPI in Global.getSettings().modManager.enabledModsCopy) {
                    if (modSpecAPI.id == modSpecId) {
                        hasMod = true
                        break
                    }
                }

                if (onlyMissing && hasMod) return@repeat

                gameMods.add(GameModInfo(modSpecId, modSpecName, modSpecVersion))
            }
        }
        return gameMods
    }
}
