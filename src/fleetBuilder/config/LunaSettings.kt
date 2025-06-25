package fleetBuilder.config

import fleetBuilder.config.ModSettings.autofitMenuEnabled
import fleetBuilder.config.ModSettings.autofitMenuHotkey
import fleetBuilder.config.ModSettings.backupSave
import fleetBuilder.config.ModSettings.defaultPrefix
import fleetBuilder.config.ModSettings.dontForceClearDMods
import fleetBuilder.config.ModSettings.dontForceClearSMods
import fleetBuilder.config.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.config.ModSettings.forceAutofit
import fleetBuilder.config.ModSettings.importPrefix
import fleetBuilder.config.ModSettings.modID
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.config.ModSettings.saveDMods
import fleetBuilder.config.ModSettings.saveSMods
import fleetBuilder.config.ModSettings.selectorsPerRow
import fleetBuilder.config.ModSettings.showCoreGoalVariants
import fleetBuilder.config.ModSettings.showCoreNonGoalVariants
import fleetBuilder.config.ModSettings.showDebug
import fleetBuilder.config.ModSettings.showHiddenModsInTooltip
import fleetBuilder.config.ModSettings.unassignPlayer
import fleetBuilder.util.Reporter
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.LoadoutManager.generatePrefixes
import lunalib.lunaSettings.LunaSettings.getBoolean
import lunalib.lunaSettings.LunaSettings.getInt
import lunalib.lunaSettings.LunaSettings.getString
import lunalib.lunaSettings.LunaSettingsListener
import org.lwjgl.input.Keyboard


internal class ModSettingsListener : LunaSettingsListener {
    init {
        settingsChanged(modID)

        //Only happens once

        val _defaultPrefix = getString(modID, "defaultPrefix")!!

        val _importPrefix = getString(modID, "importPrefix")!!

        if (generatePrefixes().contains(_defaultPrefix))
            defaultPrefix = _defaultPrefix
        if (generatePrefixes().contains(_importPrefix))
            importPrefix = _importPrefix

    }

    //Gets called whenever settings are saved in the campaign or the main menu.
    override fun settingsChanged(modID: String) {

        selectorsPerRow = getInt(modID, "selectorsPerRow")!!
        showCoreGoalVariants = getBoolean(modID, "showCoreGoalVariants")!!
        showCoreNonGoalVariants = getBoolean(modID, "showCoreNonGoalVariants")!!
        showHiddenModsInTooltip = getBoolean(modID, "showHiddenModsInTooltip")!!
        showDebug = getBoolean(modID, "showDebug")!!
        saveDMods = getBoolean(modID, "saveDMods")!!
        saveSMods = getBoolean(modID, "saveSMods")!!
        unassignPlayer = getBoolean(modID, "unassignPlayer")!!
        forceAutofit = getBoolean(modID, "forceAutofit")!!
        dontForceClearDMods = getBoolean(modID, "dontForceClearDMods")!!
        dontForceClearSMods = getBoolean(modID, "dontForceClearSMods")!!
        randomPastedCosmetics = getBoolean(modID, "randomPastedCosmetics")!!
        backupSave = getBoolean(modID, "backupSave")!!
        fleetClipboardHotkeyHandler = getBoolean(modID, "fleetClipboardHotkeyHandler")!!

        autofitMenuEnabled = getBoolean(modID, "autofitMenuEnabled")!!

        val _autofitMenuHotkey = getInt(modID, "autofitMenuHotkey")!!
        if (_autofitMenuHotkey != 0) {
            autofitMenuHotkey = _autofitMenuHotkey
        }

        LoadoutManager.loadAllDirectories()//Reload the LoadoutManager
    }
}

object ModSettings {
    val modID = "SN_FleetBuilder"

    var selectorsPerRow = 4

    var showDebug = false

    var showHiddenModsInTooltip = false

    var showCoreGoalVariants = true

    var showCoreNonGoalVariants = false

    var saveDMods = false

    var saveSMods = true

    var autofitMenuEnabled = true

    var autofitMenuHotkey = Keyboard.KEY_Z

    var randomPastedCosmetics = true

    var unassignPlayer = false

    var forceAutofit = false

    var dontForceClearDMods = false
    var dontForceClearSMods = false

    var defaultPrefix = "DF"

    var importPrefix = "IN"

    var backupSave = true

    var fleetClipboardHotkeyHandler = true

    val commandShuttleId = "FB_commandershuttle"
}