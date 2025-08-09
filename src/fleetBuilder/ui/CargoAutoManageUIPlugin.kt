package fleetBuilder.ui

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.ui.trade.CargoItemStack
import fleetBuilder.features.CargoAutoManage
import fleetBuilder.features.ItemAutoManage
import fleetBuilder.features.loadCargoAutoManage
import fleetBuilder.features.saveCargoAutoManage
import fleetBuilder.features.unsetCargoAutoManage
import fleetBuilder.ui.popUpUI.PopUpUI
import fleetBuilder.ui.popUpUI.PopUpUIDialog
import fleetBuilder.util.DialogUtil
import fleetBuilder.util.Dialogs
import fleetBuilder.util.ReflectionMisc
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.invoke
import starficz.addPara
import starficz.addTooltip
import starficz.allChildsWithMethod
import starficz.getChildrenCopy
import starficz.height
import starficz.onClick
import starficz.width
import starficz.yAlignOffset

//The implementation of this is extremely scuffed, I am aware.

val defaultIcon = "graphics/factions/crest_player_flag.png"

class CargoAutoManageUIPlugin(
    val selectedSubmarket: SubmarketAPI,
    private val width: Float,
    private val height: Float,
    private val instantUp: Boolean = false
) : CustomUIPanelPlugin {

    private var panel: CustomPanelAPI
    fun getPanel() = panel

    private val dialog: PopUpUIDialog
    private val market: MarketAPI
    private val scrollerTooltip: TooltipMakerAPI

    override fun positionChanged(position: PositionAPI?) {}
    override fun renderBelow(alphaMult: Float) {}
    override fun render(alphaMult: Float) {}

    val cargoRows = mutableListOf<CargoRow>()

    data class CargoRow(
        val type: CargoAPI.CargoItemType,
        val data: Any?,
        val icon: String,
        val displayName: String,
        val imageTooltip: TooltipMakerAPI,
        val iconPanel: UIPanelAPI,
        val amountField: TextFieldAPI,
        val percentField: TextFieldAPI,
        val takeButton: ButtonAPI,
        val putButton: ButtonAPI,
        val deleteButton: ButtonAPI,
        val quickStackButton: ButtonAPI,
        var prevAmountText: String = "",
        var prevPercentText: String = "",
        var prevTakeState: Boolean = false,
        var prevPutState: Boolean = false,
        var prevQuickStackState: Boolean = false
    )

    private fun removeCargoRowOf(cargoRow: CargoRow) {
        val index = cargoRows.indexOf(cargoRow)
        if (index == -1) return

        cargoRows[index].amountField.opacity = 0f
        cargoRows[index].percentField.opacity = 0f
        cargoRows[index].takeButton.opacity = 0f
        cargoRows[index].putButton.opacity = 0f
        cargoRows[index].quickStackButton.opacity = 0f
        cargoRows[index].deleteButton.opacity = 0f
        cargoRows[index].imageTooltip.opacity = 0f
        cargoRows[index].iconPanel.opacity = 0f
        cargoRows.removeAt(index)
    }

    fun createCargoAutoManage(): CargoAutoManage {
        val itemAutoManages = mutableListOf<ItemAutoManage>()

        cargoRows.forEachIndexed { index, row ->
            itemAutoManages.add(
                ItemAutoManage(
                    row.type,
                    row.data,
                    row.icon,
                    row.displayName,
                    if (row.amountField.text.isEmpty()) null else row.amountField.text.toInt(),
                    if (row.percentField.text.isEmpty()) null else row.percentField.text.toDouble(),
                    row.takeButton.isChecked,
                    row.putButton.isChecked,
                    row.quickStackButton.isChecked
                )
            )
        }

        return CargoAutoManage(
            dialog.fieldStates["Apply when player fleet interacts with this station"]?.value as Boolean,
            dialog.fieldStates["Apply when the player fleet leaves this station"]?.value as Boolean,
            itemAutoManages
        )
    }

    override fun advance(amount: Float) {
        val stackCount = cargoRows.size

        for (i in 0 until stackCount) {
            val amountField = cargoRows[i].amountField
            val percentField = cargoRows[i].percentField
            val takeButton = cargoRows[i].takeButton
            val putButton = cargoRows[i].putButton
            val quickStackButton = cargoRows[i].quickStackButton

            // -- Amount field changed
            if (amountField.text != cargoRows[i].prevAmountText) {
                val currentAmount = amountField.text.filter { it in '0'..'9' }
                if (amountField.text != currentAmount) {
                    amountField.text = currentAmount
                }
                cargoRows[i].prevAmountText = currentAmount

                if (currentAmount.isNotEmpty() && percentField.text.isNotEmpty()) {
                    percentField.text = ""
                    cargoRows[i].prevPercentText = ""
                }
            }

            // -- Percent field changed
            val rawPercent = percentField.text

            if (rawPercent != cargoRows[i].prevPercentText) {
                val cleaned = buildString {
                    var dotSeen = false
                    for (c in rawPercent) {
                        when {
                            c in '0'..'9' -> append(c)
                            c == '.' && !dotSeen -> {
                                append(c)
                                dotSeen = true
                            }
                        }
                    }
                }

                val parsed = cleaned.toFloatOrNull()
                val clamped = when {
                    parsed == null -> ""
                    parsed in 0f..1f -> cleaned
                    else -> "" // invalid float outside 0..1
                }

                if (clamped != rawPercent) {
                    percentField.text = clamped
                }

                cargoRows[i].prevPercentText = clamped

                if (clamped.isNotEmpty() && amountField.text.isNotEmpty()) {
                    amountField.text = ""
                    cargoRows[i].prevAmountText = ""
                }
            }

            // -- Take toggled
            val takeState = takeButton.isChecked
            if (takeState != cargoRows[i].prevTakeState) {
                cargoRows[i].prevTakeState = takeState
                if (takeState && quickStackButton.isChecked) {
                    quickStackButton.isChecked = false
                    cargoRows[i].prevQuickStackState = false
                }
            }

            // -- Put toggled
            val putState = putButton.isChecked
            if (putState != cargoRows[i].prevPutState) {
                cargoRows[i].prevPutState = putState
                if (putState && quickStackButton.isChecked) {
                    quickStackButton.isChecked = false
                    cargoRows[i].prevQuickStackState = false
                }
            }


            // -- Quick Stack toggled
            val quickStackState = quickStackButton.isChecked
            if (quickStackState != cargoRows[i].prevQuickStackState) {
                cargoRows[i].prevQuickStackState = quickStackState
                if (quickStackState) {
                    if (takeButton.isChecked) takeButton.isChecked = false
                    cargoRows[i].prevTakeState = false
                    if (putButton.isChecked) putButton.isChecked = false
                    cargoRows[i].prevPutState = false
                    amountField.text = ""
                    cargoRows[i].prevAmountText = ""
                    percentField.text = ""
                    cargoRows[i].prevPercentText = ""
                }
            }

        }
    }

    override fun processInput(events: MutableList<InputEventAPI>?) {}
    override fun buttonPressed(buttonId: Any?) {}

    val buttonHeight = 28f

    init {
        market = selectedSubmarket.market

        val cargoAutoManage = loadCargoAutoManage(selectedSubmarket)
            ?: CargoAutoManage()

        dialog = PopUpUIDialog(selectedSubmarket.name.replace("\n", " "), addCloseButton = true)

        dialog.addButton("Reset Settings", dismissOnClick = false) { _ ->

            val areYouSureDialog = PopUpUIDialog("Are you sure?", addConfirmButton = true, addCancelButton = true)
            areYouSureDialog.cancelButtonName = "No"
            areYouSureDialog.confirmButtonName = "Yes"
            areYouSureDialog.confirmAndCancelAlignment = Alignment.MID

            areYouSureDialog.onConfirm { _ ->
                dialog.forceDismissNoExit()

                unsetCargoAutoManage(selectedSubmarket)

                Dialogs.openSubmarketCargoAutoManagerDialog(selectedSubmarket, instantUp = true)
            }

            DialogUtil.initPopUpUI(areYouSureDialog, 380f, 80f)
        }

        dialog.addToggle(
            "Apply when player fleet interacts with this station", default = cargoAutoManage.applyOnInteraction
        )
        dialog.addToggle(
            "Apply when the player fleet leaves this station", default = cargoAutoManage.applyOnLeave
        )

        dialog.addPadding(dialog.buttonHeight)

        dialog.onExit { fields ->
            val cargoAutoManage = createCargoAutoManage()

            if (cargoAutoManage.isDefault()) {//If the cargo is default
                unsetCargoAutoManage(selectedSubmarket)
            } else {
                saveCargoAutoManage(selectedSubmarket, cargoAutoManage)
            }
        }


        val headerHeight = 24f
        val rowHeight = 48f
        val columnWidths = listOf(250f, 100f, 100f, 80f, 80f, 120f)
        val spacing = 10f
        //val entireRowHeight = rowHeight + spacing

        panel = Global.getSettings().createCustom(width - dialog.x * 2, height - (dialog.y * 2) - 100f, this)

        // Create main UI container
        scrollerTooltip = panel.createUIElement(panel.width, panel.height, true)

        // Header row
        val headers = listOf("Item", "Amount", "Percent", "Take", "Put", "Quick Stack")
        var xPos = 0f
        headers.forEachIndexed { index, text ->
            val colWidth = columnWidths[index]
            val label = scrollerTooltip.addSectionHeading(text, Misc.getTextColor(), Misc.getDarkPlayerColor().darker().darker(), Alignment.MID, colWidth - spacing, 0f)
            label.position.inTL(xPos, 0f)
            xPos += colWidth + spacing
        }

        // Item rows
        var yOffset = headerHeight + spacing

        if (cargoAutoManage.isDefault()) {
            val commodities = arrayOf(
                Global.getSettings().getCommoditySpec("supplies"),
                Global.getSettings().getCommoditySpec("crew"),
                Global.getSettings().getCommoditySpec("fuel")
            )

            commodities.forEach { commodity ->
                yOffset = addStack(commodity.name, commodity.iconName, CargoAPI.CargoItemType.RESOURCES, commodity.id, "0", "", false, false, false, scrollerTooltip, rowHeight, yOffset, columnWidths, spacing)
            }
            yOffset = addStack(
                "Weapons and Wings", defaultIcon,
                CargoAPI.CargoItemType.NULL, "weapon_and_wings",
                "0", "", false, false, false, scrollerTooltip, rowHeight, yOffset, columnWidths, spacing
            )
            yOffset = addStack(
                "Blueprints and ModSpecs", defaultIcon,
                CargoAPI.CargoItemType.NULL, "blueprints_and_modspecs",
                "0", "", false, false, false, scrollerTooltip, rowHeight, yOffset, columnWidths, spacing
            )
        } else {
            cargoAutoManage.autoManageItems.forEach { item ->
                yOffset = addStack(
                    item.displayName, item.icon, item.type, item.data, item.amount?.toString()
                        ?: "", item.percent?.toString()
                        ?: "", item.take, item.put, item.quickStack, scrollerTooltip, rowHeight, yOffset, columnWidths, spacing
                )
            }
        }

        val addCustom = scrollerTooltip.addAreaCheckbox("Add Custom", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), columnWidths[0], rowHeight, 0f)
        addCustom.position.inTL(0f, yOffset + (rowHeight - addCustom.position.height) / 2f)
        addCustom.onClick {
            dialog.forceDismiss()

            val cargoItemSelector = CargoItemSelector(market, selectedSubmarket)

            val panelAPI = Global.getSettings().createCustom(320f, 20f, cargoItemSelector)
            cargoItemSelector.init(
                panelAPI,
                Global.getSettings().mouseX.toFloat(),
                Global.getSettings().mouseY.toFloat(),
                isDialog = false
            )
            panelAPI.addPara("Click a cargo item to select it. Right click to cancel.")
            cargoItemSelector.quitWithEscKey = true

            cargoItemSelector.onExit {
                Dialogs.openSubmarketCargoAutoManagerDialog(selectedSubmarket)
            }
        }

        scrollerTooltip.heightSoFar = -addCustom.yAlignOffset + addCustom.height

        panel.addUIElement(scrollerTooltip).inTL(0f, 0f)




        dialog.addCustom(panel)

        DialogUtil.initPopUpUI(dialog, width, height)
        if (instantUp)
            dialog.setMaxSize()
    }

    private fun addStack(
        displayName: String,
        iconName: String,
        type: CargoAPI.CargoItemType,
        data: Any?,
        defaultAmount: String = "",
        defaultPercent: String = "",
        defaultTake: Boolean = false,
        defaultPut: Boolean = false,
        defaultQuickStack: Boolean = false,
        ui: TooltipMakerAPI,
        rowHeight: Float,
        yOffset: Float,
        columnWidths: List<Float>,
        spacing: Float
    ): Float {
        var xPos1 = 0f
        var yOffset1 = yOffset

        // 1. Image + label
        val imageTooltip = ui.beginImageWithText(iconName, rowHeight)
        val imageLabel = imageTooltip.addPara(displayName, 0f)
        imageLabel.color = Misc.getButtonTextColor()
        val newText = ui.addImageWithText(0f)
        newText.position.inTL(xPos1, yOffset1)

        xPos1 += columnWidths[0] + spacing

        val tooltipLocation = TooltipMakerAPI.TooltipLocation.BELOW
        val tooltipWidth = 400f

        // 2. Amount field
        val amountField = ui.addTextField(columnWidths[1], 0f).apply {
            isUndoOnEscape = false
            text = defaultAmount
            addTooltip(tooltipLocation, tooltipWidth) {
                it.addPara("The amount of this item to keep", 0f)
            }
            position.inTL(xPos1, yOffset1 + (rowHeight - position.height) / 2f)
        }
        xPos1 += columnWidths[1] + spacing

        // 3. Percent field
        val percentField = ui.addTextField(columnWidths[2], 0f).apply {
            isUndoOnEscape = false
            text = defaultPercent
            addTooltip(tooltipLocation, tooltipWidth) {
                it.addPara("The percentage of the fleet's capacity of this item to keep", 0f)
            }
            position.inTL(xPos1, yOffset1 + (rowHeight - position.height) / 2f)
        }
        xPos1 += columnWidths[2] + spacing

        // 4. Take button
        val takeBtn = ui.addAreaCheckbox(
            "Take", null,
            Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
            columnWidths[3], buttonHeight, 0f
        ).apply {
            isChecked = defaultTake
            addTooltip(tooltipLocation, tooltipWidth) {
                it.addPara("If checked, take this item from this submarket if below the specified amount or percentage", 0f)
            }
            position.inTL(xPos1, yOffset1 + (rowHeight - position.height) / 2f)
        }
        xPos1 += columnWidths[3] + spacing

        // 5. Put button
        val putBtn = ui.addAreaCheckbox(
            "Put", null,
            Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
            columnWidths[4], buttonHeight, 0f
        ).apply {
            isChecked = defaultPut
            addTooltip(tooltipLocation, tooltipWidth) {
                it.addPara("If checked, put this item in this submarket if above the specified amount or percentage", 0f)
            }
            position.inTL(xPos1, yOffset1 + (rowHeight - position.height) / 2f)
        }
        xPos1 += columnWidths[4] + spacing

        // 6. Quick Stack button
        val quickBtn = ui.addAreaCheckbox(
            "Quick Stack", null,
            Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
            columnWidths[5], buttonHeight, 0f
        ).apply {
            isChecked = defaultQuickStack
            addTooltip(tooltipLocation, tooltipWidth) {
                it.addPara("If checked, puts all of this item in the submarket if it is in the submarket. If it is not, do nothing.", 0f)
            }
            position.inTL(xPos1, yOffset1 + (rowHeight - position.height) / 2f)
        }
        xPos1 += columnWidths[5] + spacing

        // 7. Delete button
        val delBtn = ui.addButton("Delete", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), columnWidths[4], buttonHeight, 0f).apply {
            addTooltip(tooltipLocation, tooltipWidth) {
                it.addPara("Click to delete this element.", 0f)
            }
            position.inTL(xPos1, yOffset1 + (rowHeight - position.height) / 2f)
        }

        val cargoRow = CargoRow(
            type = type,
            data = data,
            icon = iconName,
            displayName = displayName,
            imageTooltip = imageTooltip,
            iconPanel = newText,
            amountField = amountField,
            percentField = percentField,
            takeButton = takeBtn,
            putButton = putBtn,
            quickStackButton = quickBtn,
            deleteButton = delBtn,
            prevAmountText = amountField.text,
            prevPercentText = percentField.text,
            prevTakeState = takeBtn.isChecked,
            prevPutState = putBtn.isChecked,
            prevQuickStackState = quickBtn.isChecked
        )
        cargoRows += cargoRow

        delBtn.onClick {
            removeCargoRowOf(cargoRow)
        }

        yOffset1 += rowHeight + spacing
        return yOffset1

    }
}

