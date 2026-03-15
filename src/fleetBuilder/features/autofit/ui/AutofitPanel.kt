package fleetBuilder.features.autofit.ui

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.ui.UITable
import com.fs.starfarer.coreui.refit.ModWidget
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.core.ModSettings
import fleetBuilder.core.ModSettings.getDefaultExcludeVariantTags
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.core.shipDirectory.ShipDirectory
import fleetBuilder.core.shipDirectory.ShipDirectoryService
import fleetBuilder.core.shipDirectory.ShipDirectoryService.deleteLoadoutVariant
import fleetBuilder.core.shipDirectory.ShipDirectoryService.getCoreAutofitSpecsForShip
import fleetBuilder.core.shipDirectory.ShipDirectoryService.getLoadoutAutofitSpecsForShip
import fleetBuilder.features.autofit.lib.AutofitApplier.applyVariantInRefitScreen
import fleetBuilder.serialization.ClipboardMisc
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.variant.DataVariant.copyVariant
import fleetBuilder.serialization.variant.VariantSettings
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.customPanel.common.DialogPanel
import fleetBuilder.util.*
import fleetBuilder.util.FBMisc.sModHandlerTemp
import fleetBuilder.util.LookupUtil.getAllDMods
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.api.VariantUtils.compareVariantContents
import fleetBuilder.util.api.VariantUtils.compareVariantHullMods
import fleetBuilder.util.api.VariantUtils.processSModsForComparison
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.alphaf
import org.magiclib.kotlin.bluef
import org.magiclib.kotlin.greenf
import org.magiclib.kotlin.redf
import starficz.*
import java.awt.Color


/**
 * Original author
 * @author Starficz
 * Heavily modified otherwise
 */
internal object AutofitPanel {
    private const val BACKGROUND_ALPHA = 0.7f
    var currentPrefix = ModSettings.defaultPrefix

    internal class AutofitPanelPlugin(private val parentPanel: UIPanelAPI) : BaseCustomUIPanelPlugin() {
        lateinit var autofitPanel: CustomPanelAPI
        var baseVariantPanel: CustomPanelAPI? = null

        override fun renderBelow(alphaMult: Float) {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            // Background dark fadeout
            if (baseVariantPanel != null) {
                val bgColor = Color.BLACK
                val bgAlpha = BACKGROUND_ALPHA * bgColor.alphaf * alphaMult
                GL11.glColor4f(bgColor.redf, bgColor.greenf, bgColor.bluef, bgAlpha)
                val screenW = Global.getSettings().screenWidth
                val screenH = Global.getSettings().screenHeight
                val buffer = -5f
                val tx = parentPanel.x - buffer
                val ty = parentPanel.y - buffer
                val tw = parentPanel.width + buffer * 2
                val th = parentPanel.height + buffer * 2
                // Left
                GL11.glRectf(0f, 0f, tx, screenH)
                // Right
                GL11.glRectf(tx + tw, 0f, screenW, screenH)
                // Top
                GL11.glRectf(tx, ty + th, tx + tw, screenH)
                // Bottom
                GL11.glRectf(tx, 0f, tx + tw, ty)
            }

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
            drawBorder(parentPanel.left, parentPanel.bottom, parentPanel.right, parentPanel.top)

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

        var draggedAutofitSpec: AutofitSpec? = null
        override fun processInput(events: MutableList<InputEventAPI>) {
            for (event in events) {
                if (event.isConsumed) continue

                if (event.isMouseUpEvent) {
                    draggedAutofitSpec = null
                }

                fun deleteSelf() {
                    autofitPanel.parent?.removeComponent(autofitPanel)
                    draggedPanel?.parent?.removeComponent(draggedPanel!!)
                }

                if (event.isKeyDownEvent
                    && (event.eventValue == Keyboard.KEY_ESCAPE || event.eventValue == ModSettings.autofitMenuHotkey)
                ) {
                    deleteSelf()
                    event.consume()
                } else if ((
                            UIUtils.isMouseHoveringOverComponent(autofitPanel, 8f) // Inside the autofitPanel? (grid that shows ships)
                                    || (baseVariantPanel != null && UIUtils.isMouseHoveringOverComponent(baseVariantPanel!!, 8f)) // Inside the baseVariantPanel? (current variant)
                                    || !UIUtils.isMouseWithinBounds(parentPanel.x, parentPanel.y, parentPanel.width, parentPanel.height) // Outside parent?
                            ) && (
                            event.isKeyboardEvent
                                    || event.isMouseEvent
                                    || event.isMouseMoveEvent
                            )
                ) {
                    if (!UIUtils.isMouseWithinBounds(parentPanel.x, parentPanel.y, parentPanel.width, parentPanel.height) && event.isRMBDownEvent)
                        deleteSelf()
                    //Only the upper right box is free from events being consumed.
                    event.consume()
                }

            }
        }


        var draggedPanel: UIPanelAPI? = null
        var selectorWidth: Float = 0f
        override fun advance(amount: Float) {
            if (draggedAutofitSpec == null) {
                if (draggedPanel != null) {
                    draggedPanel!!.parent?.removeComponent(draggedPanel!!)
                    draggedPanel = null
                }
                return
            }

            if (draggedPanel == null)
                draggedPanel = AutofitSelector.createShipPreview(draggedAutofitSpec!!.variant, selectorWidth, selectorWidth)

            val screenPanel = ReflectionMisc.getScreenPanel() ?: return
            if (screenPanel.getChildrenCopy().find { it === draggedPanel } == null)
                screenPanel.addComponent(draggedPanel)

            draggedPanel!!.position.setXAlignOffset(Global.getSettings().mouseX.toFloat() - draggedPanel!!.width / 2f)
            draggedPanel!!.position.setYAlignOffset(Global.getSettings().mouseY.toFloat() - draggedPanel!!.height / 2f)

        }
    }

