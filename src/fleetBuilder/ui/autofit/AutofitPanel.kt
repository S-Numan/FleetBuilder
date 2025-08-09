package fleetBuilder.ui.autofit

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.config.ModSettings
import fleetBuilder.util.ClipboardMisc
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.allDMods
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.LoadoutManager.deleteLoadoutVariant
import fleetBuilder.variants.LoadoutManager.getAllAutofitSpecsForShip
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib.compareVariantContents
import fleetBuilder.variants.VariantLib.compareVariantHullMods
import fleetBuilder.variants.VariantLib.getAllDMods
import fleetBuilder.variants.VariantLib.processSModsForComparison
import fleetBuilder.variants.autofit.AutofitApplier.applyVariantInRefitScreen
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.alphaf
import org.magiclib.kotlin.bluef
import org.magiclib.kotlin.greenf
import org.magiclib.kotlin.redf
import starficz.*
import starficz.ReflectionUtils.invoke
import java.awt.Color


/**
 * Original author
 * @author Starficz
 */
internal object AutofitPanel {
    private const val BACKGROUND_ALPHA = 0.7f

    internal class AutofitPanelPlugin(private val refitTab: UIPanelAPI) : BaseCustomUIPanelPlugin() {
        lateinit var autofitPanel: CustomPanelAPI

        override fun renderBelow(alphaMult: Float) {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            // background dark fadeout
            val bgColor = Color.BLACK
            val bgAlpha = BACKGROUND_ALPHA * bgColor.alphaf * alphaMult
            GL11.glColor4f(bgColor.redf, bgColor.greenf, bgColor.bluef, bgAlpha)
            GL11.glRectf(0f, 0f, Global.getSettings().screenWidth, Global.getSettings().screenHeight)

            // vanilla panels are transparent, but paintjobs need a clear background for display purposes
            val panelColor = Color.BLACK
            val panelAlpha = panelColor.alphaf * alphaMult
            GL11.glColor4f(panelColor.redf, panelColor.greenf, panelColor.bluef, panelAlpha)
            GL11.glRectf(autofitPanel.left, autofitPanel.bottom, autofitPanel.right, autofitPanel.top)

            GL11.glDisable(GL11.GL_BLEND)

            // need to redraw outer border, I think vanilla moves the height of the existing border back and forth,
            // but doing that in mod code is way harder than redrawing one
            val borderColor = Misc.getDarkPlayerColor()
            val borderAlpha = borderColor.alphaf * alphaMult
            GL11.glColor4f(borderColor.redf, borderColor.greenf, borderColor.bluef, borderAlpha)
            drawBorder(refitTab.left, refitTab.bottom, refitTab.right, refitTab.top)

            // the panel border itself is darker than standard player dark color
            val darkerBorderColor = borderColor.darker()
            val darkerBorderAlpha = darkerBorderColor.alphaf * alphaMult
            GL11.glColor4f(darkerBorderColor.redf, darkerBorderColor.greenf, darkerBorderColor.bluef, darkerBorderAlpha)
            drawBorder(autofitPanel.left, autofitPanel.bottom, autofitPanel.right, autofitPanel.top)

            GL11.glPopMatrix()
        }

        private fun drawBorder(x1: Float, y1: Float, x2: Float, y2: Float) {
            GL11.glRectf(x1, y1, x2 + 1, y1 - 1)
            GL11.glRectf(x2, y1, x2 + 1, y2 + 1)
            GL11.glRectf(x1, y2, x1 - 1, y1 - 1)
            GL11.glRectf(x2, y2, x1 - 1, y2 + 1)
        }

        override fun processInput(events: MutableList<InputEventAPI>?) {
            for (event in events!!) {
                if (!event.isConsumed && event.isKeyboardEvent && event.eventValue == Keyboard.KEY_ESCAPE) {
                    autofitPanel.parent?.removeComponent(autofitPanel)
                    event.consume()
                } else if (!event.isConsumed && (event.isKeyboardEvent || event.isMouseMoveEvent ||
                            event.isMouseDownEvent || event.isMouseScrollEvent)
                ) {
                    event.consume()
                }
            }
        }

        override fun advance(amount: Float) {
        }
    }

