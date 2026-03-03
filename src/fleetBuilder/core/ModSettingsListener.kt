package fleetBuilder.core

import fleetBuilder.core.shipDirectory.ShipDirectoryService
import fleetBuilder.core.shipDirectory.ShipDirectoryService.generatePrefixes
import fleetBuilder.util.VariantLib
import lunalib.lunaSettings.LunaSettings.getBoolean
import lunalib.lunaSettings.LunaSettings.getInt
import lunalib.lunaSettings.LunaSettings.getString
import lunalib.lunaSettings.LunaSettingsListener

internal class ModSettingsListener : LunaSettingsListener {
    init {
        settingsChanged(ModSettings.modID)

        //Only happens once

        val _defaultPrefix = getString(ModSettings.modID, "defaultPrefix")!!

        if (generatePrefixes().contains(_defaultPrefix))
            ModSettings.defaultPrefix = _defaultPrefix

    }

    //Gets called whenever settings are saved in the campaign or the main menu.
    override fun settingsChanged(modID: String) {

        val featuresDisabled = getBoolean(modID, "featuresDisabled")!!

        if (!featuresDisabled) {
            ModSettings.selectorsPerRow = getInt(modID, "selectorsPerRow")!!
            ModSettings.showCoreGoalVariants = getBoolean(modID, "showCoreGoalVariants")!!
            ModSettings.showCoreNonGoalVariants = getBoolean(modID, "showCoreNonGoalVariants")!!
            ModSettings.showHiddenModsInTooltip = getBoolean(modID, "showHiddenModsInTooltip")!!
            ModSettings.showDebug = getBoolean(modID, "showDebug")!!
            ModSettings.saveDMods = getBoolean(modID, "saveDMods")!!
            ModSettings.saveSMods = getBoolean(modID, "saveSMods")!!
            ModSettings.saveHiddenMods = getBoolean(modID, "saveHiddenMods")!!
            ModSettings.forceAutofit = getBoolean(modID, "forceAutofit")!!
            ModSettings.dontForceClearDMods = getBoolean(modID, "dontForceClearDMods")!!
            ModSettings.dontForceClearSMods = getBoolean(modID, "dontForceClearSMods")!!
            ModSettings.randomPastedCosmetics = getBoolean(modID, "randomPastedCosmetics")!!
            ModSettings.backupSave = getBoolean(modID, "backupSave")!!
            ModSettings.hideErrorMessages = getBoolean(modID, "hideErrorMessages")!!
            ModSettings.fleetClipboardHotkeyHandler = getBoolean(modID, "fleetClipboardHotkeyHandler")!!
            ModSettings.devModeCodexButtonEnabled = getBoolean(modID, "devModeCodexButtonEnabled")!!
            ModSettings.fleetScreenFilter = getBoolean(modID, "fleetScreenFilter")!!
            ModSettings.storeOfficersInCargo = getBoolean(modID, "storeOfficersInCargo")!!
            ModSettings.removeDefaultDMods = getBoolean(modID, "removeDefaultDMods")!!
            ModSettings.cargoAutoManager = getBoolean(modID, "cargoAutoManager")!!
            ModSettings.modPickerFilter = getBoolean(modID, "modPickerFilter")!!
            ModSettings.reportCargoAutoManagerChanges = getBoolean(modID, "reportCargoAutoManagerChanges")!!
            ModSettings.autofitMenuEnabled = getBoolean(modID, "autofitMenuEnabled")!!
            ModSettings.replaceVanillaAutofitButton = getBoolean(modID, "replaceVanillaAutofitButton")!!
            ModSettings.removeRefitHullmod = getBoolean(modID, "removeRefitHullmod")!!
            ModSettings.autofitMenuHotkey = getInt(modID, "autofitMenuHotkey")!!
            ModSettings.autofitNoSModdedBuiltInWhenNotBuiltInMod = getBoolean(modID, "autofitNoSModdedBuiltInWhenNotBuiltInMod")!!
            ModSettings.reserveFirstFourAutofitSlots = getBoolean(modID, "reserveFirstFourAutofitSlots")!!
            ModSettings.autoMothballRecoveredShips = getBoolean(modID, "autoMothballRecoveredShips")!!

            ModSettings.setUnassignPlayer(getBoolean(modID, "unassignPlayer")!!)

            ModSettings.setCheatsEnabled(getBoolean(modID, "enableCheats")!!)

            if (VariantLib.Loaded())
                ShipDirectoryService.loadAllDirectories()//Reload the LoadoutManager
        } else {
            ModSettings.setUnassignPlayer(false)
            ModSettings.setCheatsEnabled(false)
            ModSettings.backupSave = false
            ModSettings.fleetClipboardHotkeyHandler = false
            ModSettings.devModeCodexButtonEnabled = false
            ModSettings.fleetScreenFilter = false
            ModSettings.storeOfficersInCargo = false
            ModSettings.cargoAutoManager = false
            ModSettings.modPickerFilter = false
            ModSettings.autofitMenuEnabled = false
            ModSettings.removeRefitHullmod = false
            ModSettings.autoMothballRecoveredShips = false
        }

        EventDispatcher.setListeners()
    }
}