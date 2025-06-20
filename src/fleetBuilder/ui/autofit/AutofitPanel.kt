package fleetBuilder.ui.autofit

import MagicLib.*
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
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
import fleetBuilder.autofit.AutofitApplier.applyVariantInRefitScreen
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.variants.LoadoutManager.deleteLoadoutVariant
import fleetBuilder.variants.LoadoutManager.getAllAutofitSpecsForShip
import fleetBuilder.variants.LoadoutManager.getAnyVariant
import fleetBuilder.variants.LoadoutManager.getLoadoutVariant
import fleetBuilder.variants.LoadoutManager.saveLoadoutVariant
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.VariantLib.compareVariantContents
import fleetBuilder.variants.VariantLib.getAllDMods
import fleetBuilder.variants.VariantLib.makeVariantID
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.alphaf
import org.magiclib.kotlin.bluef
import org.magiclib.kotlin.greenf
import org.magiclib.kotlin.redf
import java.awt.Color
import kotlin.collections.iterator


/**
 * Original author
 * @author Starficz
 */
internal object AutofitPanel {
    private const val BACKGROUND_ALPHA = 0.7f
    internal class MagicPaintjobRefitPanelPlugin(private val refitTab: UIPanelAPI) : BaseCustomUIPanelPlugin() {
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

        private fun drawBorder(x1: Float, y1: Float, x2: Float, y2: Float){
            GL11.glRectf(x1, y1, x2+1, y1-1)
            GL11.glRectf(x2, y1, x2+1, y2+1)
            GL11.glRectf(x1, y2, x1-1, y1-1)
            GL11.glRectf(x2, y2, x1-1, y2+1)
        }

        override fun processInput(events: MutableList<InputEventAPI>?) {
            for (event in events!!) {
                if (!event.isConsumed && event.isKeyboardEvent && event.eventValue == Keyboard.KEY_ESCAPE) {
                    autofitPanel.parent?.removeComponent(autofitPanel)
                    event.consume()
                } else if (!event.isConsumed && (event.isKeyboardEvent || event.isMouseMoveEvent ||
                            event.isMouseDownEvent || event.isMouseScrollEvent)) {
                    event.consume()
                }
            }
        }

        override fun advance(amount: Float) {
        }
    }