    internal fun createMagicAutofitPanel(
        parentPanel: UIPanelAPI,
        width: Float, height: Float, variant: ShipVariantAPI, makeAndRemoveLoadouts: Boolean
    ): CustomPanelAPI {
        return createMagicAutofitPanelFull(parentPanel, null, null, width, height, variant, makeAndRemoveLoadouts)
    }

    internal fun createMagicAutofitPanel(
        refitTab: UIPanelAPI, refitPanel: UIPanelAPI, shipDisplay: UIPanelAPI,
        width: Float, height: Float
    ): CustomPanelAPI {
        return createMagicAutofitPanelFull(refitTab, refitPanel, shipDisplay, width, height, null)
    }

    private fun createMagicAutofitPanelFull(
        parentPanel: UIPanelAPI, refitPanel: UIPanelAPI?, shipDisplay: UIPanelAPI?,
        width: Float, height: Float,
        inputVariant: ShipVariantAPI? = null, makeAndRemoveLoadouts: Boolean = true
    ): CustomPanelAPI {
        val autofitPlugin = AutofitPanelPlugin(parentPanel)
        val autofitPanel = Global.getSettings().createCustom(width, height, autofitPlugin)// Background Panel
        autofitPlugin.autofitPanel = autofitPanel

        // borders are drawn outside of panel, so +2 needed to lineup scrollbar with border
        val scrollerTooltip = autofitPanel.createUIElement(width + 2f, height, true) // Tooltip on background panel
        scrollerTooltip.position.inTL(0f, 0f)

        val currentShipDirectory = ShipDirectoryService.getShipDirectoryWithPrefix(currentPrefix) ?: run {
            DisplayMessage.showError("Failed to get ship directory object")
            return autofitPanel
        }

        val baseVariant = shipDisplay?.safeInvoke("getCurrentVariant") as? HullVariantSpec
            ?: inputVariant ?: return autofitPanel
        var fleetMember: FleetMemberAPI? = null
        var ship: ShipAPI? = null
        var modWidget: ModWidget? = null

        if (refitPanel != null && shipDisplay != null) {
            fleetMember = refitPanel.safeInvoke("getMember") as? FleetMemberAPI ?: return autofitPanel
            ship = shipDisplay.safeInvoke("getShip") as? ShipAPI ?: return autofitPanel
            modWidget = ReflectionMisc.getRefitPanelModWidget(refitPanel) ?: return autofitPanel
        }

        val modWidgetHeight = modWidget?.height ?: 0f

        var firstInRow: UIPanelAPI? = null
        var prev: UIPanelAPI? = null

        val endPad = 6f
        val midPad = 5f
        val selectorsPerRow = ModSettings.selectorsPerRow
        val selectorWidth = (autofitPanel.width - (endPad * 2 + midPad * (selectorsPerRow - 1))) / selectorsPerRow
        autofitPlugin.selectorWidth = selectorWidth

        val selectorPlugins = mutableListOf<AutofitSelector.AutofitSelectorPlugin>()

        var coreEffectiveHullAutofitSpecs: MutableList<AutofitSpec?> = mutableListOf()
        if (currentPrefix == ModSettings.defaultPrefix) { // Only add core goal autofits if default prefix
            // Get core specs and convert to mutable list
            coreEffectiveHullAutofitSpecs =
                getCoreAutofitSpecsForShip((baseVariant as ShipVariantAPI).hullSpec).toMutableList()

            // Determine minimum reserved slots for core specs
            val minCoreSlots = if (ModSettings.reserveFirstFourAutofitSlots) 4 else 0

            // Pad with nulls if fewer than minCoreSlots
            while (coreEffectiveHullAutofitSpecs.size < minCoreSlots) {
                coreEffectiveHullAutofitSpecs.add(null)
            }
        }

        // Get loadout specs
        val loadoutEffectiveHullAutofitSpecs =
            getLoadoutAutofitSpecsForShip(
                currentPrefix,
                (baseVariant as ShipVariantAPI).hullSpec,
                coreEffectiveHullAutofitSpecs.size
            )

        // Combine core (with nulls) + loadout specs
        val combinedSpecs: List<AutofitSpec?> = coreEffectiveHullAutofitSpecs + loadoutEffectiveHullAutofitSpecs

        // Sort non-null specs by desired index, nulls will naturally stay after
        val sortedSpecs = combinedSpecs.filterNotNull().sortedBy { it.desiredIndexInMenu }

        // Find max desired index for indexed list
        val maxDesiredIndex = sortedSpecs.maxOfOrNull { it.desiredIndexInMenu } ?: 0

        // Start with a list of nulls big enough to fit everything
        val indexedSpecs = MutableList<AutofitSpec?>(maxDesiredIndex + sortedSpecs.size) { null }

        // For each unique desired index in ascending order
        sortedSpecs.groupBy { it.desiredIndexInMenu }
            .toSortedMap()
            .forEach { (desiredIndex, specsForIndex) ->
                for (spec in specsForIndex) {
                    // Try to place at desiredIndex or as close ahead as possible
                    var placeAt = desiredIndex
                    while (placeAt < indexedSpecs.size && indexedSpecs[placeAt] != null) {
                        placeAt++
                    }
                    // If still full, expand list
                    if (placeAt >= indexedSpecs.size) {
                        indexedSpecs.addAll(List(specsForIndex.size) { null })
                    }
                    indexedSpecs[placeAt] = spec
                }
            }


        // --- Trim to only keep current row + next row nulls ---
        val lastNonNullIndex = indexedSpecs.indexOfLast { it != null }
        val remainder = (lastNonNullIndex + 1) % selectorsPerRow
        val fillToRow = if (remainder == 0) 0 else selectorsPerRow - remainder
        var extraNulls = fillToRow + selectorsPerRow
        if (indexedSpecs.isEmpty() && ModSettings.reserveFirstFourAutofitSlots)
            extraNulls += selectorsPerRow

        // Build final list with padding
        val allAutofitSpecs: List<AutofitSpec?> = indexedSpecs.subList(0, lastNonNullIndex + 1) + List(extraNulls) { null }




        for (i in allAutofitSpecs.indices) {
            val autofitSpec = allAutofitSpecs[i]

            val selectorPanel = AutofitSelector.createAutofitSelector(autofitSpec, selectorWidth, addXIfAutofitSpecNull = ModSettings.reserveFirstFourAutofitSlots && i < 4) // Create the panel
            // Add the panel's plugin to the ModSettings.plugin && list
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
                makeTooltip(selectorPanel, autofitSpec.variant, autofitSpec.missing, if (i % selectorsPerRow >= selectorsPerRow / 2) TooltipMakerAPI.TooltipLocation.LEFT else TooltipMakerAPI.TooltipLocation.RIGHT)

                if (autofitSpec.source != null && makeAndRemoveLoadouts) // Is this a loadout from this mod?
                    removeSelectorPanelButton(selectorPanel, autofitSpec) // Allow it to be removed
            } else {
                if (selectorPlugin.addXIfAutofitSpecNull) {
                    selectorPanel.addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 780f) { tooltip ->
                        tooltip.addPara(
                            "This slot is reserved for goal variants." +
                                    "\nIf a game or mod update adds or removes goal variants, the slot positioning would be pushed around and thus messed up. This prevents that." +
                                    "\n\nThis reservation can be disabled in the LunaLib settings.", 0f
                        )
                    }
                }
            }
        }

