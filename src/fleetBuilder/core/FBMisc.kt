package fleetBuilder.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.campaign.HullModItemManager
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.loading.WeaponGroupSpec
import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.serialization.GameModInfo
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.MemberUtils
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.api.kotlin.*
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.console.overlay.v2.panels.ConsoleOverlayPanel
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.getOPCost
import java.awt.Color
import kotlin.math.min

internal object FBMisc {

    fun isConsoleOpen(): Boolean {
        if (!FBSettings.isConsoleModEnabled)
            return false
        return runCatching { ConsoleOverlayPanel.instance }.getOrNull() != null
    }

    fun getCallerClass(): Class<*>? {
        val thisClass = this::class.java.name
        var callerClass: String? = null

        return Throwable().stackTrace
            .asSequence()
            .map { it.className }
            .filter {
                it != thisClass
                        && !it.startsWith("java.")
                        && !it.startsWith("kotlin.")
                        && !it.contains("reflect")
            }
            .firstOrNull { className ->
                if (callerClass == null) {
                    // First non-this class = caller class
                    callerClass = className
                    false
                } else {
                    // Keep skipping while still in caller class
                    className != callerClass
                }
            }
            ?.let { runCatching { Class.forName(it) }.getOrNull() }
    }

    fun deepDiff(
        a: Any?,
        b: Any?,
        path: String = "root",
        visited: MutableSet<Pair<Int, Int>> = mutableSetOf(),
        depth: Int = 0,
        maxDepth: Int = 10
    ): List<String> {
        try {
            if (depth > maxDepth) return listOf("$path: <max depth reached>")

            val diffs = mutableListOf<String>()

            // Nulls
            if (a == null || b == null) {
                if (a != b) diffs += "$path: $a != $b"
                return diffs
            }

            // Prevent cycles
            val key = System.identityHashCode(a) to System.identityHashCode(b)
            if (!visited.add(key)) return diffs

            // Enums (EARLY — avoids reflection issues)
            if (a is Enum<*> && b is Enum<*>) {
                if (a != b) diffs += "$path: ${a.name} != ${b.name}"
                return diffs
            }

            // Primitives (EARLY)
            if (a is String || a is Number || a is Boolean) {
                if (a != b) diffs += "$path: $a != $b"
                return diffs
            }

            // Maps (BEFORE type check)
            if (a is Map<*, *> && b is Map<*, *>) {
                val allKeys = (a.keys + b.keys).toSet() // avoid duplicates
                for (k in allKeys) {
                    diffs += deepDiff(
                        a[k],
                        b[k],
                        "$path[$k]",
                        visited,
                        depth + 1,
                        maxDepth
                    )
                }
                return diffs
            }

            // Lists
            if (a is List<*> && b is List<*>) {
                val max = maxOf(a.size, b.size)
                for (i in 0 until max) {
                    diffs += deepDiff(
                        a.getOrNull(i),
                        b.getOrNull(i),
                        "$path[$i]",
                        visited,
                        depth + 1,
                        maxDepth
                    )
                }
                return diffs
            }

            // Sets
            if (a is Set<*> && b is Set<*>) {
                if (a != b) diffs += "$path: $a != $b"
                return diffs
            }

            // type mismatch
            if (a::class.java != b::class.java) {
                diffs += "$path: Type mismatch ${a::class.java} != ${b::class.java}"
                return diffs
            }

            // Reflection
            val skipNames = setOf(
                "this$0",
                "this$1",
                "\$stable",
                "Companion",
                "INSTANCE"
            )

            val fields = a.getFieldsMatching(searchSuperclass = true)

            for (field in fields) {
                if (field.name in skipNames) continue

                try {
                    val av = field.get(a)
                    val bv = field.get(b)

                    diffs += deepDiff(
                        av,
                        bv,
                        "$path.${field.name}",
                        visited,
                        depth + 1,
                        maxDepth
                    )
                } catch (e: Exception) {
                    diffs += "$path.${field.name}: <error: ${e.message}>"
                }
            }

            return diffs
        } catch (e: Exception) {
            DisplayMessage.showError("DeepDiff failed: ${e.message}")
        }
        return emptyList()
    }