    internal fun createMagicPaintjobRefitPanel(refitTab: UIPanelAPI, refitPanel : UIPanelAPI, coreUI : CoreUIAPI,
                                               width: Float, height: Float): CustomPanelAPI {

        val endPad = 6f
        val midPad = 5f
        val selectorsPerRow = ModSettings.selectorsPerRow

        val paintjobPlugin = MagicPaintjobRefitPanelPlugin(refitTab)
        val autofitPanel = Global.getSettings().createCustom(width, height, paintjobPlugin)
        paintjobPlugin.autofitPanel = autofitPanel

        // borders are drawn outside of panel, so +2 needed to lineup scrollbar with border
        val scrollerTooltip = autofitPanel.createUIElement(width+2f, height, true)
        scrollerTooltip.position.inTL(0f, 0f)

        val shipDisplay = ReflectionUtils.invoke("getShipDisplay", refitPanel) as? UIPanelAPI ?: return autofitPanel
        val baseVariant = ReflectionUtils.invoke("getCurrentVariant", shipDisplay) as? HullVariantSpec ?: return autofitPanel
        val fleetMember = ReflectionUtils.invoke("getMember", refitPanel) as? FleetMemberAPI ?: return autofitPanel


        //val currentPaintjob = MagicPaintjobManager.getCurrentShipPaintjob(baseVariant)
        //val baseHullPaintjobs = MagicPaintjobManager.getPaintjobsForHull(
        //   (baseVariant as ShipVariantAPI).hullSpec, false)

        //val currentPaintjob = AutofitSpec(baseVariant.hullVariantId, baseVariant.displayName, "",(baseVariant as ShipVariantAPI).hullSpec.spriteName)

        
        val baseHullPaintjobs = getAllAutofitSpecsForShip((baseVariant as ShipVariantAPI).hullSpec)//mutableListOf<MagicPaintjobSpec>()

        val selectorWidth = (autofitPanel.width-(endPad*2+midPad*(selectorsPerRow-1)))/selectorsPerRow
        var firstInRow: UIPanelAPI? = null
        var prev: UIPanelAPI? = null
        val selectorPlugins = mutableListOf<AutofitSelector.MagicPaintjobSelectorPlugin>()

        var index = 0

        val allPaintjobs = baseHullPaintjobs + listOf(null)
        for (i in allPaintjobs.indices) {
            index = i
            val paintjobSpec = allPaintjobs[i]

            var variant: HullVariantSpec
            if(paintjobSpec != null) {
                variant = (getAnyVariant(paintjobSpec.variantId) as? HullVariantSpec
                    ?: Global.getSettings().createEmptyVariant(baseVariant.hullSpec.hullId, baseVariant.hullSpec)) as HullVariantSpec
            } else {
                variant = baseVariant
            }

            variant
            val selectorPanel = AutofitSelector.createAutofitSelector(variant, paintjobSpec, selectorWidth)

            val selectorPlugin = selectorPanel.plugin as AutofitSelector.MagicPaintjobSelectorPlugin
            selectorPlugins.add(selectorPlugin)

            if(paintjobSpec != null && paintjobSpec.missingFromVariant.hasMissing()) {
                selectorPlugin.hasMissing = true
            }


            if ( paintjobSpec != null && compareVariantContents(variant, baseVariant, compareFlux = false, useEffectiveHull = true)) {
                selectorPlugin.isSelected = true
                selectorPlugin.highlightFader.forceIn()
            }

            // add panel and position them into the grid
            scrollerTooltip.addCustomDoNotSetPosition(selectorPanel).position.let { pos ->
                if (prev == null) {// First item: place in top-left
                    pos.inTL(endPad, endPad)
                    firstInRow = selectorPanel
                } else if (index % selectorsPerRow == 0) {// New row: place below the first in the previous row
                    pos.belowLeft(firstInRow, midPad)
                    firstInRow = selectorPanel
                } else pos.rightOfTop(prev, midPad)// Same row: place to the right of previous
                prev = selectorPanel // Update previous to current
            }

            if(paintjobSpec != null) {
                makeTooltip(scrollerTooltip, selectorPanel, variant, paintjobSpec.missingFromVariant)
            } else {
                makeTooltip(scrollerTooltip, selectorPanel, variant)
            }

            if(paintjobSpec != null
                && getLoadoutVariant(paintjobSpec.variantId) != null) {//A loadout from this mod?
                removeSelectorPanelButton(selectorPanel, variant, paintjobSpec)
            }

        }

        // sync all the selectors
        for (selectorPlugin in selectorPlugins) {
            selectorPlugin.onHoverEnter { event ->
                    Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f)
            }
            selectorPlugin.onClick { event ->
                if (selectorPlugin.isUnlocked || Global.getSettings().isDevMode) {
                    Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
                    if (event.isCtrlDown) {//Copy variant to clipboard
                        Global.getLogger(this.javaClass).info("Variant to clipboard")

                        var saveVariant: ShipVariantAPI?
                        if (selectorPlugin.paintjobSpec == null) {
                            saveVariant = baseVariant
                        }
                        else {
                            saveVariant = getAnyVariant(selectorPlugin.paintjobSpec!!.variantId)
                        }

                        if(saveVariant != null) {
                            val variantToSave = saveVariant.clone()
                            variantToSave.hullVariantId = makeVariantID(saveVariant)
                            val json = saveVariantToJson(variantToSave)

                            setClipboardText(json.toString(4))
                        }
                    }
                    else {//Save and load variant
                        selectorPlugins.forEach { it.isSelected = false }
                        selectorPlugin.isSelected = true

                        if (selectorPlugin.paintjobSpec == null) {

                            val newVariantId = saveLoadoutVariant(baseVariant, includeDMods = ModSettings.saveDMods, applySMods = ModSettings.saveSMods)

                            Global.getLogger(this.javaClass).info("Save ship variant with id $newVariantId")
                            if (newVariantId.isNotEmpty()) {

                                index++
                                //selectorPlugins.remove(selectorPlugin)
                                //selectorPlugin.selectorPanel.opacity = 0f

                                val newSpec = AutofitSpec(
                                    newVariantId,
                                    baseVariant.displayName,
                                    "Loadout Variant",
                                    (baseVariant as ShipVariantAPI).hullSpec.spriteName
                                )
                                selectorPlugin.paintjobSpec = newSpec
                                selectorPlugin.isSelected = true
                                selectorPlugin.highlightFader.forceIn()

                                val oldTextElement =
                                    selectorPlugin.selectorPanel.getChildrenCopy().filterIsInstance<TooltipMakerAPI>()
                                        .first()
                                selectorPlugin.selectorPanel.removeComponent(oldTextElement as UIComponentAPI)//TODO, figure out a way to edit the text in the already existing Tooltip text, rather than making a new one.

                                val textElement = selectorPlugin.selectorPanel.createUIElement(
                                    selectorPlugin.selectorPanel.width,
                                    45f - 5f,
                                    false
                                )
                                selectorPlugin.selectorPanel.addUIElement(textElement)
                                with(textElement) {
                                    position.inTL(0f, selectorPlugin.selectorPanel.width + 5f)
                                    setTitleOrbitronLarge()
                                    addTitle(newSpec.name)
                                    addPara(newSpec.description, 3f)
                                }

                                makeTooltip(scrollerTooltip, selectorPlugin.selectorPanel, baseVariant)
                                removeSelectorPanelButton(selectorPlugin.selectorPanel, baseVariant, newSpec)

                                /*
                                val selectorPanel = createAutofitSelector(baseVariant, newSpec, selectorWidth)
                                val newSelectorPlugin = selectorPanel.plugin as MagicPaintjobSelectorPlugin
                                selectorPlugins.add(newSelectorPlugin)
                                scrollerTooltip.addCustomDoNotSetPosition(selectorPanel).position.let { pos ->
                                    if (prev == null) {// First item: place in top-left
                                        pos.inTL(endPad, endPad)
                                        firstInRow = selectorPanel
                                    } else if (index % selectorsPerRow == 0) {// New row: place below the first in the previous row
                                        pos.belowLeft(firstInRow, midPad)
                                        firstInRow = selectorPanel
                                    } else pos.rightOfTop(prev, midPad)// Same row: place to the right of previous
                                    prev = selectorPanel // Update previous to current
                                }*/
                            }
                        } else {
                            Global.getLogger(this.javaClass).info("Load ship variant")
                            var variant: HullVariantSpec? = getAnyVariant(selectorPlugin.paintjobSpec!!.variantId) as? HullVariantSpec
                            if(variant == null) {
                                variant = Global.getSettings().createEmptyVariant((baseVariant as ShipVariantAPI).hullSpec.hullId, (baseVariant as ShipVariantAPI).hullSpec) as HullVariantSpec
                                Global.getLogger(this.javaClass).error("Failed to get ship variant. Creating empty variant in it's place.")
                            }

                            //if (variant != null) {
                                val ship = ReflectionUtils.invoke("getShip", shipDisplay) as? ShipAPI

                                if(ship != null) {
                                    applyVariantInRefitScreen(baseVariant, variant, fleetMember, ship, coreUI, shipDisplay)

                                    ReflectionUtils.invoke("syncWithCurrentVariant", refitPanel)
                                    ReflectionUtils.invoke("updateModules", shipDisplay)
                                    ReflectionUtils.invoke("updateButtonPositionsToZoomLevel", shipDisplay)

                                    /*
                                    if(selectorPlugins.last().selectorPanel === prev) {
                                        selectorPlugins.last().selectorPanel.clearChildren()

                                        val newSpec = AutofitSpec(
                                            baseVariant.hullVariantId,
                                            "Current Variant",
                                            "Click to save",
                                            (baseVariant as ShipVariantAPI).hullSpec.spriteName
                                        )

                                        createAutofitSelectorChildren(baseVariant, newSpec, selectorWidth, selectorPlugins.last().selectorPanel)
                                    }*/
                                }
                            //}
                        }
                    }
                }
            }
        }