        //Make base variant selector panel.

        var baseVariantSelectorPlugin: AutofitSelector.AutofitSelectorPlugin? = null
        var cargoButton: ButtonAPI? = null
        var storageButton: ButtonAPI? = null
        var marketButton: ButtonAPI? = null
        var blackMarketButton: ButtonAPI? = null
        var applySModsButton: ButtonAPI? = null

        val containerPanelWidth = parentPanel.width - autofitPanel.width - 7f
        val containerPanelHeight = parentPanel.height - modWidgetHeight + 44f
        if (fleetMember != null) {
            val descriptorHeight = 34f
            val topPad = 5f
            // Create container panel with modWidget size
            val baseVariantPanel = autofitPanel.CustomPanel(containerPanelWidth, containerPanelHeight)
            baseVariantPanel.Plugin {
                renderBelow { alphaMult ->
                    // vanilla panels are transparent, but paintjobs need a clear background for display purposes
                    val panelColor = Color.BLACK
                    val panelAlpha = panelColor.alphaf * alphaMult
                    GL11.glColor4f(panelColor.redf, panelColor.greenf, panelColor.bluef, panelAlpha)
                    GL11.glRectf(left, bottom, right, top)

                    GL11.glDisable(GL11.GL_BLEND)

                    // the panel border itself is darker than standard player dark color
                    val borderColor = Misc.getDarkPlayerColor()
                    val darkerBorderColor = borderColor.darker()
                    val darkerBorderAlpha = darkerBorderColor.alphaf * alphaMult
                    GL11.glColor4f(darkerBorderColor.redf, darkerBorderColor.greenf, darkerBorderColor.bluef, darkerBorderAlpha)
                    fun drawBorder(x1: Float, y1: Float, x2: Float, y2: Float) {
                        GL11.glRectf(x1, y1, x2 + 1, y1 - 1)
                        GL11.glRectf(x2, y1, x2 + 1, y2 + 1)
                        GL11.glRectf(x1, y2, x1 - 1, y1 - 1)
                        GL11.glRectf(x2, y2, x1 - 1, y2 + 1)
                    }
                    drawBorder(left, bottom, right, top)

                    GL11.glRectf(left, top - descriptorHeight, right, top)
                }
            }
            autofitPlugin.baseVariantPanel = baseVariantPanel

            // Position container panel
            baseVariantPanel.position.inTL(autofitPanel.width + 1f, -autofitPanel.y + (modWidgetHeight - autofitPanel.y - 50f))

            val containerTextElement = baseVariantPanel.createUIElement(baseVariantPanel.width, descriptorHeight - topPad, false)
            baseVariantPanel.addUIElement(containerTextElement)
            with(containerTextElement) {
                position.inTL(0f, topPad)
                setTitleOrbitronVeryLarge()
                val label = addTitle("Current Variant")
                label.position.inTL((baseVariantPanel.width - label.computeTextWidth(label.text)) / 2f, 0f)
            }

            //TODO, re-add this later with fixes
            /*
                val nonEmptyShipDirectories = LoadoutManager.getShipDirectoriesNotEmpty()
                if (nonEmptyShipDirectories.size > 1) { // If more than one ship directories with ships is present

                    val prefixChangerContainerPanel = baseVariantPanel.createUIElement(baseVariantPanel.width, descriptorHeight - topPad, false)
                    baseVariantPanel.addUIElement(prefixChangerContainerPanel)
                    with(prefixChangerContainerPanel) {
                        position.inTL(0f, topPad - position.height)
                        setButtonFontOrbitron20()
                        //TODO, this should be a drop down. A downwards ^ should be on the right to indicate that.
                        val currentPrefixButton = addButton(
                            currentShipDirectory.name, null, Misc.getButtonTextColor(), Misc.getDarkPlayerColor(), Alignment.LMID, CutStyle.NONE,
                            position.width - position.height * 4f, position.height, 0f
                        )
                        currentPrefixButton.position.inTL(0f, -topPad - 1f)
                        currentPrefixButton.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, 400f) { tooltip ->
                            tooltip.addPara("Click to change or create a new ship directory.\n\nShip directories can be considered to be like different folders that hold all your autofit variants.", 0f)
                        }
                        currentPrefixButton.onClick {

                        }
                        /*
                            setButtonFontOrbitron20()
                            val currentPrefixButton = addButton(currentPrefixObj.name, null, Misc.getButtonTextColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.NONE, position.width - position.height * 5, position.height, 0f)
                            currentPrefixButton.position.inTL(addPrefixButton.width, -topPad - 1f)
                            currentPrefixButton.addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 400f) { tooltip ->
                                tooltip.addPara("Shows current ship directory.\nClick to change.\nThink of different ship directories as different folders for all your ship variants.", 0f)
                            }
                            currentPrefixButton.onClick {

                            }*/
                    }
                }
        */

            // Create base selector panel
            val baseVariantSelectorPanel = AutofitSelector.createAutofitSelector(
                AutofitSpec(baseVariant, null),
                containerPanelWidth - 2f, addDescription = false, centerTitle = true
            )
            makeTooltip(baseVariantSelectorPanel, baseVariant, location = TooltipMakerAPI.TooltipLocation.LEFT)


            baseVariantSelectorPlugin = baseVariantSelectorPanel.plugin as AutofitSelector.AutofitSelectorPlugin
            baseVariantSelectorPlugin.isBase = true
            baseVariantSelectorPlugin.noClickFader = true
            baseVariantSelectorPlugin.comparisonStatus = AutofitSelector.AutofitSelectorPlugin.ComparisonStatus.EQUAL
            baseVariantSelectorPlugin.isSelected = true
            selectorPlugins.add(baseVariantSelectorPlugin)

            // Center selector inside container
            baseVariantSelectorPanel.position.inTL(
                1f, 1f + descriptorHeight
            )
            baseVariantPanel.addComponent(baseVariantSelectorPanel)


            // Create area for toggle buttons under the selector
            val toggleButtonsElement = baseVariantPanel.createUIElement(
                baseVariantPanel.width - 4f, // padding so it doesn't touch edges
                baseVariantPanel.height - baseVariantSelectorPanel.height - descriptorHeight - topPad * 4 - 4f,
                true
            )

            baseVariantPanel.addPara("Click and drag to an empty slot to save", yPad = baseVariantSelectorPanel.height + descriptorHeight + 2f)

            // Position just below the selector
            toggleButtonsElement.position.inTL(2f, descriptorHeight + baseVariantSelectorPanel.height + 4f + 20f)

            //toggleButtonsElement.addPara("Click and drag to an empty slot to save", 0f)
            //toggleButtonsElement.addSpacer(12f)

            val checkboxHeight = 24f
            val checkboxPad = 4f

            val fleetMemory = fleetMember?.fleetData?.fleet?.memoryWithoutUpdate

            fun addToggleButton(
                label: String,
                memoryKey: String,
                tooltipText: String,
                default: Boolean = true
            ): ButtonAPI {
                val button = toggleButtonsElement.addCheckbox(
                    toggleButtonsElement.computeStringWidth(label) + 28f,
                    checkboxHeight,
                    label,
                    null,
                    ButtonAPI.UICheckboxSize.SMALL,
                    checkboxPad
                )

                button.isChecked = if (fleetMemory?.contains(memoryKey) == true) {
                    fleetMemory.getBoolean(memoryKey)
                } else {
                    default
                }

                button.addTooltip(TooltipMakerAPI.TooltipLocation.LEFT, 400f) { tooltip ->
                    tooltip.addPara(tooltipText, 0f)
                }

                button.onClick {
                    fleetMemory?.set(memoryKey, button.isChecked)
                }

                return button
            }

            if (Global.getCurrentState() != GameState.TITLE) {
                cargoButton = addToggleButton(
                    label = "Use ordnance from cargo",
                    memoryKey = "\$FBA_useCargo",
                    tooltipText = "Use weapons and fighter LPCs from your fleet's cargo holds."
                )

                storageButton = addToggleButton(
                    label = "Use ordnance from storage",
                    memoryKey = "\$FBA_useStorage",
                    tooltipText = "Use weapons and fighter LPCs from your local storage facilities."
                )

                marketButton = addToggleButton(
                    label = "Buy ordnance from market",
                    memoryKey = "\$FBA_useMarket",
                    tooltipText = "Buy weapons and fighter LPCs from market, if docked at one.\n\nOrdnance from your cargo will be preferred"
                )

                blackMarketButton = addToggleButton(
                    label = "Allow black market purchases",
                    memoryKey = "\$FBA_useBlackMarket",
                    tooltipText = "Buy weapons and fighter LPCs from the black market.\n\nNon-black-market options will be preferred if the alternatives are of equal quality"
                )

                applySModsButton = addToggleButton(
                    label = "Apply SMods",
                    memoryKey = "\$FBA_applySMods",
                    tooltipText = "Spend story points to apply SMods to your ship.\n\nIf S-mods are installed, the autofit cannot be undone.",
                    default = false
                )
            }

            // Add the buttons element to the panel
            baseVariantPanel.addUIElement(toggleButtonsElement)

            autofitPanel.addComponent(baseVariantPanel)

        }

        // sync all the selectors
        for (index in selectorPlugins.indices) {
            val selectorPlugin = selectorPlugins[index]

            selectorPlugin.onHover { event ->
                if (selectorPlugin.autofitSpec == null)
                    return@onHover

                if (Keyboard.isKeyDown(Keyboard.KEY_F2))
                    Global.getSettings().showCodex(selectorPlugin.autofitSpec!!.variant.createFleetMember())
            }
            selectorPlugin.onPressOutside {
                if (selectorPlugin.autofitSpec != null) highlightBasedOnVariant(selectorPlugin.autofitSpec!!.variant, baseVariant, selectorPlugin) // Mostly for applying the different flux stat symbol on alteration of flux stats.
            }
            selectorPlugin.onHoverEnter {
                Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f)

                if (!makeAndRemoveLoadouts) return@onHoverEnter

                if (autofitPlugin.draggedAutofitSpec != null) {
                    selectorPlugin.draggingAutofitSpec = true
                }
            }
            selectorPlugin.onHoverExit {
                selectorPlugin.draggingAutofitSpec = false

                if (!makeAndRemoveLoadouts) return@onHoverExit
                if (!selectorPlugin.hasClicked || selectorPlugin.autofitSpec === autofitPlugin.draggedAutofitSpec) return@onHoverExit

                //Global.getSoundPlayer().playUISound("ui_char_reset", 1f, 1f)
                Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f)

                autofitPlugin.draggedAutofitSpec = selectorPlugin.autofitSpec
                selectorPlugin.selectorPanel.opacity = 0.15f
            }
            selectorPlugin.onClickRelease { event -> // Load variant
                if (fleetMember == null)
                    return@onClickRelease

                selectorPlugin.selectorPanel.opacity = 1f

                if (selectorPlugin.autofitSpec == null || autofitPlugin.draggedAutofitSpec != null || event.isCtrlDown || selectorPlugin.noClickFader) return@onClickRelease // If no variant. or dragging self, do nothing

                fun applyVariant(autofitSpec: AutofitSpec?, applySMods: Boolean = false) {
                    applyVariantInRefitScreen(
                        baseVariant, autofitSpec!!.variant, fleetMember, ship!!, shipDisplay!!, refitPanel!!,
                        allowCargo = cargoButton?.isChecked == true, allowStorage = storageButton?.isChecked == true, allowMarket = marketButton?.isChecked == true, allowBlackMarket = blackMarketButton?.isChecked == true, applySMods = applySMods
                    )

                    //Remake the baseSelector
                    baseVariantSelectorPlugin!!.selectorPanel.clearChildren()
                    baseVariantSelectorPlugin.autofitSpec = AutofitSpec(variant = baseVariant, source = null)
                    makeTooltip(baseVariantSelectorPlugin.selectorPanel, baseVariant)
                    AutofitSelector.createAutofitSelectorChildren(
                        baseVariantSelectorPlugin.autofitSpec!!,
                        containerPanelWidth - 2f, addDescription = false, centerTitle = true,
                        selectorPanel = baseVariantSelectorPlugin.selectorPanel
                    )

                    selectorPlugins.forEach {
                        if (it.autofitSpec != null) highlightBasedOnVariant(it.autofitSpec!!.variant, baseVariant, it)
                        else deHighlight(it)
                    }
                    selectorPlugin.isSelected = true
                }


                if (applySModsButton?.isChecked == true && !ModSettings.forceAutofit &&
                    (selectorPlugin.autofitSpec!!.variant.sMods.any { it !in baseVariant.sMods } || selectorPlugin.autofitSpec!!.variant.sModdedBuiltIns.any { it !in baseVariant.sModdedBuiltIns })
                ) {
                    val (sModsToApply, bonusXpToGrant) = sModHandlerTemp(ship!!, baseVariant, selectorPlugin.autofitSpec!!.variant)
                    if (sModsToApply.isEmpty())
                        return@onClickRelease

                    val dialog = DialogPanel(headerTitle = "Use Story Points to Apply SMods")
                    dialog.onCreateUI(450f, 110f) { ui ->
                        ui.addPara("This will consume ${sModsToApply.size} Story points and give ${bonusXpToGrant.toInt()} bonus xp", 0f).setAlignment(Alignment.MID)
                        dialog.setupConfirmCancelSection(confirmText = "Yes", cancelText = "No", alignment = Alignment.MID)
                    }
                    dialog.onConfirm { ->
                        applyVariant(selectorPlugin.autofitSpec, true)
                    }

                } else {
                    applyVariant(selectorPlugin.autofitSpec)
                }

            }
            selectorPlugin.onClickReleaseOutside {
                selectorPlugin.selectorPanel.opacity = 1f
            }
            selectorPlugin.onClickReleaseNoInitClick { // Clicked and dragged from another autofit panel
                selectorPlugin.selectorPanel.opacity = 1f

                if (autofitPlugin.draggedAutofitSpec == null) return@onClickReleaseNoInitClick

                if (selectorPlugin.autofitSpec != null) {
                    if (selectorPlugin.autofitSpec !== autofitPlugin.draggedAutofitSpec)
                        DisplayMessage.showMessage("Slot already occupied", Color.YELLOW)
                    return@onClickReleaseNoInitClick
                }
                if (selectorPlugin.addXIfAutofitSpecNull) {
                    DisplayMessage.showMessage("Slot is reserved. Hover over the slot for more details.", Color.YELLOW)
                    return@onClickReleaseNoInitClick
                }
                //Slot does not have an autofitSpec, and an autofitSpec is being dragged.

                val settings: VariantSettings
                var shipDirectory: ShipDirectory?

                if (autofitPlugin.draggedAutofitSpec!!.source == null) {
                    settings = ModSettings.getConfiguredVariantSettings()

                    shipDirectory = ShipDirectoryService.getShipDirectoryWithPrefix(currentPrefix)

                } else {
                    settings = VariantSettings().apply {
                        excludeTagsWithID = getDefaultExcludeVariantTags()
                    }

                    shipDirectory = autofitPlugin.draggedAutofitSpec!!.source
                }

                if (shipDirectory == null) {
                    DisplayMessage.showError("Could not find ship directory with prefix $currentPrefix")
                    return@onClickReleaseNoInitClick
                }

                val indexInMenu = index - coreEffectiveHullAutofitSpecs.size

                val draggedVariant = copyVariant(autofitPlugin.draggedAutofitSpec!!.variant, settings) // Copied to apply settings and ensure is variant that would be loaded if brought up later

                if (ModSettings.autofitNoSModdedBuiltInWhenNotBuiltInMod) {
                    draggedVariant.sModdedBuiltIns.toList().forEach {
                        if (it !in draggedVariant.hullSpec.builtInMods) {
                            draggedVariant.completelyRemoveMod(it)
                        }
                    }
                }

                val equalVariant = ShipDirectoryService.getLoadoutVariantsForHullspec(currentPrefix, draggedVariant.hullSpec).firstOrNull { compareVariantContents(it, draggedVariant) }

                val shipVariantID: String
                if (equalVariant != null) { // Variant already exists?

                    Global.getSoundPlayer().playUISound("ui_button_pressed", 0.94f, 1f)

                    val missing = shipDirectory.getShipEntry(equalVariant.hullVariantId)?.missingElements
                    if (missing == null) {
                        val newShipDirectory = ShipDirectoryService.getVariantSourceShipDirectory(equalVariant)
                        if (newShipDirectory != null) {//equalVariant exists, but not from this shipDirectory
                            //shipDirectory = newShipDirectory
                            //missing = shipDirectory.getShipEntry(equalVariant.hullVariantId)?.missingElements
                            //    ?: return@onClickReleaseNoInitClick
                            DisplayMessage.showError("ERROR. Cross ShipDirectory UI is no longer implemented.")
                            return@onClickReleaseNoInitClick
                        } else {
                            DisplayMessage.showError("ERROR. Could not find variant, but variant already existed?")
                            return@onClickReleaseNoInitClick
                        }
                    }

                    val isImport = shipDirectory.isShipImported(equalVariant.hullVariantId)

                    shipDirectory.removeShip(equalVariant.hullVariantId, editVariantFile = false)
                    shipVariantID = shipDirectory.addShip(
                        equalVariant,
                        setVariantID = equalVariant.hullVariantId,
                        missingFromVariant = missing,
                        inputDesiredIndexInMenu = indexInMenu,
                        editVariantFile = false, settings = settings, tagAsImport = isImport
                    )

                    deleteSelector(selectorPlugins.firstOrNull {
                        it.autofitSpec?.source != null && it.autofitSpec?.variant != null &&
                                compareVariantContents(
                                    shipDirectory.getShip(shipVariantID)!!,
                                    copyVariant(it.autofitSpec!!.variant, settings) // Copied to apply settings
                                )
                    })
                } else {
                    Global.getSoundPlayer().playUISound("ui_button_pressed", 0.94f, 1f)

                    shipVariantID = shipDirectory.addShip(
                        draggedVariant,
                        inputDesiredIndexInMenu = indexInMenu, settings = settings
                    )
                }

                // Remake the new selector
                selectorPlugin.noClickFader = false
                selectorPlugin.autofitSpec = autofitPlugin.draggedAutofitSpec!!.copy(variant = shipDirectory.getShip(shipVariantID)!!, source = shipDirectory, desiredIndexInMenu = indexInMenu, description = shipDirectory.getDescription(shipVariantID))
                AutofitSelector.createAutofitSelectorChildren(
                    selectorPlugin.autofitSpec!!,
                    selectorWidth,
                    selectorPlugin.selectorPanel
                )

                selectorPlugins.forEach {
                    if (it.autofitSpec != null) highlightBasedOnVariant(it.autofitSpec!!.variant, baseVariant, it)
                    else deHighlight(it)
                }
                makeTooltip(
                    selectorPlugin.selectorPanel, selectorPlugin.autofitSpec!!.variant, selectorPlugin.autofitSpec!!.missing,
                    if (index % selectorsPerRow >= selectorsPerRow / 2) TooltipMakerAPI.TooltipLocation.LEFT else TooltipMakerAPI.TooltipLocation.RIGHT
                )
                if (makeAndRemoveLoadouts)
                    removeSelectorPanelButton(selectorPlugin.selectorPanel, selectorPlugin.autofitSpec!!) // Allow it to be removed
            }

            selectorPlugin.onClick { event ->
                if (selectorPlugin.autofitSpec == null) return@onClick // If no variant. there's nothing to do

                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)

                if (event.isCtrlDown) {//Copy variant to clipboard
                    ClipboardMisc.saveVariantToClipboard(selectorPlugin.autofitSpec!!.variant, event.isShiftDown)
                }
            }
        }


        // add scroll at end after setting heightSoFar, needed when using addCustom to the tooltip
        val rows = ((allAutofitSpecs.size - 1) / selectorsPerRow) + 1
        scrollerTooltip.heightSoFar = endPad * 2 + prev!!.height * rows + midPad * (rows - 1)
        autofitPanel.addUIElement(scrollerTooltip)
        return autofitPanel
    }

    private fun highlightBasedOnVariant(
        variant: ShipVariantAPI,
        baseVariant: ShipVariantAPI,
        selectorPlugin: AutofitSelector.AutofitSelectorPlugin
    ) {
        deHighlight(selectorPlugin)

        var equalDefault = compareVariantContents(
            variant,
            baseVariant,
            VariantUtils.CompareOptions.allFalse(modules = true, hullMods = true, convertSModsToRegular = true, weapons = true, wings = true)
        )
        var outline = true

        if (!equalDefault && ModSettings.autofitNoSModdedBuiltInWhenNotBuiltInMod) {
            val baseVariantClone = baseVariant.clone()
            //We want to highlight but not outline the variant if it has sModdedBuiltIns that the HullSpec does not have as a built-in hullmod.
            baseVariantClone.sModdedBuiltIns.forEach {
                if (it !in baseVariant.hullSpec.builtInMods)
                    baseVariantClone.completelyRemoveMod(it)
            }

            if (compareVariantContents(
                    variant,
                    baseVariantClone,
                    VariantUtils.CompareOptions.allFalse(modules = true, hullMods = true, convertSModsToRegular = true, weapons = true, wings = true)
                )
            ) {
                equalDefault = true
                outline = false
            }
        }

        if (equalDefault) {
            selectorPlugin.isSelected = true
            selectorPlugin.highlightFader.forceIn()

            if (outline)
                outlinePanelBasedOnVariant(baseVariant, variant, selectorPlugin)

            val diffWeaponGroups = !compareVariantContents(
                variant,
                baseVariant,
                VariantUtils.CompareOptions.allFalse(modules = true, weaponGroups = true)
            )
            val diffFluxStats = !compareVariantContents(
                variant,
                baseVariant,
                VariantUtils.CompareOptions.allFalse(modules = true, flux = true)
            )
            if (diffFluxStats) {
                selectorPlugin.diffFluxStats = true
            }
            if (diffWeaponGroups) {
                selectorPlugin.diffWeaponGroups = true
            }
        }
    }

    private fun deHighlight(selectorPlugin: AutofitSelector.AutofitSelectorPlugin) {
        selectorPlugin.isSelected = false
        selectorPlugin.comparisonStatus = AutofitSelector.AutofitSelectorPlugin.ComparisonStatus.DEFAULT
        selectorPlugin.diffFluxStats = false
        selectorPlugin.diffWeaponGroups = false
    }

    private fun outlinePanelBasedOnVariant(
        baseVariant: ShipVariantAPI,
        variant: ShipVariantAPI,
        selectorPlugin: AutofitSelector.AutofitSelectorPlugin
    ) {
        val compareBaseVariant = baseVariant.clone()
        val compareVariant = variant.clone()

        //Treat built in DMods like regular hullmods.
        //compareBaseVariant.allDMods().forEach { compareBaseVariant.hullMods.add(it) }
        //compareVariant.allDMods().forEach { compareVariant.hullMods.add(it) }

        //Treat SModdedBuiltIns like SMods.
        compareBaseVariant.sModdedBuiltIns.forEach { compareBaseVariant.addPermaMod(it, true) }
        compareVariant.sModdedBuiltIns.forEach { compareVariant.addPermaMod(it, true) }

        val equalMods = compareVariantHullMods(
            compareVariant,
            compareBaseVariant,
            VariantUtils.CompareOptions(builtInHullMods = false, hiddenHullMods = false)
        )

        var equalSMods = false
        var unequalSMods = false
        if (compareBaseVariant.sMods.isNotEmpty()) {
            val compareBaseVariantTemp = compareBaseVariant.clone()
            processSModsForComparison(compareBaseVariantTemp, true)
            equalSMods = compareVariantHullMods(
                compareVariant,
                compareBaseVariantTemp,
                VariantUtils.CompareOptions(builtInHullMods = false, hiddenHullMods = false)
            )
        }
        if (compareVariant.sMods.isNotEmpty()) {
            val compareVariantTemp = compareVariant.clone()
            processSModsForComparison(compareVariantTemp, true)
            unequalSMods = compareVariantHullMods(
                compareVariantTemp,
                compareBaseVariant,
                VariantUtils.CompareOptions(builtInHullMods = false, hiddenHullMods = false)
            )
        }

        var unequalDMod = false
        if (compareBaseVariant.allDMods().isNotEmpty()) {
            compareBaseVariant.allDMods().forEach { compareBaseVariant.completelyRemoveMod(it); compareBaseVariant.hullMods.remove(it) }
            if (compareBaseVariant.sMods.isNotEmpty()) {
                unequalDMod = compareVariantHullMods(
                    compareVariant,
                    compareBaseVariant,
                    VariantUtils.CompareOptions(builtInHullMods = false, hiddenHullMods = false)
                )
            } else {
                unequalDMod = compareVariantHullMods(
                    compareVariant,
                    compareBaseVariant,
                    VariantUtils.CompareOptions(builtInHullMods = false, hiddenHullMods = false, convertSModsToRegular = true)
                )
            }
        }

        if (equalMods) {
            selectorPlugin.comparisonStatus = AutofitSelector.AutofitSelectorPlugin.ComparisonStatus.EQUAL
        } else if (equalSMods) {
            selectorPlugin.comparisonStatus = AutofitSelector.AutofitSelectorPlugin.ComparisonStatus.BETTER
        } else if (unequalDMod || unequalSMods) {
            selectorPlugin.comparisonStatus = AutofitSelector.AutofitSelectorPlugin.ComparisonStatus.WORSE
        }
    }

    private fun deleteSelector(selectorPlugin: AutofitSelector.AutofitSelectorPlugin?) {
        selectorPlugin?.autofitSpec = null
        selectorPlugin?.noClickFader = true
        selectorPlugin?.selectorPanel?.clearChildren()
        val tooltip = selectorPlugin?.selectorPanel?.safeInvoke("getTooltip")
        tooltip?.safeInvoke("removeSelf")

        if (selectorPlugin != null)
            deHighlight(selectorPlugin)
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
        //removeVariantButton.setButtonPressedSound("ui_refit_slot_cleared_large")
        removeVariantButton.xAlignOffset = selectorPanel.right - removeVariantButton.right
        removeVariantButton.yAlignOffset = selectorPanel.top - removeVariantButton.top
        removeVariantButton.onClick {
            deleteLoadoutVariant(currentPrefix, newSpec.variant.hullVariantId)
            //selectorPanel.parent?.removeComponent(selectorPanel)
            //selectorPanel.opacity = 0f
            val selectorPlugin = selectorPanel.plugin as AutofitSelector.AutofitSelectorPlugin
            deleteSelector(selectorPlugin)
        }
    }

    internal fun makeTooltip(
        selectorPanel: CustomPanelAPI,
        variant: ShipVariantAPI,
        missingFromVariant: MissingElements? = null,
        location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.RIGHT,
        margin: Float? = null,
        width: Float = if (ModSettings.showDebug) 400f else 350f
    ) {
        val allDMods = getAllDMods()
        val sizeOrder = mapOf(
            WeaponAPI.WeaponSize.LARGE to 0,
            WeaponAPI.WeaponSize.MEDIUM to 1,
            WeaponAPI.WeaponSize.SMALL to 2
        )
        val spaces = "                    "

        selectorPanel.addTooltip(location, width, margin) { tooltip ->
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

            val capWidth = tooltip.computeStringWidth(variant.numFluxCapacitors.toString()) * 2
            val ventWidth = tooltip.computeStringWidth(variant.numFluxVents.toString()) * 2
            var usualWidth = tooltip.computeStringWidth("99") * 2
            if (capWidth > usualWidth)
                usualWidth = capWidth
            if (ventWidth > usualWidth)
                usualWidth = ventWidth

            tooltip.beginTable(
                Color.GRAY, Color.DARK_GRAY, Color.LIGHT_GRAY,
                16f, false, false,
                "", usualWidth - 4f,
                "", width - 32f,
            ) as? UITable

            tooltip.addRow(
                Alignment.RMID,
                Misc.getHighlightColor(),
                variant.numFluxCapacitors.toString(),
                Alignment.LMID,
                Misc.getTextColor(),
                "flux capacitors"
            )
            tooltip.addRow(
                Alignment.RMID,
                Misc.getHighlightColor(),
                variant.numFluxVents.toString(),
                Alignment.LMID,
                Misc.getTextColor(),
                "flux vents"
            )

            tooltip.addTable("", 0, 12f)

            tooltip.addPara("Hold CTRL and click to copy loadout to clipboard", 24f)

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
                    tooltip.addPara("Required Mods:", 16f)

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
                    "Failed to load:\nWeapons: ${missingFromVariant.weaponIds}\nWings: ${missingFromVariant.wingIds}\nHullMods: ${missingFromVariant.hullModIds}",
                    16f
                )
            }


            if (ModSettings.showDebug) {
                tooltip.addPara("\n\nDEBUG: VariantID = ${variant.hullVariantId}", 2f)
                tooltip.addPara("DEBUG: HullID = ${variant.hullSpec.hullId}", 2f)
                tooltip.addPara("DEBUG: Compatible with base = ${variant.hullSpec.isCompatibleWithBase}", 2f)
                tooltip.addPara("DEBUG: BaseHullID = ${variant.hullSpec.baseHullId}", 2f)
                tooltip.addPara("DEBUG: DParentHullID = ${variant.hullSpec.dParentHullId}", 2f)
                tooltip.addPara("\nDEBUG: Tags = ${variant.tags}", 2f)
                tooltip.addPara("\nDEBUG: SModdedBuiltIns = ${variant.sModdedBuiltIns}", 2f)
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