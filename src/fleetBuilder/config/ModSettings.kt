package fleetBuilder.config

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Tags
import fleetBuilder.persistence.variant.VariantSettings
import fleetBuilder.util.containsString
import lunalib.lunaSettings.LunaSettings
import org.json.JSONArray
import org.json.JSONObject
import org.lwjgl.input.Keyboard

object ModSettings {
    fun onApplicationLoad() {
        if (Global.getSettings().modManager.isModEnabled("lunalib") && !LunaSettings.hasSettingsListenerOfClass(ModSettingsListener::class.java))
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

        listOf("rat_controller", "rat_artifact_controller", "SCVE_officerdetails_X", "sun_sl_notable", "sun_sl_wellknown", "sun_sl_famous", "sun_sl_legendary", "sun_sl_enemy_reputation").forEach { mod ->
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

    var forceAutofit = false

    var dontForceClearDMods = false
    var dontForceClearSMods = false

    var defaultPrefix = "DF"

    var backupSave = true

    var hideErrorMessages = false

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

    private var unassignPlayer = false
    fun unassignPlayer(): Boolean = unassignPlayer || cheatsEnabled()
    fun setUnassignPlayer(value: Boolean) {
        unassignPlayer = value
    }

    private var cheatsEnabled = false
    fun cheatsEnabled(): Boolean = cheatsEnabled || Global.getSettings().isDevMode
    fun setCheatsEnabled(value: Boolean) {
        cheatsEnabled = value
    }

}