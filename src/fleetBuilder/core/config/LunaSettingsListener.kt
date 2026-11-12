package fleetBuilder.core.config

import fleetBuilder.core.integration.listener.EventDispatcher
import fleetBuilder.features.autofit.shipDirectory.ShipDirectoryService
import fleetBuilder.util.LookupUtils
import lunalib.lunaSettings.LunaSettings
import lunalib.lunaSettings.LunaSettingsListener

internal class LunaSettingsListener : LunaSettingsListener {
    init {
        settingsChanged(FBSettings.getModID())

        //Only happens once

        val _defaultPrefix = LunaSettings.getString(FBSettings.getModID(), "defaultPrefix")!!

        if (ShipDirectoryService.generatePrefixes().contains(_defaultPrefix))
            FBSettings.defaultPrefix = _defaultPrefix

    }

    //Gets called whenever settings are saved in the campaign or the main menu.
    override fun settingsChanged(modID: String) {
        if (modID != FBSettings.getModID())
            return

        val featuresDisabled = LunaSettings.getBoolean(modID, "featuresDisabled")!!

        if (!featuresDisabled) {
            FBSettings.selectorsPerRow = LunaSettings.getInt(modID, "selectorsPerRow")!!
            FBSettings.showCoreGoalVariants = LunaSettings.getBoolean(modID, "showCoreGoalVariants")!!
            FBSettings.showCoreNonGoalVariants = LunaSettings.getBoolean(modID, "showCoreNonGoalVariants")!!
            FBSettings.showHiddenModsInTooltip = LunaSettings.getBoolean(modID, "showHiddenModsInTooltip")!!
            FBSettings.showDebug = LunaSettings.getBoolean(modID, "showDebug")!!
            FBSettings.enableDebug = LunaSettings.getBoolean(modID, "enableDebug")!!
            FBSettings.autofitSaveDMods = LunaSettings.getBoolean(modID, "saveDMods")!!
            FBSettings.autofitApplySMods = LunaSettings.getBoolean(modID, "saveSMods")!!
            FBSettings.autofitSaveHiddenMods = LunaSettings.getBoolean(modID, "saveHiddenMods")!!
            FBSettings.forceAutofit = LunaSettings.getBoolean(modID, "forceAutofit")!!
            FBSettings.dontForceClearDMods = LunaSettings.getBoolean(modID, "dontForceClearDMods")!!
            FBSettings.dontForceClearSMods = LunaSettings.getBoolean(modID, "dontForceClearSMods")!!
            FBSettings.randomPastedCosmetics = LunaSettings.getBoolean(modID, "randomPastedCosmetics")!!
            FBSettings.backupSave = LunaSettings.getBoolean(modID, "backupSave")!!
            FBSettings.hideErrorMessages = LunaSettings.getBoolean(modID, "hideErrorMessages")!!
            FBSettings.fleetClipboardHotkeyHandler = LunaSettings.getBoolean(modID, "fleetClipboardHotkeyHandler")!!
            FBSettings.devModeCodexButtonEnabled = LunaSettings.getBoolean(modID, "devModeCodexButtonEnabled")!!
            FBSettings.fleetScreenFilter = LunaSettings.getBoolean(modID, "fleetScreenFilter")!!
            FBSettings.storeOfficersInCargo = LunaSettings.getBoolean(modID, "storeOfficersInCargo")!!
            FBSettings.cargoAutoManager = LunaSettings.getBoolean(modID, "cargoAutoManager")!!
            FBSettings.modPickerFilter = LunaSettings.getBoolean(modID, "modPickerFilter")!!
            FBSettings.cargoScreenFilter = LunaSettings.getBoolean(modID, "cargoScreenFilter")!!
            FBSettings.reportCargoAutoManagerChanges = LunaSettings.getBoolean(modID, "reportCargoAutoManagerChanges")!!
            FBSettings.autofitMenuEnabled = LunaSettings.getBoolean(modID, "autofitMenuEnabled")!!
            FBSettings.codexAutofitButton = LunaSettings.getBoolean(modID, "codexAutofitButton")!!
            FBSettings.replaceVanillaAutofitButton = LunaSettings.getBoolean(modID, "replaceVanillaAutofitButton")!!
            FBSettings.removeRefitHullmod = LunaSettings.getBoolean(modID, "removeRefitHullmod")!!
            FBSettings.autofitMenuHotkey = LunaSettings.getInt(modID, "autofitMenuHotkey")!!
            FBSettings.autofitNoSModdedBuiltInWhenNotBuiltInMod = LunaSettings.getBoolean(
                modID,
                "autofitNoSModdedBuiltInWhenNotBuiltInMod"
            )!!
            FBSettings.reserveFirstFourAutofitSlots = LunaSettings.getBoolean(modID, "reserveFirstFourAutofitSlots")!!
            FBSettings.autoMothballRecoveredShips = LunaSettings.getBoolean(modID, "autoMothballRecoveredShips")!!
            FBSettings.removeOldIntelUpdates = LunaSettings.getBoolean(modID, "removeOldIntelUpdates")!!
            FBSettings.removeIntelUpdatesAfterXDays = LunaSettings.getInt(modID, "removeIntelUpdatesAfterXDays")!!
            FBSettings.transponderOffInHyperspace = LunaSettings.getBoolean(modID, "transponderOffInHyperspace")!!
            FBSettings.displayDerelictRecoveryEarly = LunaSettings.getBoolean(modID, "displayDerelictRecoveryEarly")!!
            FBSettings.recentBattleTracker = LunaSettings.getBoolean(modID, "recentBattleTracker")!!
            FBSettings.showTagsInTooltip = LunaSettings.getBoolean(modID, "showTagsInTooltip")!!
            FBSettings.fixShipSkinSourceMod = LunaSettings.getBoolean(modID, "fixShipSkinSourceMod")!!
            FBSettings.cleanGameVariantsForRemovedElements = LunaSettings.getBoolean(
                modID,
                "cleanGameVariantsForRemovedElements"
            )!!

            FBSettings.setUnassignPlayer(LunaSettings.getBoolean(modID, "unassignPlayer")!!)

            FBSettings.setCheatsEnabledInSettings(LunaSettings.getBoolean(modID, "enableCheats")!!)

        } else {
            FBSettings.setUnassignPlayer(false)
            FBSettings.setCheatsEnabledInSettings(false)
            FBSettings.backupSave = false
            FBSettings.fleetClipboardHotkeyHandler = false
            FBSettings.devModeCodexButtonEnabled = false
            FBSettings.fleetScreenFilter = false
            FBSettings.storeOfficersInCargo = false
            FBSettings.cargoAutoManager = false
            FBSettings.modPickerFilter = false
            FBSettings.cargoScreenFilter = false
            FBSettings.autofitMenuEnabled = false
            FBSettings.removeRefitHullmod = false
            FBSettings.autoMothballRecoveredShips = false
            FBSettings.removeOldIntelUpdates = false
            FBSettings.transponderOffInHyperspace = false
            FBSettings.displayDerelictRecoveryEarly = false
            FBSettings.recentBattleTracker = false
            FBSettings.fixShipSkinSourceMod = false
            FBSettings.cleanGameVariantsForRemovedElements = false
        }

        if (LookupUtils.isSetup())
            EventDispatcher.updateApplicationState()
    }
}