        // add scroll at end after setting heightSoFar, needed when using addCustom to the tooltip
        val rows = (baseHullPaintjobs.size/selectorsPerRow) + 1
        scrollerTooltip.heightSoFar = endPad*2 + prev!!.height*rows + midPad*(rows-1)
        autofitPanel.addUIElement(scrollerTooltip)
        return autofitPanel
    }

    private fun removeSelectorPanelButton(
        selectorPanel: CustomPanelAPI,
        baseVariant: HullVariantSpec,
        newSpec: AutofitSpec,
    ) {
        val removeVariantButton = selectorPanel.addButton(
            "X",
            null,
            Misc.getButtonTextColor(),
            Misc.getDarkPlayerColor(),
            Alignment.MID,
            CutStyle.ALL,
            Font.ORBITRON_20,
            25f, 25f
        )
        removeVariantButton.xAlignOffset = selectorPanel.right - removeVariantButton.right
        removeVariantButton.yAlignOffset = selectorPanel.top - removeVariantButton.top
        removeVariantButton.onClick {
            deleteLoadoutVariant(newSpec.variantId)
            //selectorPanel.parent?.removeComponent(selectorPanel)//Causes an error if removing a panel with one in front of it when closing the autofitPanel
            selectorPanel.opacity = 0f//Somehow removes the panel? Including preventing input to it???
        }
    }


    private fun makeTooltip(
        scrollerTooltip: TooltipMakerAPI,
        selectorPanel: CustomPanelAPI,
        variant: HullVariantSpec,
        missingFromVariant: MissingElements = MissingElements()
    ) {
        val allDMods = getAllDMods()
        val sizeOrder = mapOf(
            WeaponAPI.WeaponSize.LARGE to 0,
            WeaponAPI.WeaponSize.MEDIUM to 1,
            WeaponAPI.WeaponSize.SMALL to 2
        )
        val spaces = "                    "

        scrollerTooltip.addTooltip(selectorPanel, TooltipMakerAPI.TooltipLocation.BELOW, 350f) { tooltip ->
            tooltip.addTitle(variant.displayName + " Variant")


            var weaponsWithSize = mutableMapOf<String, Pair<Int, WeaponAPI.WeaponSize>>()
            for (slot in (variant as ShipVariantAPI).hullSpec.builtInWeapons) {
                val spec = Global.getSettings().getWeaponSpec(slot.value)

                if(spec.type == WeaponAPI.WeaponType.DECORATIVE) continue

                weaponsWithSize.merge(spec.weaponName, 1 to spec.size) { old, _ ->
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
                weaponsWithSize.merge(spec.weaponName, 1 to spec.size) { old, _ ->
                    old.copy(first = old.first + 1)
                }
            }
            val sortedWeapons = weaponsWithSize
                .toList()
                .sortedBy { sizeOrder[it.second.second] }
                .associate { it.first to it.second.first }

            var _builtinwings: MutableMap<String, Int> = mutableMapOf()

            (variant as ShipVariantAPI).hullSpec.builtInWings.forEachIndexed { index, wingId ->
                val wing = (variant as ShipVariantAPI).getWing(index)
                if(wing != null) {
                    _builtinwings.merge(wing.wingName, 1, Int::plus)
                }
            }
            var _wings: MutableMap<String, Int> = mutableMapOf()
            for (wing in variant.nonBuiltInWings) {
                _wings.merge(Global.getSettings().getFighterWingSpec(wing).wingName, 1, Int::plus)
            }

            tooltip.addPara("Armaments:", 10f)
            for ((weapon, count) in sortedBuiltInWeapons) {
                tooltip.addPara("$spaces%s $weapon %s", 0f, arrayOf(Misc.getHighlightColor(), Color.GRAY),"${count}x", "(B)")
            }
            for ((weapon, count) in sortedWeapons) {
                tooltip.addPara("$spaces%s $weapon", 0f, Misc.getHighlightColor(), "${count}x")
            }

            if (_wings.isNotEmpty() || _builtinwings.isNotEmpty()) {
                tooltip.addPara("Wings:", 2f)

                for ((wing, count) in _builtinwings) {
                    tooltip.addPara("$spaces%s $wing %s", 0f, arrayOf(Misc.getHighlightColor(), Color.GRAY), "${count}x", "(B)")
                }
                for ((wing, count) in _wings) {
                    tooltip.addPara("$spaces%s $wing", 0f, Misc.getHighlightColor(), "${count}x")
                }
            }


            val allMods = variant.allMods as List<HullModSpecAPI>

            val smoddedBuiltIns = mutableListOf<String>()
            val builtIns = mutableListOf<String>()
            val sMods = mutableListOf<String>()
            val permaMods = mutableListOf<String>()
            val regularMods = mutableListOf<String>()
            val dMods = mutableListOf<String>()
            val hiddenMods = mutableListOf<String>()

            for (mod in allMods) {
                val name = mod.displayName

                if ((variant as ShipVariantAPI).hullSpec.builtInMods.contains(mod.id)) {
                    if (allDMods.contains(mod.id) && !variant.permaMods.contains(mod.id)) {//DMod that the hull has, but the variant doesn't, means it's a DMOD built into the hull
                        dMods += "$name (D) (B)"
                        continue
                    }

                    if (variant.sModdedBuiltIns.contains(mod.id) || variant.sMods.contains(mod.id)) {
                        smoddedBuiltIns += "$name (B) (S)"
                    } else if (!variant.suppressedMods.contains(mod.id) && !mod.isHiddenEverywhere){
                        builtIns += "$name (B)"
                    } else {
                        hiddenMods += "$name (Hidden) (B)"
                    }
                    continue
                }

                if (mod.isHiddenEverywhere || variant.suppressedMods.contains(mod.id)) {
                    hiddenMods += "$name (Hidden)"
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
            tooltip.addPara("\n%4s  %s", 0f, Misc.getHighlightColor(), variant.numFluxCapacitors.toString(), "Flux capacitors")
            tooltip.addPara("%4s  %s", 0f, Misc.getHighlightColor(), variant.numFluxVents.toString(), "Flux vents")


            tooltip.addPara("\nHold CTRL and click to copy loadout to clipboard", 2f)

            if (missingFromVariant.hasMissing()) {

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
                        tooltip.addPara("$modName (VERSION DIFFERENCE)\n        $modId $modVersion",  Color.LIGHT_GRAY, 0f)
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

            if(ModSettings.showDebug) {
                tooltip.addPara("\n\nDEBUG: VariantId = ${variant.hullVariantId}", 2f)
                tooltip.addPara("\nDEBUG: Tags = ${variant.tags}", 2f)
                tooltip.addPara("\n\nDEBUG: WeaponGroups: ", 2f)
                for (weaponGroup in variant.weaponGroups.withIndex()) {
                    tooltip.addPara("\nWeaponGroup[${weaponGroup.index}] mode:${weaponGroup.value.type}     autofire:${weaponGroup.value.isAutofireOnByDefault}", 0f)
                    for (slotId in weaponGroup.value.slots) {
                        val weaponId = variant.getWeaponId(slotId)
                        if(weaponId != null)
                            tooltip.addPara("Slot[$slotId]  Weapon: $weaponId ", 0f)
                        else
                            tooltip.addPara("Weapon on slot null", 0f)
                    }

                }
            }
        }
    }
}