    fun replaceVariantWithVariant(
        to: ShipVariantAPI,
        from: ShipVariantAPI,
        copyVariantID: Boolean = true,
        dontForceClearDMods: Boolean = false,
        dontForceClearSMods: Boolean = false
    ) {
        if (to.hullSpec.getEffectiveHullId() != from.hullSpec.getEffectiveHullId()) {
            DisplayMessage.showError("Replace Variant With Variant failed. Base hulls '${to.hullSpec.getEffectiveHullId()}' and '${from.hullSpec.getEffectiveHullId()}' do not match.")
            return
        }
        to.clear()
        to.weaponGroups.clear()
        to.clearTags()
        to.nonBuiltInWeaponSlots.clear()
        to.nonBuiltInWings.clear()
        to.fittedWeaponSlots.clear()

        to.hullMods.toList().forEach { mod ->
            if (dontForceClearSMods && to.sMods.contains(mod))
                return@forEach
            if (dontForceClearDMods && LookupUtils.getAllDMods().contains(mod))
                return@forEach

            to.completelyRemoveMod(mod)
        }

        if (FBSettings.removeDefaultDMods) {
            to.allDMods().forEach {
                to.hullMods.remove(it)
            }
        }

        if (!dontForceClearSMods) {
            to.sModdedBuiltIns.clear()
        }
        /*to.weaponGroups.forEachIndexed { index, group ->
            for (i in group.slots.indices.reversed()) {
                to.weaponGroups[index].removeSlot(group.slots[i])
            }
        }*/

        // Copy tags
        for (tag in from.tags) {
            if (!tag.startsWith("#"))
                to.addTag(tag)
        }

        // Copy hullmod data
        for (mod in from.getRegularHullMods()) {
            to.addMod(mod)
        }

        // Copy perma-mods
        for (mod in from.permaMods) {
            to.addPermaMod(mod, false)
        }

        // Copy S-mods
        for (mod in from.sMods) {
            to.addPermaMod(mod, true)
        }

        // Copy S-modded built-ins
        for (mod in from.sModdedBuiltIns) {
            to.sModdedBuiltIns.add(mod)
        }

        // Copy Built-in DMods
        for (mod in from.allDMods()) {
            if (mod !in from.hullSpec.builtInMods) continue
            to.hullMods.add(mod)
        }

        for (mod in from.suppressedMods) {
            to.addSuppressedMod(mod)
        }

        // Copy weapon assignments
        for (slotId in from.nonBuiltInWeaponSlots) {
            val weapon = from.getWeaponId(slotId)
            if (weapon != null) {
                to.addWeapon(slotId, weapon)
            }
        }

        // Copy autofire groups
        from.weaponGroups.forEach { group ->
            val newGroup = WeaponGroupSpec()
            newGroup.isAutofireOnByDefault = group.isAutofireOnByDefault
            newGroup.type = group.type

            group.slots.forEach { slotId ->
                newGroup.addSlot(slotId)
            }

            to.weaponGroups.add(newGroup)
        }


        // Copy wings
        for (i in from.hullSpec.builtInWings.size until from.wings.size) {
            val wing = from.getWingId(i)
            if (wing != null) {
                to.setWingId(i, wing)
            }
        }

        // Copy flux vents/caps
        to.numFluxVents = from.numFluxVents
        to.numFluxCapacitors = from.numFluxCapacitors

        // Copy variant ID and display name
        if (copyVariantID)
            to.hullVariantId = from.hullVariantId
        to.setVariantDisplayName(from.displayName)
        to.source = from.source

        from.getModules(true).forEach { (slot, fromVariant) ->
            val toVariant = runCatching { to.getModuleVariant(slot) }.getOrNull()?.clone()
            if (toVariant == null) return@forEach

            replaceVariantWithVariant(
                toVariant,
                fromVariant.clone().apply { if (isStockVariant) source = VariantSource.REFIT }, // Have to avoid stock module variants, they will cause a game crash due to vanilla code removing them.
                copyVariantID,
                dontForceClearDMods,
                dontForceClearSMods
            )
            to.setModuleVariant(slot, toVariant)
        }
    }