class CargoItemSelector(val market: MarketAPI, val selectedSubmarket: SubmarketAPI) : PopUpUI() {
    override fun advance(amount: Float) {
        val panelWidth = this.panel.position.width
        val panelHeight = this.panel.position.height

        // Center horizontally, position above mouse
        this.panel.position.setXAlignOffset(Global.getSettings().mouseX.toFloat() - panelWidth / 2f)
        this.panel.position.setYAlignOffset(Global.getSettings().mouseY.toFloat() + panelHeight + 20f - Global.getSettings().screenHeight)

        parent?.bringComponentToTop(panel)

        super.advance(amount)
    }


    override fun processInput(events: MutableList<InputEventAPI>) {
        for (event in events) {
            if (event.isConsumed) continue
            if (event.isKeyboardEvent) {
                if (event.eventValue == Keyboard.KEY_ESCAPE) {
                    if (event.isKeyDownEvent) {
                        event.consume()
                    } else if (event.isKeyUpEvent) {
                        forceDismiss()
                        event.consume()
                        break
                    }
                } else {
                    event.consume()
                }
            } else if (event.isMouseDownEvent) {
                if (event.isRMBDownEvent) {
                    forceDismiss()
                    event.consume()
                    break
                } else if (event.isLMBDownEvent) {
                    event.consume()

                    val cargoTab = ReflectionMisc.getCargoTab() ?: continue
                    val dataViewPanels = cargoTab.allChildsWithMethod("isInvalidDropTarget")

                    val allDataChildren = dataViewPanels.mapNotNull { child ->
                        val dataView = (child.invoke("getCargoDataView") as? UIPanelAPI)
                            ?: return@mapNotNull null
                        dataView.getChildrenCopy()
                    }.flatten()

                    allDataChildren.forEach { child ->
                        //if (!FBMisc.isMouseHoveringOverComponent(child)) return@forEach // This applies even when behind another panel

                        val fader = child.invoke("getMouseoverHighlightFader") as? Fader ?: return@forEach
                        if (!fader.isFadingIn && fader.brightness != 1f) return@forEach

                        val stack = child.invoke("getStack") as? CargoItemStack ?: return

                        val cargoAutoManage = loadCargoAutoManage(selectedSubmarket)
                            ?: CargoAutoManage()

                        val iconName: String =
                            if (stack.resourceIfResource != null) {
                                stack.resourceIfResource.iconName
                            } else if (stack.weaponSpecIfWeapon != null) {
                                stack.weaponSpecIfWeapon.turretSpriteName
                            } else if (stack.fighterWingSpecIfWing != null) {
                                stack.fighterWingSpecIfWing.variant.hullSpec.spriteName
                            } else if (stack.hullModSpecIfHullMod != null) {
                                stack.hullModSpecIfHullMod.spriteName
                            } else if (stack.specialItemSpecIfSpecial != null) {
                                stack.specialItemSpecIfSpecial.iconName
                            } else if (stack.specialDataIfSpecial != null)
                                defaultIcon
                            else {
                                defaultIcon
                            }

                        cargoAutoManage.autoManageItems.add(
                            ItemAutoManage(
                                stack.type,
                                stack.data,
                                iconName,
                                stack.displayName,
                                0,
                                null,
                                false,
                                false,
                                false
                            )
                        )

                        saveCargoAutoManage(selectedSubmarket, cargoAutoManage)

                        forceDismiss()
                        return
                    }
                }
            }
        }
    }
}