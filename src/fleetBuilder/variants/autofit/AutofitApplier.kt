package fleetBuilder.variants.autofit

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.loading.WeaponGroupSpec
import com.fs.starfarer.api.plugins.impl.CoreAutofitPlugin
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.allDMods
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.util.getEffectiveHullId
import fleetBuilder.util.getRegularHullMods
import fleetBuilder.variants.VariantLib
import starficz.ReflectionUtils.invoke
import java.util.*

object AutofitApplier {

    fun applyVariantInRefitScreen(
        baseVariant: ShipVariantAPI,
        loadout: ShipVariantAPI,
        fleetMember: FleetMemberAPI,
        ship: ShipAPI,
        coreUI: CoreUIAPI,
        shipDisplay: UIPanelAPI,
        refitPanel: UIPanelAPI,
        allowCargo: Boolean = true,
        allowStorage: Boolean = true,
        allowMarket: Boolean = true,
        allowBlackMarket: Boolean = true
    ) {
        shipDisplay.invoke("setSuppressMessages", true)

        try {
            baseVariant.source = VariantSource.REFIT

            val delegate = FBPlayerAutofitDelegate(
                fleetMember,
                Global.getSector().playerFaction,
                ship,
                coreUI,
                shipDisplay
            )

            val auto: CoreAutofitPlugin

            if (!Global.getSettings().isInCampaignState) {
                replaceVariantWithVariant(baseVariant, loadout, ModSettings.dontForceClearDMods, ModSettings.dontForceClearSMods)

            } else {
                val ui = Global.getSector().campaignUI
                val interaction = ui.currentInteractionDialog

                //Add market to delegate if present
                if (interaction != null && interaction.interactionTarget != null && interaction.interactionTarget.market != null) {
                    val market = interaction.interactionTarget.market
                    delegate.setMarket(market)

                    for (submarket in market.submarketsCopy) {
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


                //Strip items off ship first if in campaign. Both so they can be used by the autofitplugin, and so if using forceAutofit, the things on the ship aren't erased.
                auto = CoreAutofitPlugin(fleetMember.fleetData.commander).apply { random = Random() }
                auto.setChecked(CoreAutofitPlugin.STRIP, false)

                fun stripVaraint(variant: ShipVariantAPI, variantTo: ShipVariantAPI) {
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
                    variant.numFluxCapacitors = 0
                    variant.numFluxVents = 0
                }

                stripVaraint(baseVariant, loadout)

                for (moduleSlot in baseVariant.moduleSlots) {
                    val baseModule = baseVariant.getModuleVariant(moduleSlot)
                    val loadoutModule = loadout.getModuleVariant(moduleSlot)
                    if (baseModule == null) {
                        throw Exception("base ship module was null")
                    } else if (loadoutModule == null) {
                        throw Exception("loadout ship module was null")
                    }
                    stripVaraint(baseModule, loadoutModule)
                }


                if (ModSettings.forceAutofit) {
                    replaceVariantWithVariant(baseVariant, loadout, ModSettings.dontForceClearDMods, ModSettings.dontForceClearSMods)
                    //baseVariant.addTag(Tags.SHIP_RECOVERABLE)
                    //baseVariant.addTag(Tags.VARIANT_ALWAYS_RETAIN_SMODS_ON_SALVAGE)
                } else {

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


                    auto.doFit(baseVariant, loadout, 0, delegate)

                    //For some reason, vanilla starsector autofit does not fit hullmods into modules. I'm not sure why. So it has to be done ourselves.
                    loadout.moduleSlots.forEach { slot ->
                        val loadoutModVariant = loadout.getModuleVariant(slot)
                        val baseModVariant = baseVariant.getModuleVariant(slot)
                        baseModVariant.numFluxVents = 0
                        baseModVariant.numFluxCapacitors = 0
                        baseModVariant.clearHullMods()
                        auto.addVentsAndCaps(baseModVariant, loadoutModVariant, 1f)
                        auto.addHullmods(baseModVariant, delegate, *loadoutModVariant.nonBuiltInHullmods.toTypedArray())
                    }

                    if (auto.creditCost > 0) {
                        DisplayMessage.showMessage(
                            "Autofit confirmed, purchased ${Misc.getDGSCredits(auto.creditCost.toFloat())} worth of ordnance",
                            Misc.getDGSCredits(auto.creditCost.toFloat()), Misc.getHighlightColor()
                        )
                    }
                }

            }
        } catch (e: Exception) {
            DisplayMessage.showError("ERROR: Failed to apply ship variant", e)
            //e.printStackTrace()
        }

        try {
            refitPanel.invoke("syncWithCurrentVariant")
            shipDisplay.invoke("updateModules")
            shipDisplay.invoke("updateButtonPositionsToZoomLevel")
        } catch (e: Exception) {
            DisplayMessage.showError("Failed to apply variant in refit screen", e)
        }

        shipDisplay.invoke("setSuppressMessages", false)
    }

    private fun replaceVariantWithVariant(
        to: ShipVariantAPI,
        from: ShipVariantAPI,
        dontForceClearDMods: Boolean = false,
        dontForceClearSMods: Boolean = false
    ) {
        if (to.hullSpec.getEffectiveHullId() != from.hullSpec.getEffectiveHullId()) {
            DisplayMessage.showError("Replace Variant With Variant failed. Base hulls '${to.hullSpec.getEffectiveHullId()}' and '${from.hullSpec.getEffectiveHullId()}' do not match.")
            return
        }
        to.clear()
        to.weaponGroups.clear()
        to.clearTags()
        to.nonBuiltInWeaponSlots.clear()
        to.nonBuiltInWings.clear()

        //Some things really don't want to go easily. Let's be extra sure.
        //to.clearHullMods()
        //to.sMods.clear()
        //to.permaMods.clear()
        //to.sModdedBuiltIns.clear()
        //to.suppressedMods.clear()
        to.hullMods.toList().forEach { mod ->
            //if (to.hullSpec.builtInMods.contains(mod))
            //    return@forEach
            if (dontForceClearSMods && to.sMods.contains(mod))
                return@forEach
            if (dontForceClearDMods && VariantLib.getAllDMods().contains(mod))
                return@forEach

            to.completelyRemoveMod(mod)
        }

        if (ModSettings.removeDefaultDMods) {
            to.allDMods().forEach {
                to.hullMods.remove(it)
            }
        }

        if (!dontForceClearSMods) {
            to.sModdedBuiltIns.clear()
        }
        /*to.weaponGroups.forEachIndexed { index, group ->
            for (i in group.slots.indices.reversed()) {
                to.weaponGroups[index].removeSlot(group.slots[i])
            }
        }*/

        // Copy tags
        for (tag in from.tags) {
            to.addTag(tag)
        }

        // Copy hullmod data
        for (mod in from.getRegularHullMods()) {
            to.addMod(mod)
        }

        // Copy perma-mods
        for (mod in from.permaMods) {
            to.addPermaMod(mod, false)
        }

        // Copy S-mods
        for (mod in from.sMods) {
            to.addPermaMod(mod, true)
        }

        // Copy S-modded built-ins
        for (mod in from.sModdedBuiltIns) {
            to.sModdedBuiltIns.add(mod)
        }

        // Copy Built-in DMods
        for (mod in from.allDMods()) {
            if (mod !in from.hullSpec.builtInMods) continue
            to.hullMods.add(mod)
        }

        for (mod in from.suppressedMods) {
            to.addSuppressedMod(mod)
        }

        // Copy weapon assignments
        for (slotId in from.nonBuiltInWeaponSlots) {
            val weapon = from.getWeaponId(slotId)
            if (weapon != null) {
                to.addWeapon(slotId, weapon)
            }
        }

        // Copy autofire groups
        from.weaponGroups.forEach { group ->
            val newGroup = WeaponGroupSpec()
            newGroup.isAutofireOnByDefault = group.isAutofireOnByDefault
            newGroup.type = group.type

            group.slots.forEach { slotId ->
                newGroup.addSlot(slotId)
            }

            to.weaponGroups.add(newGroup)
        }


        // Copy wings
        for (i in from.hullSpec.builtInWings.size until from.wings.size) {
            val wing = from.getWingId(i)
            if (wing != null) {
                to.setWingId(i, wing)
            }
        }

        // Copy flux vents/caps
        to.numFluxVents = from.numFluxVents
        to.numFluxCapacitors = from.numFluxCapacitors

        // Copy variant ID and display name
        to.hullVariantId = from.hullVariantId
        to.setVariantDisplayName(from.displayName)

        for (slot in from.moduleSlots) {
            val toVariant = to.getModuleVariant(slot)
            val fromVariant = from.getModuleVariant(slot)

            replaceVariantWithVariant(
                toVariant,
                fromVariant,
                dontForceClearDMods,
                dontForceClearSMods
            )
            to.setModuleVariant(slot, toVariant)
        }
    }
}