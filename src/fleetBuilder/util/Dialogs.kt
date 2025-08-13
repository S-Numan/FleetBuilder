package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import fleetBuilder.persistence.fleet.FleetSerialization
import fleetBuilder.persistence.fleet.FleetSerialization.buildFleetFull
import fleetBuilder.ui.CargoAutoManageUIPlugin
import fleetBuilder.ui.popUpUI.PopUpUIDialog
import fleetBuilder.util.DialogUtil.initPopUpUI
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib
import fleetBuilder.variants.reportMissingElementsIfAny

object Dialogs {
    fun pasteFleetIntoPlayerFleetDialog(
        data: FleetSerialization.ParsedFleetData,
        validatedFleet: FleetSerialization.ParsedFleetData,
    ) {
        val dialog = PopUpUIDialog("Paste Fleet into Player Fleet", addCloseButton = true)

        val memberCount = validatedFleet.members.size
        val officerCount = validatedFleet.members.count { it.personData != null }
        dialog.addParagraph(
            "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                    if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else ""
        )

        val missingHullCount = validatedFleet.members.count { it.variantData == null || it.variantData.tags.contains(VariantLib.errorTag) }
        if (missingHullCount > 0)
            dialog.addParagraph("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods")

        /*
                                        val tempFleet = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, FleetTypes.TASK_FORCE, false)
                                        buildFleet(validatedFleet, tempFleet.fleetData, settings = FleetSerialization.FleetSettings())


                                        val fleetGridClass = FleetGrid::class.java
                                        //val fleetGrid = MagicLib.ReflectionUtils.instantiate(fleetGridClass, 2, 2, 32f, 32f, 8f) as? UIComponentAPI

                                        val fleetGridConstructor = starficz.ReflectionUtils.getConstructorsMatching(fleetGridClass, numOfParams = 6).getOrNull(0)
                                        val fleetGrid = fleetGridConstructor?.newInstance(2, 2, 32f, 32f, 8f, null) as? UIComponentAPI
                                        if (fleetGrid != null)
                                            dialog.addCustom(fleetGrid)*/

        /*val custom = Global.getSettings().createCustom(200f, 200f, null)
                        val tooltip = custom.createUIElement(200f, 200f, false)
                        tooltip.addShipList(3, 2, 160f, Misc.getBasePlayerColor(), tempFleet.fleetData.membersListCopy, 0f)
                        val panel = tooltip.invoke("getPanel") as? UIPanelAPI
                        val shipList = panel?.getChildrenCopy()?.getOrNull(0) as? S ?: return

                        //shipList.isShowDmods = false

                        //tempFleet.fleetData.membersListCopy.forEach { member ->
                        //    shipList.removeIconFor(member as FleetMember?)
                        //}
                        shipList.clear()

                        shipList.isShowDmods = true
                        shipList.setUseExpandedTooltip(true)
                        shipList.members.forEach { member ->
                            //member.
                        }
                        tempFleet.fleetData.membersListCopy.forEach { member ->
                            shipList.addIconFor(member as FleetMember)
                            val icon = shipList.invoke("getIconForMember", member)
                            icon

                        }

                        //shipList?.invoke("setUseBasicTooltip", true)
                        //val tooltipOptions = shipList?.invoke("getTooltipOptions")
                        //val shipListItems = shipList?.list?.items

                        //shipList?
                        //tooltip.isRecreateEveryFrame = true

                        custom.addUIElement(tooltip)
                        dialog.addCustom(custom)*/







        dialog.addPadding(8f)

        dialog.addButton("Append to Player Fleet") { fields ->
            val playerFleet = Global.getSector().playerFleet.fleetData

            val addMissing = MissingElements()
            val fleet = FleetSerialization.createCampaignFleetFromData(
                data, false,
                settings = FleetSerialization.FleetSettings().apply {
                    excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
                    memberSettings.includeOfficer = fields["Include Officers"] as Boolean
                    includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
                },
                missing = addMissing
            )

            fleet.fleetData.membersListCopy.forEach { member ->
                playerFleet.addFleetMember(member)

                val captain = member.captain
                if (!captain.isDefault && !captain.isAICore) {
                    playerFleet.addOfficer(captain)
                }
            }

            reportMissingElementsIfAny(addMissing)

            if (fields["Fulfill cargo, fuel, crew, and repair for fleet"] as Boolean)
                FBMisc.fulfillPlayerFleet()
        }
        dialog.addPadding(24f)

        dialog.addButton("Replace Player Fleet") { fields ->
            val settings = FleetSerialization.FleetSettings()
            settings.memberSettings.includeOfficer = fields["Include Officers"] as Boolean
            settings.excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
            settings.includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
            settings.includeAggression = fields["Set Aggression Doctrine"] as Boolean

            val replaceMissing = FBMisc.replacePlayerFleetWith(
                data,
                (fields["Replace Player with Commander"] as Boolean && settings.includeCommanderAsOfficer),
                settings
            )

            reportMissingElementsIfAny(replaceMissing)

            if (fields["Fulfill cargo, fuel, crew, and repair for fleet"] as Boolean)
                FBMisc.fulfillPlayerFleet()
        }
        dialog.addToggle("Set Aggression Doctrine", default = true)
        dialog.addToggle("Replace Player with Commander", default = false)

        dialog.addPadding(48f)
        dialog.addParagraph("Additional Settings:")
        dialog.addToggle("Include Officers", default = true)
        dialog.addToggle("Include Commander as Officer", default = true)
        dialog.addToggle("Exclude Ships From Missing Mods", default = false)
        dialog.addToggle("Fulfill cargo, fuel, crew, and repair for fleet", default = true)


        initPopUpUI(dialog, 500f, 380f)
    }

