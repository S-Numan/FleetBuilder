package fleetBuilder.config

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Tags
import fleetBuilder.config.ModSettings.autofitMenuEnabled
import fleetBuilder.config.ModSettings.autofitMenuHotkey
import fleetBuilder.config.ModSettings.backupSave
import fleetBuilder.config.ModSettings.cargoAutoManager
import fleetBuilder.config.ModSettings.defaultPrefix
import fleetBuilder.config.ModSettings.devModeCodexButtonEnabled
import fleetBuilder.config.ModSettings.dontForceClearDMods
import fleetBuilder.config.ModSettings.dontForceClearSMods
import fleetBuilder.config.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.config.ModSettings.fleetScreenFilter
import fleetBuilder.config.ModSettings.forceAutofit
import fleetBuilder.config.ModSettings.importPrefix
import fleetBuilder.config.ModSettings.modID
import fleetBuilder.config.ModSettings.modPickerFilter
import fleetBuilder.config.ModSettings.randomPastedCosmetics
import fleetBuilder.config.ModSettings.removeDefaultDMods
import fleetBuilder.config.ModSettings.removeRefitHullmod
import fleetBuilder.config.ModSettings.reportCargoAutoManagerChanges
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
import fleetBuilder.persistence.variant.VariantSettings
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

        listOf("rat_controller", "rat_artifact_controller", "SCVE_officerdetails_X").forEach { mod ->
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

    fun getConfiguredVariantSettings(): VariantSettings {
        return VariantSettings().apply {
            applySMods = saveSMods
            includeDMods = saveDMods
            includeHiddenMods = saveHiddenMods
            excludeTagsWithID = getDefaultExcludeVariantTags()
        }
    }

    fun getDefaultExcludeVariantTags(): MutableSet<String> = mutableSetOf(Tags.SHIP_RECOVERABLE, Tags.TAG_RETAIN_SMODS_ON_RECOVERY, Tags.TAG_NO_AUTOFIT, Tags.VARIANT_CONSISTENT_WEAPON_DROPS)

    val modID = "SN_FleetBuilder"
    
    val modName = "FleetBuilder"


    var selectorsPerRow = 4

    var showDebug = false

    var showHiddenModsInTooltip = false

    var showCoreGoalVariants = true

    var showCoreNonGoalVariants = false

    var saveDMods = false

    var saveSMods = true

    var saveHiddenMods = true

    var autofitMenuEnabled = false

    var replaceVanillaAutofitButton = true

    var autofitMenuHotkey = Keyboard.KEY_V

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
    val storedOfficerTag = "\$FB_stored_officer"

    var isConsoleModEnabled = false

    var storeOfficersInCargo = false

    var removeDefaultDMods = true

    var cargoAutoManager = true

    var modPickerFilter = false

    var reportCargoAutoManagerChanges = true

    var removeRefitHullmod = true

    var autofitNoSModdedBuiltInWhenNotBuiltInMod = true

    var reserveFirstFourAutofitSlots = true

}