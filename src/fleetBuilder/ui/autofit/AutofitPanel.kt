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
import fleetBuilder.config.ModSettings.getDefaultExcludeVariantTags
import fleetBuilder.persistence.variant.DataVariant.copyVariant
import fleetBuilder.persistence.variant.VariantSettings
import fleetBuilder.ui.autofit.AutofitSelector.AutofitSelectorPlugin.ComparisonStatus
import fleetBuilder.ui.autofit.AutofitSelector.createAutofitSelectorChildren
import fleetBuilder.ui.autofit.AutofitSelector.createShipPreview
import fleetBuilder.util.*
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.LoadoutManager.deleteLoadoutVariant
import fleetBuilder.variants.LoadoutManager.getCoreAutofitSpecsForShip
import fleetBuilder.variants.LoadoutManager.getLoadoutAutofitSpecsForShip
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.ShipDirectory
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
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke
import java.awt.Color


/**
 * Original author
 * @author Starficz
 * Heavily modified otherwise
 */
internal object AutofitPanel {
    private const val BACKGROUND_ALPHA = 0.7f

    internal class AutofitPanelPlugin(private val refitTab: UIPanelAPI) : BaseCustomUIPanelPlugin() {
        lateinit var autofitPanel: CustomPanelAPI
        var baseVariantPanel: CustomPanelAPI? = null

