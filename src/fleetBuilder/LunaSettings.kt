package fleetBuilder

import fleetBuilder.ModSettings.autofitMenuEnabled
import lunalib.lunaSettings.LunaSettings.getBoolean
import lunalib.lunaSettings.LunaSettings.getInt
import lunalib.lunaSettings.LunaSettings.getString
import lunalib.lunaSettings.LunaSettingsListener
import fleetBuilder.ModSettings.autofitMenuHotkey
import fleetBuilder.ModSettings.backupSave
import fleetBuilder.ModSettings.defaultPrefix
import fleetBuilder.ModSettings.dontForceClearDMods
import fleetBuilder.ModSettings.dontForceClearSMods
import fleetBuilder.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.ModSettings.forceAutofit
import fleetBuilder.ModSettings.importPrefix
import fleetBuilder.ModSettings.randomPastedCosmetics
import fleetBuilder.ModSettings.saveDMods
import fleetBuilder.ModSettings.saveSMods
import fleetBuilder.ModSettings.selectorsPerRow
import fleetBuilder.ModSettings.showCoreGoalVariants
import fleetBuilder.ModSettings.showCoreNonGoalVariants
import fleetBuilder.ModSettings.showDebug
import fleetBuilder.ModSettings.showHiddenModsInTooltip
import fleetBuilder.ModSettings.modID
import fleetBuilder.ModSettings.unassignPlayer
import fleetBuilder.variants.LoadoutManager.generatePrefixes
import org.lwjgl.input.Keyboard


class ModSettingsListener : LunaSettingsListener {
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
        if(_autofitMenuHotkey != 0) {
            autofitMenuHotkey = _autofitMenuHotkey
        }

        Reporter.onApplicationLoad()
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

    var autofitMenuEnabled = false

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