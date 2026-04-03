package fleetBuilder.core.listener

import fleetBuilder.core.FBSettings
import fleetBuilder.features.autofit.shipDirectory.ShipDirectoryService
import fleetBuilder.util.LookupUtils
import lunalib.lunaSettings.LunaSettings.getBoolean
import lunalib.lunaSettings.LunaSettings.getInt
import lunalib.lunaSettings.LunaSettings.getString
import lunalib.lunaSettings.LunaSettingsListener
import org.apache.log4j.Level

internal class ModSettingsListener : LunaSettingsListener {
    init {
        settingsChanged(FBSettings.getModID())

        //Only happens once

        val _defaultPrefix = getString(FBSettings.getModID(), "defaultPrefix")!!

        if (ShipDirectoryService.generatePrefixes().contains(_defaultPrefix))
            FBSettings.defaultPrefix = _defaultPrefix

    }

    //Gets called whenever settings are saved in the campaign or the main menu.
    override fun settingsChanged(modID: String) {
        if (modID != FBSettings.getModID())
            return

        val featuresDisabled = getBoolean(modID, "featuresDisabled")!!

        if (!featuresDisabled) {
            val messageLevel = getString(modID, "addLogsToDisplayMessageLevel")!!
            val consoleLevel = getString(modID, "addLogsToConsoleModConsoleLevel")!!

            fun getLevel(level: String): Level? = when (level) {
                "FATAL" -> Level.FATAL
                "ERROR" -> Level.ERROR
                "WARN" -> Level.WARN
                "INFO" -> Level.INFO
                "DEBUG" -> Level.DEBUG
                "ALL" -> Level.ALL
                else -> Level.OFF
            }

            FBSettings.addLogsToDisplayMessageLevel = getLevel(messageLevel)
            FBSettings.addLogsToConsoleModConsoleLevel = getLevel(consoleLevel)

            FBSettings.selectorsPerRow = getInt(modID, "selectorsPerRow")!!
            FBSettings.showCoreGoalVariants = getBoolean(modID, "showCoreGoalVariants")!!
            FBSettings.showCoreNonGoalVariants = getBoolean(modID, "showCoreNonGoalVariants")!!
            FBSettings.showHiddenModsInTooltip = getBoolean(modID, "showHiddenModsInTooltip")!!
            FBSettings.showDebug = getBoolean(modID, "showDebug")!!
            FBSettings.enableDebug = getBoolean(modID, "enableDebug")!!
            FBSettings.saveDMods = getBoolean(modID, "saveDMods")!!
            FBSettings.saveSMods = getBoolean(modID, "saveSMods")!!
            FBSettings.saveHiddenMods = getBoolean(modID, "saveHiddenMods")!!
            FBSettings.forceAutofit = getBoolean(modID, "forceAutofit")!!
            FBSettings.dontForceClearDMods = getBoolean(modID, "dontForceClearDMods")!!
            FBSettings.dontForceClearSMods = getBoolean(modID, "dontForceClearSMods")!!
            FBSettings.randomPastedCosmetics = getBoolean(modID, "randomPastedCosmetics")!!
            FBSettings.backupSave = getBoolean(modID, "backupSave")!!
            FBSettings.hideErrorMessages = getBoolean(modID, "hideErrorMessages")!!
            FBSettings.fleetClipboardHotkeyHandler = getBoolean(modID, "fleetClipboardHotkeyHandler")!!
            FBSettings.devModeCodexButtonEnabled = getBoolean(modID, "devModeCodexButtonEnabled")!!
            FBSettings.fleetScreenFilter = getBoolean(modID, "fleetScreenFilter")!!
            FBSettings.storeOfficersInCargo = getBoolean(modID, "storeOfficersInCargo")!!
            FBSettings.removeDefaultDMods = getBoolean(modID, "removeDefaultDMods")!!
            FBSettings.cargoAutoManager = getBoolean(modID, "cargoAutoManager")!!
            FBSettings.modPickerFilter = getBoolean(modID, "modPickerFilter")!!
            FBSettings.cargoScreenFilter = getBoolean(modID, "cargoScreenFilter")!!
            FBSettings.reportCargoAutoManagerChanges = getBoolean(modID, "reportCargoAutoManagerChanges")!!
            FBSettings.autofitMenuEnabled = getBoolean(modID, "autofitMenuEnabled")!!
            FBSettings.codexAutofitButton = getBoolean(modID, "codexAutofitButton")!!
            FBSettings.replaceVanillaAutofitButton = getBoolean(modID, "replaceVanillaAutofitButton")!!
            FBSettings.removeRefitHullmod = getBoolean(modID, "removeRefitHullmod")!!
            FBSettings.autofitMenuHotkey = getInt(modID, "autofitMenuHotkey")!!
            FBSettings.autofitNoSModdedBuiltInWhenNotBuiltInMod = getBoolean(modID, "autofitNoSModdedBuiltInWhenNotBuiltInMod")!!
            FBSettings.reserveFirstFourAutofitSlots = getBoolean(modID, "reserveFirstFourAutofitSlots")!!
            FBSettings.autoMothballRecoveredShips = getBoolean(modID, "autoMothballRecoveredShips")!!
            FBSettings.transponderOffInHyperspace = getBoolean(modID, "transponderOffInHyperspace")!!
            FBSettings.recentBattleTracker = getBoolean(modID, "recentBattleTracker")!!
            FBSettings.showTagsInTooltip = getBoolean(modID, "showTagsInTooltip")!!

            FBSettings.setUnassignPlayer(getBoolean(modID, "unassignPlayer")!!)

            FBSettings.setCheatsEnabled(getBoolean(modID, "enableCheats")!!)

        } else {
            FBSettings.setUnassignPlayer(false)
            FBSettings.setCheatsEnabled(false)
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
            FBSettings.transponderOffInHyperspace = false
            FBSettings.addLogsToConsoleModConsoleLevel = Level.OFF
            FBSettings.addLogsToDisplayMessageLevel = Level.OFF
            FBSettings.recentBattleTracker = false
        }

        if (LookupUtils.isSetup())
            EventDispatcher.updateApplicationState()
    }
}