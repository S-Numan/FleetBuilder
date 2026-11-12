package fleetkit.core.config

import fleetkit.core.integration.listener.EventDispatcher
import fleetkit.core.integration.plugin.ModPlugin
import fleetkit.features.autofit.shipDirectory.ShipDirectoryService
import lunalib.lunaSettings.LunaSettings
import lunalib.lunaSettings.LunaSettingsListener

internal class LunaSettingsListener : LunaSettingsListener {
    var init = false

    init {
        settingsChanged(ModPlugin.getModID())

        //Only happens once

        val _defaultPrefix = LunaSettings.getString(ModPlugin.getModID(), "defaultPrefix")!!

        if (ShipDirectoryService.generatePrefixes().contains(_defaultPrefix))
            FTSettings.defaultPrefix = _defaultPrefix

        init = true
    }

    //Gets called whenever settings are saved in the campaign or the main menu.
    override fun settingsChanged(modID: String) {
        if (modID != ModPlugin.getModID())
            return

        val featuresDisabled = LunaSettings.getBoolean(modID, "featuresDisabled")!!

        if (!featuresDisabled) {
            FTSettings.selectorsPerRow = LunaSettings.getInt(modID, "selectorsPerRow")!!
            FTSettings.showCoreGoalVariants = LunaSettings.getBoolean(modID, "showCoreGoalVariants")!!
            FTSettings.showCoreNonGoalVariants = LunaSettings.getBoolean(modID, "showCoreNonGoalVariants")!!
            FTSettings.showHiddenModsInTooltip = LunaSettings.getBoolean(modID, "showHiddenModsInTooltip")!!
            FTSettings.showDebug = LunaSettings.getBoolean(modID, "showDebug")!!
            FTSettings.enableDebug = LunaSettings.getBoolean(modID, "enableDebug")!!
            FTSettings.autofitSaveDMods = LunaSettings.getBoolean(modID, "saveDMods")!!
            FTSettings.autofitApplySMods = LunaSettings.getBoolean(modID, "saveSMods")!!
            FTSettings.autofitSaveHiddenMods = LunaSettings.getBoolean(modID, "saveHiddenMods")!!
            FTSettings.forceAutofit = LunaSettings.getBoolean(modID, "forceAutofit")!!
            FTSettings.dontForceClearDMods = LunaSettings.getBoolean(modID, "dontForceClearDMods")!!
            FTSettings.dontForceClearSMods = LunaSettings.getBoolean(modID, "dontForceClearSMods")!!
            FTSettings.randomPastedCosmetics = LunaSettings.getBoolean(modID, "randomPastedCosmetics")!!
            FTSettings.backupSave = LunaSettings.getBoolean(modID, "backupSave")!!
            FTSettings.hideErrorMessages = LunaSettings.getBoolean(modID, "hideErrorMessages")!!
            FTSettings.fleetClipboardHotkeyHandler = LunaSettings.getBoolean(modID, "fleetClipboardHotkeyHandler")!!
            FTSettings.devModeCodexButtonEnabled = LunaSettings.getBoolean(modID, "devModeCodexButtonEnabled")!!
            FTSettings.fleetScreenFilter = LunaSettings.getBoolean(modID, "fleetScreenFilter")!!
            FTSettings.storeOfficersInCargo = LunaSettings.getBoolean(modID, "storeOfficersInCargo")!!
            FTSettings.cargoAutoManager = LunaSettings.getBoolean(modID, "cargoAutoManager")!!
            FTSettings.modPickerFilter = LunaSettings.getBoolean(modID, "modPickerFilter")!!
            FTSettings.cargoScreenFilter = LunaSettings.getBoolean(modID, "cargoScreenFilter")!!
            FTSettings.reportCargoAutoManagerChanges = LunaSettings.getBoolean(modID, "reportCargoAutoManagerChanges")!!
            FTSettings.autofitMenuEnabled = LunaSettings.getBoolean(modID, "autofitMenuEnabled")!!
            FTSettings.codexAutofitButton = LunaSettings.getBoolean(modID, "codexAutofitButton")!!
            FTSettings.replaceVanillaAutofitButton = LunaSettings.getBoolean(modID, "replaceVanillaAutofitButton")!!
            FTSettings.removeRefitHullmod = LunaSettings.getBoolean(modID, "removeRefitHullmod")!!
            FTSettings.autofitMenuHotkey = LunaSettings.getInt(modID, "autofitMenuHotkey")!!
            FTSettings.autofitNoSModdedBuiltInWhenNotBuiltInMod = LunaSettings.getBoolean(modID, "autofitNoSModdedBuiltInWhenNotBuiltInMod")!!
            FTSettings.reserveFirstFourAutofitSlots = LunaSettings.getBoolean(modID, "reserveFirstFourAutofitSlots")!!
            FTSettings.autoMothballRecoveredShips = LunaSettings.getBoolean(modID, "autoMothballRecoveredShips")!!
            FTSettings.removeOldIntelUpdates = LunaSettings.getBoolean(modID, "removeOldIntelUpdates")!!
            FTSettings.removeIntelUpdatesAfterXDays = LunaSettings.getInt(modID, "removeIntelUpdatesAfterXDays")!!
            FTSettings.transponderOffInHyperspace = LunaSettings.getBoolean(modID, "transponderOffInHyperspace")!!
            FTSettings.displayDerelictRecoveryEarly = LunaSettings.getBoolean(modID, "displayDerelictRecoveryEarly")!!
            FTSettings.recentBattleTracker = LunaSettings.getBoolean(modID, "recentBattleTracker")!!
            FTSettings.showTagsInTooltip = LunaSettings.getBoolean(modID, "showTagsInTooltip")!!

            FTSettings.setUnassignPlayer(LunaSettings.getBoolean(modID, "unassignPlayer")!!)

            FTSettings.setCheatsEnabledInSettings(LunaSettings.getBoolean(modID, "enableCheats")!!)

        } else {
            FTSettings.setUnassignPlayer(false)
            FTSettings.setCheatsEnabledInSettings(false)
            FTSettings.backupSave = false
            FTSettings.fleetClipboardHotkeyHandler = false
            FTSettings.devModeCodexButtonEnabled = false
            FTSettings.fleetScreenFilter = false
            FTSettings.storeOfficersInCargo = false
            FTSettings.cargoAutoManager = false
            FTSettings.modPickerFilter = false
            FTSettings.cargoScreenFilter = false
            FTSettings.autofitMenuEnabled = false
            FTSettings.removeRefitHullmod = false
            FTSettings.autoMothballRecoveredShips = false
            FTSettings.removeOldIntelUpdates = false
            FTSettings.transponderOffInHyperspace = false
            FTSettings.displayDerelictRecoveryEarly = false
            FTSettings.recentBattleTracker = false
        }

        if (init)
            EventDispatcher.updateApplicationState()
    }
}