package fleetBuilder.config

import com.fs.starfarer.api.Global
import fleetBuilder.config.ModSettings.autofitMenuEnabled
import fleetBuilder.config.ModSettings.autofitMenuHotkey
import fleetBuilder.config.ModSettings.backupSave
import fleetBuilder.config.ModSettings.defaultPrefix
import fleetBuilder.config.ModSettings.devModeCodexButtonEnabled
import fleetBuilder.config.ModSettings.dontForceClearDMods
import fleetBuilder.config.ModSettings.dontForceClearSMods
import fleetBuilder.config.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.config.ModSettings.fleetScreenFilter
import fleetBuilder.config.ModSettings.forceAutofit
import fleetBuilder.config.ModSettings.importPrefix
import fleetBuilder.config.ModSettings.modID
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.config.ModSettings.removeDefaultDMods
import fleetBuilder.config.ModSettings.saveDMods
import fleetBuilder.config.ModSettings.saveHiddenMods
import fleetBuilder.config.ModSettings.saveSMods
import fleetBuilder.config.ModSettings.selectorsPerRow
import fleetBuilder.config.ModSettings.showCoreGoalVariants
import fleetBuilder.config.ModSettings.showCoreNonGoalVariants
import fleetBuilder.config.ModSettings.showDebug
import fleetBuilder.config.ModSettings.showHiddenModsInTooltip
import fleetBuilder.config.ModSettings.storeOfficersInCargo
import fleetBuilder.config.ModSettings.unassignPlayer
import fleetBuilder.util.Reporter
import fleetBuilder.util.containsString
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.LoadoutManager.generatePrefixes
import fleetBuilder.variants.VariantLib
import lunalib.lunaSettings.LunaSettings
import lunalib.lunaSettings.LunaSettings.getBoolean
import lunalib.lunaSettings.LunaSettings.getInt
import lunalib.lunaSettings.LunaSettings.getString
import lunalib.lunaSettings.LunaSettingsListener
import org.json.JSONArray
import org.json.JSONObject
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
        saveHiddenMods = getBoolean(modID, "saveHiddenMods")!!
        unassignPlayer = getBoolean(modID, "unassignPlayer")!!
        forceAutofit = getBoolean(modID, "forceAutofit")!!
        dontForceClearDMods = getBoolean(modID, "dontForceClearDMods")!!
        dontForceClearSMods = getBoolean(modID, "dontForceClearSMods")!!
        randomPastedCosmetics = getBoolean(modID, "randomPastedCosmetics")!!
        backupSave = getBoolean(modID, "backupSave")!!
        fleetClipboardHotkeyHandler = getBoolean(modID, "fleetClipboardHotkeyHandler")!!
        devModeCodexButtonEnabled = getBoolean(modID, "devModeCodexButtonEnabled")!!
        fleetScreenFilter = getBoolean(modID, "fleetScreenFilter")!!
        storeOfficersInCargo = getBoolean(modID, "storeOfficersInCargo")!!
        removeDefaultDMods = getBoolean(modID, "removeDefaultDMods")!!

        autofitMenuEnabled = getBoolean(modID, "autofitMenuEnabled")!!

        val _autofitMenuHotkey = getInt(modID, "autofitMenuHotkey")!!
        if (_autofitMenuHotkey != 0) {
            autofitMenuHotkey = _autofitMenuHotkey
        }

        if (VariantLib.Loaded())
            LoadoutManager.loadAllDirectories()//Reload the LoadoutManager

        Reporter.setListeners()
    }
}

object ModSettings {
    fun onApplicationLoad() {
        if (Global.getSettings().modManager.isModEnabled("lunalib"))
            LunaSettings.addSettingsListener(ModSettingsListener())

        isConsoleModEnabled = Global.getSettings().modManager.isModEnabled("lw_console")

        setNeverSaveHullmods()
    }

    private fun setNeverSaveHullmods() {
        val neverHullModsPath = "${PRIMARYDIR}HullModsToNeverSave"
        val neverHullModsJson = try {
            if (Global.getSettings().fileExistsInCommon(neverHullModsPath)) {
                Global.getSettings().readJSONFromCommon(neverHullModsPath, false)
            } else {
                JSONObject()
            }
        } catch (_: Exception) {
            JSONObject()
        }

        val hullModsToNeverSaveJSONArray = neverHullModsJson.optJSONArray("HullModsToNeverSave") ?: JSONArray()

        listOf("rat_controller", "rat_artifact_controller").forEach { mod ->
            if (!hullModsToNeverSaveJSONArray.containsString(mod)) {
                hullModsToNeverSaveJSONArray.put(mod)
            }
        }

        neverHullModsJson.put("HullModsToNeverSave", hullModsToNeverSaveJSONArray)

        Global.getSettings().writeJSONToCommon(neverHullModsPath, neverHullModsJson, false)


        hullModsToNeverSave = (0 until hullModsToNeverSaveJSONArray.length())
            .asSequence()
            .map { hullModsToNeverSaveJSONArray.getString(it) }
            .toSet()
    }

    const val PRIMARYDIR = "FleetBuilder/"
    const val PACKDIR = (PRIMARYDIR + "LoadoutPacks/")
    const val FLEETDIR = (PRIMARYDIR + "Fleets/")
    const val DIRECTORYCONFIGNAME = "directory"

    private var hullModsToNeverSave = setOf<String>()

    fun getHullModsToNeverSave(): Set<String> = hullModsToNeverSave

    val modID = "SN_FleetBuilder"

    var selectorsPerRow = 4

    var showDebug = false

    var showHiddenModsInTooltip = false

    var showCoreGoalVariants = true

    var showCoreNonGoalVariants = false

    var saveDMods = false

    var saveSMods = true

    var saveHiddenMods = false

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

    var devModeCodexButtonEnabled = true

    var fleetScreenFilter = false

    val commandShuttleId = "FB_commandershuttle"

    var isConsoleModEnabled = false

    var storeOfficersInCargo = false

    var removeDefaultDMods = true
}