package fleetBuilder.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import fleetBuilder.core.FBConst.PRIMARY_DIR
import fleetBuilder.core.listener.ModSettingsListener
import fleetBuilder.serialization.variant.VariantSettings
import fleetBuilder.util.api.kotlin.containsString
import lunalib.lunaSettings.LunaSettings
import org.apache.log4j.Level
import org.json.JSONArray
import org.json.JSONObject
import org.lwjgl.input.Keyboard

object FBSettings {
    fun onApplicationLoad() {
        modSpec = Global.getSettings().modManager.enabledModsCopy.find { it.modPluginClassName == FleetBuilderPlugin::class.java.name }!!

        if (Global.getSettings().modManager.isModEnabled("lunalib") && !LunaSettings.hasSettingsListenerOfClass(ModSettingsListener::class.java))
            LunaSettings.addSettingsListener(ModSettingsListener())

        isConsoleModEnabled = Global.getSettings().modManager.isModEnabled("lw_console")
    }

    fun setNeverSaveHullmods() {
        val neverHullModsPath = "${PRIMARY_DIR}HullModsToNeverSave"
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

        listOf(
            "rat_controller", "rat_artifact_controller", "SCVE_officerdetails_X", "sun_sl_notable",
            "sun_sl_wellknown", "sun_sl_famous", "sun_sl_legendary", "sun_sl_enemy_reputation",
        ).forEach { mod ->
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

    private var hullModsToNeverSave = setOf<String>()

    fun getHullModsToNeverSave(): Set<String> = hullModsToNeverSave

    fun getConfiguredAutofitSaveSettings(): VariantSettings {
        return VariantSettings().apply {
            applySMods = autofitApplySMods
            includeDMods = autofitSaveDMods
            includeHiddenMods = autofitSaveHiddenMods
            excludeTagsWithID = FBConst.DEFAULT_EXCLUDE_TAGS_ON_VARIANT_COPY.toMutableSet()
        }
    }

    private lateinit var modSpec: ModSpecAPI
    fun getModSpec(): ModSpecAPI = modSpec
    fun getModName(): String = modSpec.name
    fun getModID(): String = modSpec.id

    var addLogsToConsoleModConsoleLevel = Level.OFF
    var addLogsToDisplayMessageLevel = Level.OFF


    var selectorsPerRow = 4

    var showDebug = false
    var enableDebug = false

    var showHiddenModsInTooltip = false

    var showCoreGoalVariants = true

    var showCoreNonGoalVariants = false

    var autofitSaveDMods = false

    var autofitApplySMods = true

    var autofitSaveHiddenMods = true

    var autofitMenuEnabled = false

    var codexAutofitButton = true

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

    var isConsoleModEnabled = false

    var storeOfficersInCargo = false

    var removeDefaultDMods = true

    var cargoAutoManager = true

    var modPickerFilter = false

    var cargoScreenFilter = false

    var reportCargoAutoManagerChanges = true

    var removeRefitHullmod = true

    var autoMothballRecoveredShips = false

    var transponderOffInHyperspace = false

    var displayDerelictRecoveryEarly = false

    var autofitNoSModdedBuiltInWhenNotBuiltInMod = true

    var reserveFirstFourAutofitSlots = true

    var recentBattleTracker = false

    var showTagsInTooltip = false

    var fixShipSkinSourceMod = true

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