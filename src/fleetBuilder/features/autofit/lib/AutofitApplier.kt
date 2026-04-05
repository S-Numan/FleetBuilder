package fleetBuilder.features.autofit.lib

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.HullModItemManager
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.plugins.impl.CoreAutofitPlugin
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.FBMisc.replaceVariantWithVariant
import fleetBuilder.core.FBMisc.sModHandlerTemp
import fleetBuilder.core.FBSettings
import fleetBuilder.core.FBTxt
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.CampaignUtils.spendStoryPoint
import fleetBuilder.util.api.VariantUtils.getHullModBuildInBonusXP
import fleetBuilder.util.api.kotlin.getModules
import fleetBuilder.util.api.kotlin.safeInvoke
import java.awt.Color
import java.util.*

internal object AutofitApplier {

    fun applyVariantInRefitScreen(
        baseVariant: ShipVariantAPI,
        loadout: ShipVariantAPI,
        fleetMember: FleetMemberAPI,
        ship: ShipAPI,
        shipDisplay: UIPanelAPI,
        refitPanel: UIPanelAPI,
        allowCargo: Boolean = true,
        allowStorage: Boolean = true,
        allowMarket: Boolean = true,
        allowBlackMarket: Boolean = true,
        applySMods: Boolean = false
    ) {
        shipDisplay.safeInvoke("setSuppressMessages", true)

        var appliedSMods = false

        try {
            baseVariant.source = VariantSource.REFIT

            if (baseVariant.getModules().keys != loadout.getModules().keys) {
                DisplayMessage.showError("Module slots between loadout and base variant's do not match. Stopping autofit to prevent crash.")
                return
            }

            if (Global.getCurrentState() != GameState.CAMPAIGN) {
                replaceVariantWithVariant(baseVariant, loadout, FBSettings.dontForceClearDMods, FBSettings.dontForceClearSMods)
            } else {
                val coreUI = ReflectionMisc.getCoreUI() ?: return

                val delegate = FBPlayerAutofitDelegate(
                    fleetMember,
                    Global.getSector().playerFaction,
                    ship,
                    coreUI,
                    shipDisplay
                )

                val interaction = Global.getSector()?.campaignUI?.currentInteractionDialog
                //Add market to delegate if present
                if (interaction != null && interaction.interactionTarget != null && interaction.interactionTarget.market != null) {
                    delegate.setMarket(interaction.interactionTarget.market)
                }

                //Strip items off ship first if in campaign. Both so they can be used by the autofitplugin, and so if using forceAutofit, the things on the ship aren't erased.
                val auto: CoreAutofitPlugin = CoreAutofitPlugin(fleetMember.fleetData.commander).apply { random = Random() }
                auto.setChecked(CoreAutofitPlugin.STRIP, false) // We do this manually
                var stripVariant = true

                val fleetMemory = fleetMember.fleetData?.fleet?.memoryWithoutUpdate
                if (fleetMemory != null) {
                    if (fleetMemory.contains("\$FBA_upgradeWeapons"))
                        auto.setChecked(CoreAutofitPlugin.UPGRADE, fleetMemory.getBoolean("\$FBA_upgradeWeapons"))

                    if (fleetMemory.contains("\$FBA_stripBeforeAutofit") && !FBSettings.forceAutofit)
                        stripVariant = fleetMemory.getBoolean("\$FBA_stripBeforeAutofit")
                }

                fun stripVaraint(variant: ShipVariantAPI, variantTo: ShipVariantAPI) {
                    if (stripVariant) {
                        variant.nonBuiltInWeaponSlots
                            .mapNotNull { variant.getSlot(it) }
                            .filter { variant.getWeaponId(it.id) != variantTo.getWeaponId(it.id) }//Only strip weapons that aren't in the right position
                            .forEach { auto.clearWeaponSlot(it, delegate, variant) }

                        variant.wings.indices
                            .drop(variant.hullSpec.builtInWings.size)
                            .filter { variant.getWingId(it) != variantTo.getWingId(it) }
                            .forEach { auto.clearFighterSlot(it, delegate, variant) }

                        variant.nonBuiltInHullmods
                            .filter { delegate.canAddRemoveHullmodInPlayerCampaignRefit(it) }
                            //.filter { !variantTo.hasHullMod(it) }//No need
                            .forEach { variant.removeMod(it) }
                    }
                    variant.numFluxCapacitors = 0
                    variant.numFluxVents = 0
                }

                stripVaraint(baseVariant, loadout)

                baseVariant.getModules().forEach { (slot, baseModule) ->
                    val loadoutModule = loadout.getModuleVariant(slot)
                        ?: throw Exception("loadout ship module was null")

                    stripVaraint(baseModule, loadoutModule)
                }

                if (FBSettings.forceAutofit) {
                    replaceVariantWithVariant(baseVariant, loadout, FBSettings.dontForceClearDMods, FBSettings.dontForceClearSMods)
                    //baseVariant.addTag(Tags.SHIP_RECOVERABLE)
                    //baseVariant.addTag(Tags.VARIANT_ALWAYS_RETAIN_SMODS_ON_SALVAGE)
                } else {
                    //Add submarkets to delegate if present
                    if (delegate.market != null) {
                        for (submarket in delegate.market!!.submarketsCopy) {
                            if (submarket.plugin.isHidden) continue
                            if (!submarket.plugin.isEnabled(coreUI)) continue
                            if (!submarket.plugin.showInCargoScreen()) continue
                            if (!allowMarket && !submarket.plugin.isFreeTransfer) continue
                            if (!allowBlackMarket && submarket.plugin.isBlackMarket) continue
                            if (!allowStorage && submarket.plugin.isFreeTransfer) continue

                            for (weapon in submarket.cargo.weapons) {
                                delegate.addAvailableWeapon(
                                    Global.getSettings().getWeaponSpec(weapon.item),
                                    weapon.count,
                                    submarket.cargo,
                                    submarket
                                )
                            }
                            for (fighter in submarket.cargo.fighters) {
                                delegate.addAvailableFighter(
                                    Global.getSettings().getFighterWingSpec(fighter.item),
                                    fighter.count,
                                    submarket.cargo,
                                    submarket
                                )
                            }
                        }
                    }

                    if (allowCargo) {
                        //Add player cargo weapons/fighters to delegate for AutofitPlugin to use.
                        for (weapon in Global.getSector().playerFleet.cargo.weapons) {
                            delegate.addAvailableWeapon(
                                Global.getSettings().getWeaponSpec(weapon.item),
                                weapon.count,
                                Global.getSector().playerFleet.cargo,
                                null
                            )
                        }
                        for (fighter in Global.getSector().playerFleet.cargo.fighters) {
                            delegate.addAvailableFighter(
                                Global.getSettings().getFighterWingSpec(fighter.item),
                                fighter.count,
                                Global.getSector().playerFleet.cargo,
                                null
                            )
                        }
                    }

                    if (applySMods) {
                        var (sModsToApply, bonusXpToGrant) = sModHandlerTemp(ship, baseVariant, loadout)
                        sModsToApply = sModsToApply.filter { delegate.canAddRemoveHullmodInPlayerCampaignRefit(it) }.toMutableList()
                        if (sModsToApply.isNotEmpty()) {
                            val itemManager = HullModItemManager.getInstance()
                            sModsToApply.toList().forEach {
                                if (!itemManager.isRequiredItemAvailable(it, ship.fleetMember, baseVariant, delegate.market)) {
                                    sModsToApply.remove(it)
                                    bonusXpToGrant -= getHullModBuildInBonusXP(baseVariant, it)
                                    DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_item"), Color.YELLOW)
                                    return@forEach
                                }

                                appliedSMods = true

                                if (baseVariant.hullSpec.builtInMods.contains(it)) {
                                    baseVariant.sModdedBuiltIns.add(it)
                                } else {
                                    baseVariant.addPermaMod(it, true)
                                }
                            }

                            spendStoryPoint(sModsToApply.size, bonusXpToGrant)
                        }
                    }

                    auto.doFit(baseVariant, loadout, 0, delegate)

                    //For some reason, vanilla starsector autofit does not fit hullmods into modules. I'm not sure why. So it has to be done ourselves.
                    loadout.getModules().forEach { (slot, loadoutModVariant) ->
                        val baseModVariant = baseVariant.getModuleVariant(slot)
                        baseModVariant.numFluxVents = 0
                        baseModVariant.numFluxCapacitors = 0
                        baseModVariant.clearHullMods()
                        auto.addVentsAndCaps(baseModVariant, loadoutModVariant, 1f)
                        auto.addHullmods(baseModVariant, delegate, *loadoutModVariant.nonBuiltInHullmods.toTypedArray())
                    }

                    if (auto.creditCost > 0) {
                        val creditString = Misc.getDGSCredits(auto.creditCost.toFloat())
                        DisplayMessage.showMessage(
                            FBTxt.txt("autofit_confirmed_purchased", creditString),
                            creditString, Misc.getHighlightColor()
                        )
                    }
                }
            }

        } catch (e: Exception) {
            DisplayMessage.showError(FBTxt.txt("failed_to_apply_variant"), e)
            //e.printStackTrace()
        }

        try {
            if (appliedSMods) {
                refitPanel.safeInvoke("saveCurrentVariant") // Prevent undo. Refunding SMods is difficult.
                refitPanel.safeInvoke("setEditedSinceSave", false)
            }
            refitPanel.safeInvoke("syncWithCurrentVariant")

            //try {
            shipDisplay.safeInvoke("updateModules")
            /*} catch (e: Exception) {
                DisplayMessage.showError("ERROR: " + FBTxt.txt("failed_to_apply_variant_update_modules"), e)
                baseVariant.stationModules.forEach { (slot, variantID) ->
                    //val moduleVariant = Global.getSettings().getVariant(variantID)
                    //baseVariant.setModuleVariant(slot, null)
                }
            }*/

            shipDisplay.safeInvoke("updateButtonPositionsToZoomLevel")
            //refitPanel.safeInvoke("recreateUI")
        } catch (e: Exception) {
            DisplayMessage.showError("ERROR: " + FBTxt.txt("failed_to_apply_variant"), e)
        }

        shipDisplay.safeInvoke("setSuppressMessages", false)
    }
}