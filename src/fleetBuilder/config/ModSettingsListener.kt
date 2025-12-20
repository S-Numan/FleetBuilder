package fleetBuilder.config

import fleetBuilder.config.ModSettings.autofitMenuEnabled
import fleetBuilder.config.ModSettings.autofitMenuHotkey
import fleetBuilder.config.ModSettings.autofitNoSModdedBuiltInWhenNotBuiltInMod
import fleetBuilder.config.ModSettings.backupSave
import fleetBuilder.config.ModSettings.cargoAutoManager
import fleetBuilder.config.ModSettings.defaultPrefix
import fleetBuilder.config.ModSettings.devModeCodexButtonEnabled
import fleetBuilder.config.ModSettings.dontForceClearDMods
import fleetBuilder.config.ModSettings.dontForceClearSMods
import fleetBuilder.config.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.config.ModSettings.fleetScreenFilter
import fleetBuilder.config.ModSettings.forceAutofit
import fleetBuilder.config.ModSettings.hideErrorMessages
import fleetBuilder.config.ModSettings.modID
import fleetBuilder.config.ModSettings.modPickerFilter
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.config.ModSettings.removeDefaultDMods
import fleetBuilder.config.ModSettings.removeRefitHullmod
import fleetBuilder.config.ModSettings.replaceVanillaAutofitButton
import fleetBuilder.config.ModSettings.reportCargoAutoManagerChanges
import fleetBuilder.config.ModSettings.reserveFirstFourAutofitSlots
import fleetBuilder.config.ModSettings.saveDMods
import fleetBuilder.config.ModSettings.saveHiddenMods
import fleetBuilder.config.ModSettings.saveSMods
import fleetBuilder.config.ModSettings.selectorsPerRow
import fleetBuilder.config.ModSettings.showCoreGoalVariants
import fleetBuilder.config.ModSettings.showCoreNonGoalVariants
import fleetBuilder.config.ModSettings.showDebug
import fleetBuilder.config.ModSettings.showHiddenModsInTooltip
import fleetBuilder.config.ModSettings.storeOfficersInCargo
import fleetBuilder.util.Reporter
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.LoadoutManager.generatePrefixes
import fleetBuilder.variants.VariantLib
import lunalib.lunaSettings.LunaSettings.getBoolean
import lunalib.lunaSettings.LunaSettings.getInt
import lunalib.lunaSettings.LunaSettings.getString
import lunalib.lunaSettings.LunaSettingsListener

internal class ModSettingsListener : LunaSettingsListener {
    init {
        settingsChanged(modID)

        //Only happens once

        val _defaultPrefix = getString(modID, "defaultPrefix")!!

        if (generatePrefixes().contains(_defaultPrefix))
            defaultPrefix = _defaultPrefix

    }

    //Gets called whenever settings are saved in the campaign or the main menu.
    override fun settingsChanged(modID: String) {

        val featuresDisabled = getBoolean(modID, "featuresDisabled")!!

        if (!featuresDisabled) {
            selectorsPerRow = getInt(modID, "selectorsPerRow")!!
            showCoreGoalVariants = getBoolean(modID, "showCoreGoalVariants")!!
            showCoreNonGoalVariants = getBoolean(modID, "showCoreNonGoalVariants")!!
            showHiddenModsInTooltip = getBoolean(modID, "showHiddenModsInTooltip")!!
            showDebug = getBoolean(modID, "showDebug")!!
            saveDMods = getBoolean(modID, "saveDMods")!!
            saveSMods = getBoolean(modID, "saveSMods")!!
            saveHiddenMods = getBoolean(modID, "saveHiddenMods")!!
            forceAutofit = getBoolean(modID, "forceAutofit")!!
            dontForceClearDMods = getBoolean(modID, "dontForceClearDMods")!!
            dontForceClearSMods = getBoolean(modID, "dontForceClearSMods")!!
            randomPastedCosmetics = getBoolean(modID, "randomPastedCosmetics")!!
            backupSave = getBoolean(modID, "backupSave")!!
            hideErrorMessages = getBoolean(modID, "hideErrorMessages")!!
            fleetClipboardHotkeyHandler = getBoolean(modID, "fleetClipboardHotkeyHandler")!!
            devModeCodexButtonEnabled = getBoolean(modID, "devModeCodexButtonEnabled")!!
            fleetScreenFilter = getBoolean(modID, "fleetScreenFilter")!!
            storeOfficersInCargo = getBoolean(modID, "storeOfficersInCargo")!!
            removeDefaultDMods = getBoolean(modID, "removeDefaultDMods")!!
            cargoAutoManager = getBoolean(modID, "cargoAutoManager")!!
            modPickerFilter = getBoolean(modID, "modPickerFilter")!!
            reportCargoAutoManagerChanges = getBoolean(modID, "reportCargoAutoManagerChanges")!!
            autofitMenuEnabled = getBoolean(modID, "autofitMenuEnabled")!!
            replaceVanillaAutofitButton = getBoolean(modID, "replaceVanillaAutofitButton")!!
            removeRefitHullmod = getBoolean(modID, "removeRefitHullmod")!!
            autofitMenuHotkey = getInt(modID, "autofitMenuHotkey")!!
            autofitNoSModdedBuiltInWhenNotBuiltInMod = getBoolean(modID, "autofitNoSModdedBuiltInWhenNotBuiltInMod")!!
            reserveFirstFourAutofitSlots = getBoolean(modID, "reserveFirstFourAutofitSlots")!!

            ModSettings.setUnassignPlayer(getBoolean(modID, "unassignPlayer")!!)

            ModSettings.setCheatsEnabled(getBoolean(modID, "enableCheats")!!)

            if (VariantLib.Loaded())
                LoadoutManager.loadAllDirectories()//Reload the LoadoutManager
        } else {
            ModSettings.setUnassignPlayer(false)
            ModSettings.setCheatsEnabled(false)
            backupSave = false
            fleetClipboardHotkeyHandler = false
            devModeCodexButtonEnabled = false
            fleetScreenFilter = false
            storeOfficersInCargo = false
            cargoAutoManager = false
            modPickerFilter = false
            autofitMenuEnabled = false
            removeRefitHullmod = false
        }

        Reporter.setListeners()
    }
}