    fun mapToJsonObject(map: Map<*, *>): JSONObject {
        val json = JSONObject()

        for ((key, value) in map) {
            // JSON keys must be strings
            val stringKey = key?.toString() ?: continue

            json.put(
                stringKey, when (value) {
                    null -> JSONObject.NULL
                    is Map<*, *> -> mapToJsonObject(value)
                    is List<*> -> listToJsonArray(value)
                    else -> value
                }
            )
        }

        return json
    }

    fun listToJsonArray(list: List<*>): JSONArray {
        val array = JSONArray()

        for (value in list) {
            array.put(
                when (value) {
                    null -> JSONObject.NULL
                    is Map<*, *> -> mapToJsonObject(value)
                    is List<*> -> listToJsonArray(value)
                    else -> value
                }
            )
        }

        return array
    }

    fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !is String) continue
            val value = json.get(key)

            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }

        return map
    }

    fun jsonArrayToList(array: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()

        for (i in 0 until array.length()) {
            val value = array.get(i)

            list.add(
                when (value) {
                    is JSONObject -> jsonObjectToMap(value)
                    is JSONArray -> jsonArrayToList(value)
                    JSONObject.NULL -> null
                    else -> value
                }
            )
        }

        return list
    }


    fun SpecialItemData.getSpecialItemName(): String? {
        return when (id) {
            "fighter_bp" ->
                Global.getSettings().allFighterWingSpecs.find { it.id == data }?.wingName
            "weapon_bp" ->
                Global.getSettings().allWeaponSpecs.find { it.weaponId == data }?.weaponName
            "ship_bp" ->
                Global.getSettings().allShipHullSpecs.find { it.hullId == data }?.hullName
            "modspec" ->
                Global.getSettings().allHullModSpecs.find { it.id == data }?.displayName
            "industry_bp" ->
                Global.getSettings().allIndustrySpecs.find { it.id == data }?.name

            else -> null
        }
    }

    // Untested
    fun getAllSkinVariants(baseHull: String): MutableList<String?> {
        val out: MutableList<String?> = ArrayList()

        for (s in Global.getSettings().allShipHullSpecs) {
            if (s.isDefaultDHull) continue
            if (s.fuel <= 0) continue
            val id = s.hullId
            if (id.contains(baseHull) && (id != baseHull) && (!out.contains(id))) {
                out.add(id)
            }
        }
        return out
    }

    // Untested
    fun getIntelOfClass(c: Class<*>): IntelInfoPlugin? {
        try {
            if (!Global.getSector().intelManager.hasIntelOfClass(c)) {
                Global.getSector().intelManager.addIntel(c.getDeclaredConstructor().newInstance() as IntelInfoPlugin) // Global.getSector().intelManager.addIntel(c.newInstance() as IntelInfoPlugin)
            }
        } catch (ex: Exception) {
        }
        return Global.getSector().intelManager.getFirstIntel(c)
    }

    // Untested
    fun getIntelByName(name: String?): IntelInfoPlugin? {
        for (i1 in Global.getSector().intelManager.intel) {
            val n1 = i1.javaClass.getSimpleName()
            if (n1 == name) {
                return i1
            }
        }
        return null
    }

    fun startStencilWithYPad(panel: CustomPanelAPI, yPad: Float) {
        GL11.glClearStencil(0)
        GL11.glStencilMask(0xff)
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)

        GL11.glColorMask(false, false, false, false)
        GL11.glEnable(GL11.GL_STENCIL_TEST)

        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xff)
        GL11.glStencilMask(0xff)
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE)

        GL11.glBegin(GL11.GL_POLYGON)
        val position = panel.getPosition()
        val x = position.getX() - 5
        val y = position.getY()
        val width = position.getWidth() + 10
        val height = position.getHeight()

        // Define the rectangle
        GL11.glVertex2f(x, y)
        GL11.glVertex2f(x + width, y)
        GL11.glVertex2f(x + width, y + height - yPad)
        GL11.glVertex2f(x, y + height - yPad)
        GL11.glEnd()

        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
        GL11.glColorMask(true, true, true, true)

        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
    }

    fun startStencilWithXPad(panel: CustomPanelAPI, xPad: Float) {
        GL11.glClearStencil(0)
        GL11.glStencilMask(0xff)
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)

        GL11.glColorMask(false, false, false, false)
        GL11.glEnable(GL11.GL_STENCIL_TEST)

        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xff)
        GL11.glStencilMask(0xff)
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE)

        GL11.glBegin(GL11.GL_POLYGON)
        val position = panel.getPosition()
        val x = position.getX() - 5
        val y = position.getY() - 10
        val width = position.getWidth() + 10
        val height = position.getHeight() + 20

        // Define the rectangle
        GL11.glVertex2f(x, y)
        GL11.glVertex2f(x + width - xPad, y)
        GL11.glVertex2f(x + width - xPad, y + height)
        GL11.glVertex2f(x, y + height)
        GL11.glEnd()

        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
        GL11.glColorMask(true, true, true, true)

        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
    }

    fun endStencil() {
        GL11.glDisable(GL11.GL_STENCIL_TEST)
    }

    fun renderTiledTexture(
        textureId: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tileWidth: Float,
        tileHeight: Float,
        alphaMult: Float,
        color: Color
    ) {
        if (textureId == 0) {
            DisplayMessage.showError("Error: Invalid texture ID.")
            return
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)

        // Enable blending for alpha transparency
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        // Set the texture to repeat
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)

        // Set nearest neighbor filtering to preserve the lines
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)

        // Calculate texture repeat factors based on the panel's size and the texture's tile size
        val uMax: Float = width / tileWidth // Repeat in the X direction
        val vMax: Float = height / tileHeight // Repeat in the Y direction

        // Set color with alpha transparency
        GL11.glColor4f(color.red.toFloat(), color.green.toFloat(), color.blue.toFloat(), alphaMult)

        // Render the panel with tiling
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(x, y) // Bottom-left
        GL11.glTexCoord2f(uMax, 0f)
        GL11.glVertex2f(x + width, y) // Bottom-right
        GL11.glTexCoord2f(uMax, vMax)
        GL11.glVertex2f(x + width, y + height) // Top-right
        GL11.glTexCoord2f(0f, vMax)
        GL11.glVertex2f(x, y + height) // Top-left
        GL11.glEnd()

        // Reset color to fully opaque to avoid affecting other renders
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)

        // Disable blending and textures
        GL11.glDisable(GL11.GL_BLEND)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
    }

    fun getModuleSlotsFromVariantFile(filepath: String): HashMap<String, String> {
        val moduleSlots = HashMap<String, String>()

        try {
            val fixedFilePath = filepath.replace("\\", "/")
            val jsonObject = Global.getSettings().loadJSON(fixedFilePath)

            if (jsonObject.has("modules")) {
                val modulesArray = jsonObject.getJSONArray("modules")

                for (i in 0..<modulesArray.length()) {
                    val moduleJSON = modulesArray.getJSONObject(i)

                    val keys = moduleJSON.keys()
                    while (keys.hasNext()) {
                        val key = keys.next() as? String ?: continue
                        val value = moduleJSON.getString(key)

                        moduleSlots[key] = value
                    }
                }

            }

        } catch (e: Exception) {

        }

        return moduleSlots
    }

    fun sModHandlerTemp(
        ship: ShipAPI,
        baseVariant: ShipVariantAPI,
        loadout: ShipVariantAPI
    ): Pair<List<String>, Float> {
        val coreUI = ReflectionMisc.getCoreUI() as? CoreUIAPI ?: return emptyList<String>() to 0f

        val playerSPLeft = Global.getSector().playerStats.storyPoints

        var maxSMods = MemberUtils.getMaxSMods(ship.mutableStats)
        val currentSMods = (baseVariant.sMods + baseVariant.sModdedBuiltIns).toSet()
        val newSMods = (loadout.sMods + loadout.sModdedBuiltIns).toSet()
        var sModsToApply = newSMods.filter { it !in currentSMods }

        // Filter out SMods that are sModdedBuiltIns in the loadout, but not a built-in hullmod in the baseVariant. See Mad Rockpiper MIDAS from Roider Union for why this is done. Also, sometimes variant skins have built in hullmods that can be sModded.
        sModsToApply = sModsToApply.filterNot { it in loadout.sModdedBuiltIns && !baseVariant.hullSpec.builtInMods.contains(it) }
        if (currentSMods.count { it !in baseVariant.hullSpec.builtInMods } + sModsToApply.count { it !in loadout.hullSpec.builtInMods } > maxSMods) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_build_in_slots"), Color.YELLOW)
            return emptyList<String>() to 0f
        }
        maxSMods = min(maxSMods, playerSPLeft)

        if (sModsToApply.size > maxSMods) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_story_point"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        var canApplySMods: List<String> = sModsToApply.filter { Global.getSector().playerFaction.knowsHullMod(it) }

        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_knowledge"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        /*val requiredItems = sModsToApply.mapNotNull { Global.getSettings().getHullModSpec(it).effect.requiredItem }
        val numAvailable = requiredItems.sumOf { HullModItemManager.getInstance().getNumAvailableMinusUnconfirmed(it, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket) }
        if (numAvailable < sModsToApply.size) {
            DisplayMessage.showMessage("Cannot apply some SMods. Lacking required items ...", Color.YELLOW)
            return emptyList<String>() to 0f
        }

        sModsToApply.forEach { modID ->
            val modSpec = Global.getSettings().getHullModSpec(modID)
            if (modSpec.effect.requiredItem == null) return@forEach
            modSpec.effect.requiredItem
            val numAvailable = HullModItemManager.getInstance().getNumAvailableMinusUnconfirmed(modSpec.effect.requiredItem, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket)
        }*/

        canApplySMods = sModsToApply.filter { HullModItemManager.getInstance().isRequiredItemAvailable(it, ship.fleetMember, baseVariant, Global.getSector().currentlyOpenMarket) }
        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_item"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        canApplySMods = sModsToApply.filter { Global.getSettings().getHullModSpec(it).effect.canBeAddedOrRemovedNow(ship, Global.getSector().currentlyOpenMarket, coreUI.tradeMode) }

        if (sModsToApply.size != canApplySMods.size) {
            DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_dock"), Color.YELLOW)
            return emptyList<String>() to 0f
        }

        sModsToApply.forEach { modID ->
            if (baseVariant.hullSpec.getOrdnancePoints(null) < Global.getSettings().getHullModSpec(modID).getOPCost(baseVariant.hullSize)) {
                DisplayMessage.showMessage(FBTxt.txt("cannot_apply_smod_lack_op"), Color.YELLOW)
                return emptyList<String>() to 0f
            }
        }

        var bonusXpToGrant = 0f
        sModsToApply.forEach { modID ->
            bonusXpToGrant += VariantUtils.getHullModBuildInBonusXP(baseVariant, modID)
        }

        return sModsToApply to bonusXpToGrant
    }

    fun getModInfosFromJson(json: JSONObject, onlyMissing: Boolean = false): MutableSet<GameModInfo> {
        val gameMods = mutableSetOf<GameModInfo>()
        json.optJSONArray("mod_info")?.let {
            repeat(it.length()) { i ->
                val modSpecJson = it.optJSONObject(i)
                val modSpecId = modSpecJson.optString("mod_id")
                val modSpecName = modSpecJson.optString("mod_name")
                val modSpecVersion = modSpecJson.optString("mod_version")

                var hasMod = false
                for (modSpecAPI in Global.getSettings().modManager.enabledModsCopy) {
                    if (modSpecAPI.id == modSpecId) {
                        hasMod = true
                        break
                    }
                }

                if (onlyMissing && hasMod) return@repeat

                gameMods.add(GameModInfo(modSpecId, modSpecName, modSpecVersion))
            }
        }
        return gameMods
    }
}