    fun openSubmarketCargoAutoManagerDialog(
        selectedSubmarket: SubmarketAPI,
        instantUp: Boolean = false
    ) {
        CargoAutoManageUIPlugin(selectedSubmarket, 1000f, 1000f, instantUp).getPanel()
    }

    fun createDevModeDialog() {
        val dialog = PopUpUIDialog("Dev Options", addCancelButton = false, addConfirmButton = false, addCloseButton = true)
        dialog.addToggle("Toggle Dev Mode", Global.getSettings().isDevMode)

        dialog.onExit { fields ->
            Global.getSettings().isDevMode = fields["Toggle Dev Mode"] as Boolean
        }
        initPopUpUI(dialog, 500f, 200f)
    }

    fun spawnFleetInCampaignDialog(
        sector: SectorAPI,
        data: FleetSerialization.ParsedFleetData,
        validatedData: FleetSerialization.ParsedFleetData
    ) {
        val dialog = PopUpUIDialog("Spawn Fleet in Campaign", addConfirmButton = true, addCancelButton = true)
        dialog.confirmButtonName = "Spawn Fleet"

        val memberCount = validatedData.members.size
        val officerCount = validatedData.members.count { it.personData != null }
        dialog.addParagraph(
            "Pasted fleet contains $memberCount member${if (memberCount != 1) "s" else ""}" +
                    if (officerCount > 0) " and $officerCount officer${if (officerCount != 1) "s" else ""}" else ""
        )

        val missingHullCount = validatedData.members.count { it.variantData == null || it.variantData.tags.contains(VariantLib.errorTag) }
        if (missingHullCount > 0)
            dialog.addParagraph("Fleet contains $missingHullCount hull${if (missingHullCount != 1) "s" else ""} from missing mods")


        dialog.addPadding(8f)

        dialog.addToggle("Include Officers", default = true)
        dialog.addToggle("Include Commander as Commander", default = true)
        dialog.addToggle("Include Commander as Officer", default = true)
        dialog.addPadding(dialog.buttonHeight / 2)
        dialog.addToggle("Set Faction to Pirate", default = true)
        dialog.addToggle("Set Aggression Doctrine", default = true)
        dialog.addToggle("Fight To The Last", default = true)
        dialog.addPadding(dialog.buttonHeight / 2)
        dialog.addToggle("Repair and Set Max CR", default = true)
        dialog.addToggle("Exclude Ships From Missing Mods", default = true)

        dialog.onConfirm { fields ->

            val settings = FleetSerialization.FleetSettings()
            settings.includeAggression = fields["Set Aggression Doctrine"] as Boolean
            settings.memberSettings.includeOfficer = fields["Include Officers"] as Boolean
            settings.includeCommanderSetFlagship = fields["Include Commander as Commander"] as Boolean
            settings.includeCommanderAsOfficer = fields["Include Commander as Officer"] as Boolean
            settings.excludeMembersWithMissingHullSpec = fields["Exclude Ships From Missing Mods"] as Boolean
            val repairAndSetMaxCR = fields["Repair and Set Max CR"] as Boolean
            val setFactionToPirates = (fields["Set Faction to Pirate"] as Boolean)
            val missing = MissingElements()
            missing.gameMods.addAll(data.gameMods)

            val fleet = FleetSerialization.createCampaignFleetFromData(
                if (setFactionToPirates) data.copy(factionID = Factions.PIRATES) else data,
                true, settings = settings, missing = missing
            )

            reportMissingElementsIfAny(missing)

            if (repairAndSetMaxCR)
                FBMisc.fullFleetRepair(fleet.fleetData)

            sector.playerFleet.containingLocation.spawnFleet(sector.playerFleet, 0f, 0f, fleet)
            Global.getSector().campaignUI.showInteractionDialog(fleet)
            if (fields["Fight To The Last"] as Boolean)
                fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

            showMessage("Fleet from clipboard added to campaign")
        }

        initPopUpUI(dialog, 500f, 350f)
    }
}