    internal fun createMagicAutofitPanel(
        refitTab: UIPanelAPI, refitPanel: UIPanelAPI, coreUI: CoreUIAPI,
        width: Float, height: Float
    ): CustomPanelAPI {

        val paintjobPlugin = AutofitPanelPlugin(refitTab)
        val autofitPanel = Global.getSettings().createCustom(width, height, paintjobPlugin)// Background Panel
        paintjobPlugin.autofitPanel = autofitPanel

        // borders are drawn outside of panel, so +2 needed to lineup scrollbar with border
        val scrollerTooltip = autofitPanel.createUIElement(width + 2f, height, true) // Tooltip on background panel
        scrollerTooltip.position.inTL(0f, 0f)

        val shipDisplay = refitPanel.invoke("getShipDisplay") as? UIPanelAPI ?: return autofitPanel
        val baseVariant = shipDisplay.invoke("getCurrentVariant") as? HullVariantSpec
            ?: return autofitPanel
        val fleetMember = refitPanel.invoke("getMember") as? FleetMemberAPI ?: return autofitPanel
        val ship = shipDisplay.invoke("getShip") as? ShipAPI ?: return autofitPanel

        val effectiveHullAutofitSpecs = getAllAutofitSpecsForShip((baseVariant as ShipVariantAPI).hullSpec)

        val endPad = 6f
        val midPad = 5f
        val selectorsPerRow = ModSettings.selectorsPerRow
        val selectorWidth = (autofitPanel.width - (endPad * 2 + midPad * (selectorsPerRow - 1))) / selectorsPerRow

        var firstInRow: UIPanelAPI? = null
        var prev: UIPanelAPI? = null

        val selectorPlugins = mutableListOf<AutofitSelector.AutofitSelectorPlugin>()

        // Calculate how many items in the last row so far
        val remainder = effectiveHullAutofitSpecs.size % selectorsPerRow
        // If remainder == 0, last row is already full
        val fillToRow = if (remainder == 0) 0 else selectorsPerRow - remainder
        // Add another full row of nulls after filling
        val extraNulls = fillToRow + selectorsPerRow

        val allAutofitSpecs = effectiveHullAutofitSpecs + List(extraNulls) { null }

        for (i in allAutofitSpecs.indices) {
            val autofitSpec = allAutofitSpecs[i]

            val selectorPanel = AutofitSelector.createAutofitSelector(autofitSpec, selectorWidth) // Create the panel
            // Add the panel's plugin to the plugin list
            val selectorPlugin = selectorPanel.plugin as AutofitSelector.AutofitSelectorPlugin
            selectorPlugins.add(selectorPlugin)

            // add panel and position into the grid
            scrollerTooltip.addCustomDoNotSetPosition(selectorPanel).position.let { pos ->
                if (prev == null) {// First item: place in top-left
                    pos.inTL(endPad, endPad)
                    firstInRow = selectorPanel
                } else if (i % selectorsPerRow == 0) {// New row: place below the first in the previous row
                    pos.belowLeft(firstInRow, midPad)
                    firstInRow = selectorPanel
                } else pos.rightOfTop(prev, midPad)// Same row: place to the right of previous
                prev = selectorPanel // Update previous to current
            }

            if (autofitSpec != null) {
                highlightBasedOnVariant(autofitSpec.variant, baseVariant, selectorPlugin)
                makeTooltip(selectorPanel, autofitSpec.variant, autofitSpec.missing)

                if (autofitSpec.source != null) // Is this a loadout from this mod?
                    removeSelectorPanelButton(selectorPanel, autofitSpec) // Allow it to be removed
            }
        }

        // sync all the selectors
        for (selectorPlugin in selectorPlugins) {
            selectorPlugin.onHoverEnter {
                Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f)
            }
            selectorPlugin.onClick { event ->
                if (selectorPlugin.autofitSpec == null) return@onClick // If no variant. there's nothing to do

                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)

                if (event.isCtrlDown) {//Copy variant to clipboard
                    ClipboardMisc.saveVariantToClipboard(selectorPlugin.autofitSpec!!.variant, event.isShiftDown)

                } else {//Save and load variant

                    val saveVariant = false

                    if (saveVariant) { // Save variant
                        val shipDirectory = LoadoutManager.getShipDirectoryWithPrefix(ModSettings.defaultPrefix)
                        if (shipDirectory == null) {
                            DisplayMessage.showError("Could not find ship directory with prefix ${ModSettings.defaultPrefix}")
                            return@onClick
                        }

                        val newVariantId = shipDirectory.addShip(baseVariant, settings = ModSettings.getConfiguredVariantSettings())

                    } else { // Load variant

                        applyVariantInRefitScreen(baseVariant, selectorPlugin.autofitSpec!!.variant, fleetMember, ship, coreUI, shipDisplay, refitPanel)

                        selectorPlugins.forEach {
                            if (it.autofitSpec != null) highlightBasedOnVariant(it.autofitSpec!!.variant, baseVariant, it)
                        }
                        selectorPlugin.isSelected = true
                    }
                }
            }
        }


        // add scroll at end after setting heightSoFar, needed when using addCustom to the tooltip
        val rows = (effectiveHullAutofitSpecs.size / selectorsPerRow) + 1
        scrollerTooltip.heightSoFar = endPad * 2 + prev!!.height * rows + midPad * (rows - 1)
        autofitPanel.addUIElement(scrollerTooltip)
        return autofitPanel
    }

    private fun highlightBasedOnVariant(
        variant: ShipVariantAPI,
        baseVariant: ShipVariantAPI,
        selectorPlugin: AutofitSelector.AutofitSelectorPlugin
    ) {
        selectorPlugin.isEqual = false
        selectorPlugin.isBetter = false
        selectorPlugin.isWorse = false

        val equalDefault = compareVariantContents(
            variant,
            baseVariant,
            compareWeaponGroups = false,
            compareBuiltInHullMods = false,
            compareFlux = false,
            compareDMods = false,
            convertSModsToRegular = true,
            compareHiddenHullMods = false,
            useEffectiveHull = true
        )

        if (equalDefault) {
            selectorPlugin.isSelected = true
            selectorPlugin.highlightFader.forceIn()

            outlinePanelBasedOnVariant(baseVariant, variant, selectorPlugin)
        } else {
            selectorPlugin.isSelected = false
        }
    }

    private fun outlinePanelBasedOnVariant(
        baseVariant: ShipVariantAPI,
        variant: ShipVariantAPI,
        selectorPlugin: AutofitSelector.AutofitSelectorPlugin
    ) {
        val compareBaseVariant = baseVariant.clone()
        val compareVariant = variant.clone()

        //Treat built in DMods like regular PermaMods.
        compareBaseVariant.allDMods().forEach { compareBaseVariant.addPermaMod(it) }
        compareVariant.allDMods().forEach { compareVariant.addPermaMod(it) }

        //Treat SModdedBuiltIns like SMods.
        compareBaseVariant.sModdedBuiltIns.forEach { compareBaseVariant.addPermaMod(it, true) }
        compareVariant.sModdedBuiltIns.forEach { compareVariant.addPermaMod(it, true) }

        val equalMods = compareVariantHullMods(
            compareVariant,
            compareBaseVariant,
            compareBuiltInHullMods = false,
            compareHiddenHullMods = false,
        )

        var equalSMods = false
        var unequalSMods = false
        if (compareBaseVariant.sMods.isNotEmpty()) {
            val compareBaseVariantTemp = compareBaseVariant.clone()
            processSModsForComparison(compareBaseVariantTemp, true)
            equalSMods = compareVariantHullMods(
                compareVariant,
                compareBaseVariantTemp,
                compareBuiltInHullMods = false,
                compareHiddenHullMods = false,
            )
        }
        if (compareVariant.sMods.isNotEmpty()) {
            val compareVariantTemp = compareVariant.clone()
            processSModsForComparison(compareVariantTemp, true)
            unequalSMods = compareVariantHullMods(
                compareVariantTemp,
                compareBaseVariant,
                compareBuiltInHullMods = false,
                compareHiddenHullMods = false,
            )
        }

        var unequalDMod = false
        if (compareBaseVariant.allDMods().isNotEmpty()) {
            compareBaseVariant.allDMods().forEach { compareBaseVariant.completelyRemoveMod(it) }
            if (compareBaseVariant.sMods.isNotEmpty()) {
                unequalDMod = compareVariantHullMods(
                    compareVariant,
                    compareBaseVariant,
                    compareBuiltInHullMods = false,
                    compareHiddenHullMods = false,
                )
            } else {
                unequalDMod = compareVariantHullMods(
                    compareVariant,
                    compareBaseVariant,
                    compareBuiltInHullMods = false,
                    compareHiddenHullMods = false,
                    convertSModsToRegular = true
                )
            }
        }

        if (equalMods) {
            selectorPlugin.isEqual = true
        } else if (equalSMods) {
            selectorPlugin.isBetter = true
        } else if (unequalDMod || unequalSMods) {
            selectorPlugin.isWorse = true
        }
    }

    private fun removeSelectorPanelButton(
        selectorPanel: CustomPanelAPI,
        newSpec: AutofitSpec,
    ) {
        val removeVariantButton = selectorPanel.addButton(
            "X",
            null,
            Misc.getButtonTextColor(),
            Misc.getDarkPlayerColor(),
            Alignment.MID,
            CutStyle.ALL,
            25f, 25f,
            Font.ORBITRON_20
        )
        removeVariantButton.xAlignOffset = selectorPanel.right - removeVariantButton.right
        removeVariantButton.yAlignOffset = selectorPanel.top - removeVariantButton.top
        removeVariantButton.onClick {
            deleteLoadoutVariant(newSpec.variant.hullVariantId)
            //selectorPanel.parent?.removeComponent(selectorPanel)
            selectorPanel.opacity = 0f
        }
    }


    fun makeTooltip(
        selectorPanel: CustomPanelAPI,
        variant: ShipVariantAPI,
        missingFromVariant: MissingElements? = null
    ) {
        val allDMods = getAllDMods()
        val sizeOrder = mapOf(
            WeaponAPI.WeaponSize.LARGE to 0,
            WeaponAPI.WeaponSize.MEDIUM to 1,
            WeaponAPI.WeaponSize.SMALL to 2
        )
        val spaces = "                    "

        val width = if (ModSettings.showDebug) {
            400f
        } else {
            350f
        }

        selectorPanel.addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, width) { tooltip ->
            tooltip.addTitle(variant.displayName + " Variant")


            var weaponsWithSize = mutableMapOf<String, Pair<Int, WeaponAPI.WeaponSize>>()
            for (slot in variant.hullSpec.builtInWeapons) {
                val spec = Global.getSettings().getWeaponSpec(slot.value)
                if (spec.type == WeaponAPI.WeaponType.DECORATIVE) continue

                val weaponName = if (ModSettings.showDebug)
                    "${spec.weaponName} (${spec.weaponId} $slot)"
                else
                    spec.weaponName

                weaponsWithSize.merge(weaponName, 1 to spec.size) { old, _ ->
                    old.copy(first = old.first + 1)
                }
            }
            val sortedBuiltInWeapons = weaponsWithSize
                .toList()
                .sortedBy { sizeOrder[it.second.second] }
                .associate { it.first to it.second.first }

            weaponsWithSize = mutableMapOf()
            for (slot in variant.nonBuiltInWeaponSlots) {
                val spec = variant.getWeaponSpec(slot)

                val weaponName = if (ModSettings.showDebug)
                    "${spec.weaponName} (${spec.weaponId} $slot)"
                else
                    spec.weaponName

                weaponsWithSize.merge(weaponName, 1 to spec.size) { old, _ ->
                    old.copy(first = old.first + 1)
                }
            }
            val sortedWeapons = weaponsWithSize
                .toList()
                .sortedBy { sizeOrder[it.second.second] }
                .associate { it.first to it.second.first }

            var _builtinwings: MutableMap<String, Int> = mutableMapOf()

            variant.hullSpec.builtInWings.forEachIndexed { index, wingId ->
                val spec = variant.getWing(index) ?: return@forEachIndexed

                val wingName = if (ModSettings.showDebug)
                    "${spec.wingName} (${spec.id})"
                else
                    spec.wingName

                _builtinwings.merge(wingName, 1, Int::plus)
            }
            var _wings: MutableMap<String, Int> = mutableMapOf()
            for (wing in variant.nonBuiltInWings) {
                val spec = Global.getSettings().getFighterWingSpec(wing) ?: continue

                val wingName = if (ModSettings.showDebug)
                    "${spec.wingName} (${spec.id})"
                else
                    spec.wingName

                _wings.merge(wingName, 1, Int::plus)
            }

            tooltip.addPara("Armaments:", 10f)
            for ((weapon, count) in sortedBuiltInWeapons) {
                tooltip.addPara(
                    "$spaces%s $weapon %s",
                    0f,
                    arrayOf(Misc.getHighlightColor(), Color.GRAY),
                    "${count}x",
                    "(B)"
                )
            }
            for ((weapon, count) in sortedWeapons) {
                tooltip.addPara("$spaces%s $weapon", 0f, Misc.getHighlightColor(), "${count}x")
            }

            if (_wings.isNotEmpty() || _builtinwings.isNotEmpty()) {
                tooltip.addPara("Wings:", 2f)

                for ((wing, count) in _builtinwings) {
                    tooltip.addPara(
                        "$spaces%s $wing %s",
                        0f,
                        arrayOf(Misc.getHighlightColor(), Color.GRAY),
                        "${count}x",
                        "(B)"
                    )
                }
                for ((wing, count) in _wings) {
                    tooltip.addPara("$spaces%s $wing", 0f, Misc.getHighlightColor(), "${count}x")
                }
            }


            val allMods = (variant as HullVariantSpec).allMods as List<HullModSpecAPI>

            val smoddedBuiltIns = mutableListOf<String>()
            val builtIns = mutableListOf<String>()
            val sMods = mutableListOf<String>()
            val permaMods = mutableListOf<String>()
            val regularMods = mutableListOf<String>()
            val dMods = mutableListOf<String>()
            val hiddenMods = mutableListOf<String>()

            for (mod in allMods) {
                val name = if (ModSettings.showDebug) {
                    mod.displayName + " '${mod.id}'"
                } else {
                    mod.displayName
                }

                if ((variant as ShipVariantAPI).hullSpec.builtInMods.contains(mod.id)) {
                    if (allDMods.contains(mod.id) && !variant.permaMods.contains(mod.id)) {//DMod that the hull has, but the variant doesn't, means it's a DMOD built into the hull
                        dMods += "$name (D) (B)"
                        continue
                    }

                    if (variant.sModdedBuiltIns.contains(mod.id) || variant.sMods.contains(mod.id)) {
                        smoddedBuiltIns += "$name (B) (S)"
                    } else if (!variant.suppressedMods.contains(mod.id) && !mod.isHiddenEverywhere) {
                        builtIns += "$name (B)"
                    } else {
                        hiddenMods += "$name (Hidden) (B)"
                    }
                    continue
                }

                if (mod.isHiddenEverywhere) {
                    hiddenMods += "$name (Hidden)"
                    continue
                }
                if (variant.suppressedMods.contains(mod.id)) {
                    hiddenMods += "$name (Suppressed)"
                    continue
                }

                if (allDMods.contains(mod.id)) {
                    dMods += "$name (D)"
                    continue
                }

                if (variant.sMods.contains(mod.id)) {
                    sMods += "$name (S)"
                    continue
                }

                if (variant.permaMods.contains(mod.id)) {
                    if (variant.sModdedBuiltIns.contains(mod.id)) {
                        smoddedBuiltIns += "$name (P) (S)"
                    } else {
                        permaMods += "$name (P)"
                    }
                    continue
                }

                regularMods += name
            }

            // Add header
            tooltip.addPara("Hull Mods:", 2f)

            fun addModLines(list: List<String>) {
                for (line in list) {
                    val highlights = mutableListOf<String>()
                    val highlightColors = mutableListOf<Color>()

                    val tagColorMap = mapOf(
                        "(B)" to Color.GRAY,
                        "(S)" to Misc.getPositiveHighlightColor(),
                        "(D)" to Misc.getNegativeHighlightColor(),
                        "(P)" to Misc.getDarkHighlightColor()
                    )

                    // Find all tags *in order of appearance* in the line
                    val regex = "\\(B\\)|\\(S\\)|\\(D\\)|\\(P\\)".toRegex()
                    val matches = regex.findAll(line)

                    for (match in matches) {
                        val tag = match.value
                        val color = tagColorMap[tag] ?: continue
                        highlights += tag
                        highlightColors += color
                    }

                    // If line has (D), set base color to dark gray, otherwise default text color
                    val baseTextColor = if (line.contains("(D)") || line.contains("(Hidden)")) Misc.getGrayColor() else Misc.getTextColor()

                    tooltip.addPara(
                        "$spaces$line",
                        0f,
                        highlightColors.toTypedArray(),
                        *highlights.toTypedArray()
                    ).color = baseTextColor
                }
            }



            addModLines(smoddedBuiltIns)
            addModLines(sMods)
            addModLines(builtIns)
            addModLines(permaMods)
            addModLines(regularMods)
            addModLines(dMods)

            if (ModSettings.showHiddenModsInTooltip) {
                addModLines(hiddenMods)
            }

            //val capStr = variant.numFluxCapacitors.toString().padStart(3, ' ')
            //val ventStr = variant.numFluxVents.toString().padStart(3, ' ')

            //TODO, figure out how to align these properly. Font does not appear to be monospaced.
            tooltip.addPara(
                "\n%4s  %s",
                0f,
                Misc.getHighlightColor(),
                variant.numFluxCapacitors.toString(),
                "Flux capacitors"
            )
            tooltip.addPara("%4s  %s", 0f, Misc.getHighlightColor(), variant.numFluxVents.toString(), "Flux vents")


            tooltip.addPara("\nHold CTRL and click to copy loadout to clipboard", 2f)

            if (missingFromVariant != null && missingFromVariant.hasMissing()) {

                val enabledMods = Global.getSettings().modManager.enabledModsCopy

                val missingMods = mutableSetOf<Triple<String, String, String>>()
                val haveModsEqVer = mutableSetOf<Triple<String, String, String>>()
                val haveModsDifVer = mutableSetOf<Triple<String, String, String>>()

                for ((modId, modName, modVersion) in missingFromVariant.gameMods) {
                    val matchedMod = enabledMods.find { it.id == modId }

                    when {
                        matchedMod == null -> missingMods.add(Triple(modId, modName, modVersion))
                        matchedMod.version == modVersion -> haveModsEqVer.add(Triple(modId, modName, modVersion))
                        else -> haveModsDifVer.add(Triple(modId, modName, modVersion))
                    }
                }

                if (missingMods.isNotEmpty()) {
                    tooltip.addPara("\nRequired Mods:", 2f)

                    for ((modId, modName, modVersion) in missingMods) {
                        tooltip.addPara("$modName (MISSING)\n       $modId $modVersion", Color.RED, 0f)
                    }
                    for ((modId, modName, modVersion) in haveModsDifVer) {
                        tooltip.addPara(
                            "$modName (VERSION DIFFERENCE)\n        $modId $modVersion",
                            Color.LIGHT_GRAY,
                            0f
                        )
                    }
                    for ((modId, modName, modVersion) in haveModsEqVer) {
                        tooltip.addPara("$modName (EXACT)\n     $modId $modVersion", 0f)
                    }
                }


                tooltip.addPara(
                    "\nFailed to load:\nWeapons: ${missingFromVariant.weaponIds}\nWings: ${missingFromVariant.wingIds}\nHullMods: ${missingFromVariant.hullModIds}",
                    2f
                )
            }

            if (ModSettings.showDebug) {
                tooltip.addPara("\n\nDEBUG: VariantId = ${variant.hullVariantId}", 2f)
                tooltip.addPara("\nDEBUG: Tags = ${variant.tags}", 2f)
                /*tooltip.addPara("\n\nDEBUG: WeaponGroups: ", 2f)
                for (weaponGroup in variant.weaponGroups.withIndex()) {
                    tooltip.addPara(
                        "\nWeaponGroup[${weaponGroup.index}] mode:${weaponGroup.value.type}     autofire:${weaponGroup.value.isAutofireOnByDefault}",
                        0f
                    )
                    for (slotId in weaponGroup.value.slots) {
                        val weaponId = variant.getWeaponId(slotId)
                        if (weaponId != null)
                            tooltip.addPara("Slot[$slotId]  Weapon: $weaponId ", 0f)
                        else
                            tooltip.addPara("Weapon on slot null", 0f)
                    }
                }*/
            }
        }
    }
}