        override fun renderBelow(alphaMult: Float) {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            // Background dark fadeout
            val bgColor = Color.BLACK
            val bgAlpha = BACKGROUND_ALPHA * bgColor.alphaf * alphaMult
            GL11.glColor4f(bgColor.redf, bgColor.greenf, bgColor.bluef, bgAlpha)
            val screenW = Global.getSettings().screenWidth
            val screenH = Global.getSettings().screenHeight
            val buffer = -5f
            val tx = refitTab.x - buffer
            val ty = refitTab.y - buffer
            val tw = refitTab.width + buffer * 2
            val th = refitTab.height + buffer * 2
            // Left
            GL11.glRectf(0f, 0f, tx, screenH)
            // Right
            GL11.glRectf(tx + tw, 0f, screenW, screenH)
            // Top
            GL11.glRectf(tx, ty + th, tx + tw, screenH)
            // Bottom
            GL11.glRectf(tx, 0f, tx + tw, ty)


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

        var draggedAutofitSpec: AutofitSpec? = null
        override fun processInput(events: MutableList<InputEventAPI>) {
            for (event in events) {
                if (event.isConsumed) continue

                if (event.isMouseUpEvent) {
                    draggedAutofitSpec = null
                }

                if (event.isKeyboardEvent && event.eventValue == Keyboard.KEY_ESCAPE) {
                    autofitPanel.parent?.removeComponent(autofitPanel)
                    draggedPanel?.parent?.removeComponent(draggedPanel!!)
                    event.consume()
                } else if ((
                            FBMisc.isMouseHoveringOverComponent(autofitPanel, 8f) ||
                                    (baseVariantPanel != null && FBMisc.isMouseHoveringOverComponent(baseVariantPanel!!, 8f)) ||
                                    !FBMisc.isMouseWithinBounds(refitTab.x, refitTab.y, refitTab.width, refitTab.height) // block if outside tab
                            ) && (
                            event.isKeyboardEvent ||
                                    event.isMouseMoveEvent ||
                                    event.isMouseDownEvent ||
                                    event.isMouseScrollEvent
                            )
                ) {
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
                draggedPanel = createShipPreview(draggedAutofitSpec!!.variant, selectorWidth, selectorWidth)

            val coreUI = ReflectionMisc.getCoreUI() ?: return
            if (coreUI.getChildrenCopy().find { it === draggedPanel } == null)
                coreUI.addComponent(draggedPanel)

            draggedPanel!!.position.setXAlignOffset(Global.getSettings().mouseX.toFloat() - draggedPanel!!.width / 2f)
            draggedPanel!!.position.setYAlignOffset(Global.getSettings().mouseY.toFloat() - draggedPanel!!.height / 2f)

        }
    }

    internal fun createMagicAutofitPanel(
        refitTab: UIPanelAPI, refitPanel: UIPanelAPI, coreUI: CoreUIAPI,
        width: Float, height: Float
    ): CustomPanelAPI {

        val autofitPlugin = AutofitPanelPlugin(refitTab)
        val autofitPanel = Global.getSettings().createCustom(width, height, autofitPlugin)// Background Panel
        autofitPlugin.autofitPanel = autofitPanel

        // borders are drawn outside of panel, so +2 needed to lineup scrollbar with border
        val scrollerTooltip = autofitPanel.createUIElement(width + 2f, height, true) // Tooltip on background panel
        scrollerTooltip.position.inTL(0f, 0f)

        val shipDisplay = refitPanel.invoke("getShipDisplay") as? UIPanelAPI ?: return autofitPanel
        val baseVariant = shipDisplay.invoke("getCurrentVariant") as? HullVariantSpec
            ?: return autofitPanel
        val fleetMember = refitPanel.invoke("getMember") as? FleetMemberAPI ?: return autofitPanel
        val ship = shipDisplay.invoke("getShip") as? ShipAPI ?: return autofitPanel
        val modWidget = ReflectionMisc.getRefitPanelModWidget(refitPanel) ?: return autofitPanel

        val endPad = 6f
        val midPad = 5f
        val selectorsPerRow = ModSettings.selectorsPerRow
        val selectorWidth = (autofitPanel.width - (endPad * 2 + midPad * (selectorsPerRow - 1))) / selectorsPerRow
        autofitPlugin.selectorWidth = selectorWidth

        var firstInRow: UIPanelAPI? = null
        var prev: UIPanelAPI? = null

        val selectorPlugins = mutableListOf<AutofitSelector.AutofitSelectorPlugin>()

        val coreEffectiveHullAutofitSpecs = getCoreAutofitSpecsForShip((baseVariant as ShipVariantAPI).hullSpec)
        val loadoutEffectiveHullAutofitSpecs = getLoadoutAutofitSpecsForShip((baseVariant as ShipVariantAPI).hullSpec, coreEffectiveHullAutofitSpecs.size).values.flatten()


        // Combine both lists into one, because they share the same index space in the menu
        val combinedSpecs = coreEffectiveHullAutofitSpecs + loadoutEffectiveHullAutofitSpecs

        // Sort by desired index first
        val sortedSpecs = combinedSpecs.sortedBy { it.desiredIndexInMenu }

        // Find the maximum index any spec wants
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
        val extraNulls = fillToRow + selectorsPerRow

        // Build final list with padding
        val allAutofitSpecs: List<AutofitSpec?> = indexedSpecs.subList(0, lastNonNullIndex + 1) + List(extraNulls) { null }


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

        //Make base variant selector panel.

        val containerPanelWidth = refitTab.width - autofitPanel.width - 7f
        val containerPanelHeight = refitTab.height - modWidget.height + 44f
        val descriptorHeight = 34f
        val topPad = 5f
        // Create container panel with modWidget size
        val baseVariantPanel = autofitPanel.CustomPanel(containerPanelWidth, containerPanelHeight) {
            (plugin as ExtendableCustomUIPanelPlugin).renderBelow { alphaMult ->
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
        baseVariantPanel.position.inTL(autofitPanel.width + 1f, -autofitPanel.y + (modWidget.height - autofitPanel.y - 50f))

        val containerTextElement = baseVariantPanel.createUIElement(baseVariantPanel.width, descriptorHeight - topPad, false)
        baseVariantPanel.addUIElement(containerTextElement)
        with(containerTextElement) {
            position.inTL(0f, topPad)
            setTitleOrbitronVeryLarge()
            val label = addTitle("Current Variant")
            label.position.inTL((baseVariantPanel.width - label.computeTextWidth(label.text)) / 2f, 0f)
        }

        // Create base selector panel
        val baseVariantSelectorPanel = AutofitSelector.createAutofitSelector(
            AutofitSpec(baseVariant, null),
            containerPanelWidth - 2f, addDescription = false, centerTitle = true
        )
        makeTooltip(baseVariantSelectorPanel, baseVariant)

        val baseVariantSelectorPlugin = baseVariantSelectorPanel.plugin as AutofitSelector.AutofitSelectorPlugin
        baseVariantSelectorPlugin.isBase = true
        baseVariantSelectorPlugin.noClickFader = true
        baseVariantSelectorPlugin.comparisonStatus = ComparisonStatus.EQUAL
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
            baseVariantPanel.height - baseVariantSelectorPanel.height,
            false
        )

        // Position just below the selector
        toggleButtonsElement.position.inTL(2f, descriptorHeight + baseVariantSelectorPanel.height + 4f)

        toggleButtonsElement.addPara("Click and drag to an empty slot to save", 0f)
        toggleButtonsElement.addSpacer(12f)

        val checkboxHeight = 24f
        val checkboxPad = 4f

        val fleetMemory = fleetMember.fleetData?.fleet?.memoryWithoutUpdate

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

            button.addTooltip(TooltipMakerAPI.TooltipLocation.RIGHT, 400f) { tooltip ->
                tooltip.addPara(tooltipText, 0f)
            }

            button.onClick {
                fleetMemory?.set(memoryKey, button.isChecked)
            }

            return button
        }

        val cargoButton = addToggleButton(
            label = "Use ordnance from cargo",
            memoryKey = "\$FBA_useCargo",
            tooltipText = "Use weapons and fighter LPCs from your fleet's cargo holds."
        )

        val storageButton = addToggleButton(
            label = "Use ordnance from storage",
            memoryKey = "\$FBA_useStorage",
            tooltipText = "Use weapons and fighter LPCs from your local storage facilities."
        )

        val marketButton = addToggleButton(
            label = "Buy ordnance from market",
            memoryKey = "\$FBA_useMarket",
            tooltipText = "Buy weapons and fighter LPCs from market, if docked at one.\n\nOrdnance from your cargo will be preferred"
        )

        val blackMarketButton = addToggleButton(
            label = "Allow black market purchases",
            memoryKey = "\$FBA_useBlackMarket",
            tooltipText = "Buy weapons and fighter LPCs from the black market.\n\nNon-black-market options will be preferred if the alternatives are of equal quality"
        )
        // Add the buttons element to the panel
        baseVariantPanel.addUIElement(toggleButtonsElement)



        autofitPanel.addComponent(baseVariantPanel)


        // sync all the selectors
        for (index in selectorPlugins.indices) {
            val selectorPlugin = selectorPlugins[index]

            selectorPlugin.onPressOutside {
                if (selectorPlugin.autofitSpec != null) highlightBasedOnVariant(selectorPlugin.autofitSpec!!.variant, baseVariant, selectorPlugin) // Mostly for applying the different flux stat symbol on alteration of flux stats.
            }
            selectorPlugin.onHoverEnter {
                Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f)
            }
            selectorPlugin.onHoverExit {
                if (!selectorPlugin.hasClicked || selectorPlugin.autofitSpec === autofitPlugin.draggedAutofitSpec) return@onHoverExit

                //Global.getSoundPlayer().playUISound("ui_char_reset", 1f, 1f)
                Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f)

                autofitPlugin.draggedAutofitSpec = selectorPlugin.autofitSpec
                selectorPlugin.selectorPanel.opacity = 0.15f
            }
            selectorPlugin.onClickRelease { event -> // Load variant
                selectorPlugin.selectorPanel.opacity = 1f

                if (selectorPlugin.autofitSpec == null || autofitPlugin.draggedAutofitSpec != null || event.isCtrlDown || selectorPlugin.noClickFader) return@onClickRelease // If no variant. or dragging self, do nothing

                fun applyVariant(autofitSpec: AutofitSpec?) {
                    applyVariantInRefitScreen(
                        baseVariant, autofitSpec!!.variant, fleetMember, ship, coreUI, shipDisplay, refitPanel,
                        allowCargo = cargoButton.isChecked, allowStorage = storageButton.isChecked, allowMarket = marketButton.isChecked, allowBlackMarket = blackMarketButton.isChecked
                    )

                    //Remake the baseSelector
                    baseVariantSelectorPlugin.selectorPanel.clearChildren()
                    baseVariantSelectorPlugin.autofitSpec = AutofitSpec(variant = baseVariant, source = null)
                    makeTooltip(baseVariantSelectorPlugin.selectorPanel, baseVariant)
                    createAutofitSelectorChildren(
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

                //Too complicated
                /*if (applySModsButton.isChecked && !ModSettings.forceAutofit) {
                    val maxSMods = ship.mutableStats.dynamic.getStat(Stats.MAX_PERMANENT_HULLMODS_MOD).modifiedInt

                    val currentSMods = baseVariant.sMods.size
                    val newSMods = selectorPlugin.autofitSpec!!.variant.sMods.size
                    val sModsToApply = min(newSMods, maxSMods) - currentSMods
                    if (sModsToApply <= 0) {
                        DisplayMessage.showMessage("No free SMods to apply")
                        applyVariant(selectorPlugin.autofitSpec)
                        return@onClickRelease
                    }

                    val xpToGrant = 0f
                    xpToGrant += Misc.getBuildInBonusXP()

                    Global.getSector().playerStats.addBonusXP(xpToGrant.toLong(), true, null, true)

                    val areYouSureDialog = PopUpUIDialog("Use Story Points to apply SMods?", addConfirmButton = true, addCancelButton = true)
                    areYouSureDialog.cancelButtonName = "No"
                    areYouSureDialog.confirmButtonName = "Yes"
                    areYouSureDialog.confirmAndCancelAlignment = Alignment.MID
                    areYouSureDialog.addParagraph("This will consume x Story points and give x bonus xp")

                    areYouSureDialog.onConfirm { _ ->
                        applyVariant(selectorPlugin.autofitSpec)
                    }

                    DialogUtil.initPopUpUI(areYouSureDialog, 380f, 110f)
                } else {*/
                applyVariant(selectorPlugin.autofitSpec)
                //}
            }
            selectorPlugin.onClickReleaseOutside {
                selectorPlugin.selectorPanel.opacity = 1f
            }
            selectorPlugin.onClickReleaseNoInitClick { // Clicked and dragged from another autofit panel
                selectorPlugin.selectorPanel.opacity = 1f

                if (selectorPlugin.autofitSpec != null || autofitPlugin.draggedAutofitSpec == null) return@onClickReleaseNoInitClick

                val settings: VariantSettings
                var shipDirectory: ShipDirectory?

                if (autofitPlugin.draggedAutofitSpec!!.source == null) {
                    settings = ModSettings.getConfiguredVariantSettings()

                    shipDirectory = LoadoutManager.getShipDirectoryWithPrefix(ModSettings.defaultPrefix)

                } else {
                    settings = VariantSettings().apply {
                        excludeTagsWithID = getDefaultExcludeVariantTags()
                    }

                    shipDirectory = autofitPlugin.draggedAutofitSpec!!.source
                }

                if (shipDirectory == null) {
                    DisplayMessage.showError("Could not find ship directory with prefix ${ModSettings.defaultPrefix}")
                    return@onClickReleaseNoInitClick
                }

                val indexInMenu = index - coreEffectiveHullAutofitSpecs.size

                val draggedVariant = copyVariant(autofitPlugin.draggedAutofitSpec!!.variant, settings) // Copied to apply settings and ensure is variant that would be loaded if brought up later

                val equalVariant = LoadoutManager.getLoadoutVariantsForHullspec(draggedVariant.hullSpec).firstOrNull { compareVariantContents(it, draggedVariant, compareTags = true) }

                val shipVariantID: String
                if (equalVariant != null) { // Variant already exists?

                    Global.getSoundPlayer().playUISound("ui_button_pressed", 0.94f, 1f)

                    var missing = shipDirectory.getShipMissings(equalVariant.hullVariantId)
                    if (missing == null) {
                        //equalVariant exists, but not from this shipDirectory
                        shipDirectory = LoadoutManager.getVariantSourceShipDirectory(equalVariant)
                            ?: return@onClickReleaseNoInitClick
                        missing = shipDirectory.getShipMissings(equalVariant.hullVariantId)
                            ?: return@onClickReleaseNoInitClick
                    }

                    shipDirectory.removeShip(equalVariant.hullVariantId, editVariantFile = false)
                    shipVariantID = shipDirectory.addShip(
                        equalVariant, missing,
                        inputDesiredIndexInMenu = indexInMenu,
                        setVariantID = equalVariant.hullVariantId.removePrefix(shipDirectory.prefix + "_"),
                        editVariantFile = false, settings = settings
                    )

                    deleteSelector(selectorPlugins.firstOrNull {
                        it.autofitSpec?.source != null && it.autofitSpec?.variant != null &&
                                compareVariantContents(
                                    shipDirectory.getShip(shipVariantID)!!,
                                    copyVariant(it.autofitSpec!!.variant, settings), // Copied to apply settings
                                    compareTags = true
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
                selectorPlugin.autofitSpec = autofitPlugin.draggedAutofitSpec!!.copy(variant = shipDirectory.getShip(shipVariantID)!!, source = shipDirectory, desiredIndexInMenu = indexInMenu, description = shipDirectory.getDescription())
                createAutofitSelectorChildren(
                    selectorPlugin.autofitSpec!!,
                    selectorWidth,
                    selectorPlugin.selectorPanel
                )

                selectorPlugins.forEach {
                    if (it.autofitSpec != null) highlightBasedOnVariant(it.autofitSpec!!.variant, baseVariant, it)
                    else deHighlight(it)
                }
                makeTooltip(selectorPlugin.selectorPanel, selectorPlugin.autofitSpec!!.variant, selectorPlugin.autofitSpec!!.missing)
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

        val equalDefault = compareVariantContents(
            variant,
            baseVariant,
            compareWeaponGroups = false,
            compareFlux = false,
            compareBuiltInHullMods = false,
            compareDMods = false,
            convertSModsToRegular = true,
            compareHiddenHullMods = false,
            useEffectiveHull = true
        )

        if (equalDefault) {
            selectorPlugin.isSelected = true
            selectorPlugin.highlightFader.forceIn()

            outlinePanelBasedOnVariant(baseVariant, variant, selectorPlugin)

            val diffWeaponGroups = !compareVariantContents(
                variant,
                baseVariant,
                compareWeaponGroups = true,
                compareFlux = false,
                compareHullMods = false,
                useEffectiveHull = true
            )
            val diffFluxStats = !compareVariantContents(
                variant,
                baseVariant,
                compareWeaponGroups = false,
                compareFlux = true,
                compareHullMods = false,
                useEffectiveHull = true
            )
            if (diffFluxStats) {
                selectorPlugin.diffFluxStats = true
            }
            if (diffWeaponGroups) {
                selectorPlugin.diffWeaponGroups = true
            }
        }
    }

    fun deHighlight(selectorPlugin: AutofitSelector.AutofitSelectorPlugin) {
        selectorPlugin.isSelected = false
        selectorPlugin.comparisonStatus = ComparisonStatus.DEFAULT
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
            selectorPlugin.comparisonStatus = ComparisonStatus.EQUAL
        } else if (equalSMods) {
            selectorPlugin.comparisonStatus = ComparisonStatus.BETTER
        } else if (unequalDMod || unequalSMods) {
            selectorPlugin.comparisonStatus = ComparisonStatus.WORSE
        }
    }

    fun deleteSelector(selectorPlugin: AutofitSelector.AutofitSelectorPlugin?) {
        selectorPlugin?.autofitSpec = null
        selectorPlugin?.noClickFader = true
        selectorPlugin?.selectorPanel?.clearChildren()
        val tooltip = selectorPlugin?.selectorPanel?.invoke("getTooltip")
        tooltip?.getMethodsMatching("removeSelf")?.getOrNull(0)?.invoke(tooltip) // Safe way to remove the tooltip

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
            deleteLoadoutVariant(newSpec.variant.hullVariantId)
            //selectorPanel.parent?.removeComponent(selectorPanel)
            //selectorPanel.opacity = 0f
            val selectorPlugin = selectorPanel.plugin as AutofitSelector.AutofitSelectorPlugin
            deleteSelector(selectorPlugin)
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

        selectorPanel.addTooltip(TooltipMakerAPI.TooltipLocation.RIGHT, width) { tooltip ->
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