package fleetBuilder.features.cargoAutoManage

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.ModSettings.PRIMARYDIR
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.features.cargoAutoManage.CargoAutoManage.loadCargoAutoManageFromMap
import fleetBuilder.features.cargoAutoManage.CargoAutoManage.loadCargoAutoManageFromSubmarket
import fleetBuilder.features.cargoAutoManage.CargoAutoManage.saveCargoAutoManageToMap
import fleetBuilder.features.cargoAutoManage.CargoAutoManage.saveCargoAutoManageToSubmarket
import fleetBuilder.features.cargoAutoManage.CargoAutoManage.unsetCargoAutoManage
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.otherMods.starficz.ReflectionUtils.invoke
import fleetBuilder.ui.customPanel.common.BasePanel
import fleetBuilder.ui.customPanel.common.DialogPanel
import fleetBuilder.ui.customPanel.common.ModalPanel
import fleetBuilder.util.FBMisc.jsonArrayToList
import fleetBuilder.util.FBMisc.listToJsonArray
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.addToggle
import fleetBuilder.util.loadTextureCached
import fleetBuilder.util.safeInvoke
import org.json.JSONArray
import org.json.JSONObject
import org.lwjgl.input.Keyboard

//The implementation of this is extremely scuffed, I am aware.

private val defaultIcon = "graphics/factions/crest_player_flag.png"
private val errorIcon = "graphics/ui/icons/64x_xcircle.png"

internal class CargoAutoManageUIPlugin(
    val selectedSubmarket: SubmarketAPI,
    private val width: Float,
    private val height: Float,
    private val instantUp: Boolean = false
) : CustomUIPanelPlugin {

    private var panel: CustomPanelAPI
    fun getPanel() = panel

    private val dialog: DialogPanel
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

    var interactToggle: ButtonAPI? = null
    var leaveToggle: ButtonAPI? = null

    fun createCargoAutoManage(): CargoAutoManage.AutoManage {
        val itemAutoManages = mutableListOf<CargoAutoManage.ItemAutoManage>()

        cargoRows.forEachIndexed { index, row ->
            itemAutoManages.add(
                CargoAutoManage.ItemAutoManage(
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

        return CargoAutoManage.AutoManage(
            interactToggle?.isChecked ?: false,
            leaveToggle?.isChecked ?: false,
            autoManageItems = itemAutoManages
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
        Global.getSettings().loadTextureCached(errorIcon)

        market = selectedSubmarket.market

        val cargoAutoManage = loadCargoAutoManageFromSubmarket(selectedSubmarket)
            ?: CargoAutoManage.AutoManage()

        //dialog = PopUpUIDialog(selectedSubmarket.name.replace("\n", " "), addCloseButton = true)
        dialog = DialogPanel(selectedSubmarket.name.replace("\n", " "))

        val headerHeight = 24f
        val rowHeight = 48f
        val columnWidths = listOf(250f, 100f, 100f, 80f, 80f, 120f)
        val spacing = 10f
        //val entireRowHeight = rowHeight + spacing

        panel = Global.getSettings().createCustom(width - (dialog.getXTooltipPadding() * 2), height - (dialog.getYTooltipPadding() * 2) - 100f, this)

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

            val panelAPI = cargoItemSelector.init(
                325f,
                20f,
                Global.getSettings().mouseX.toFloat(),
                Global.getSettings().mouseY.toFloat(),
                ReflectionMisc.getScreenPanel()
            )
            panelAPI.addPara("Click a cargo item to select it. Right click to cancel.")

            cargoItemSelector.onExit {
                openSubmarketCargoAutoManagerDialog(selectedSubmarket, instantUp = true)
            }
        }

        scrollerTooltip.heightSoFar = -addCustom.yAlignOffset + addCustom.height

        panel.addUIElement(scrollerTooltip).inTL(dialog.getXTooltipPadding(), 0f)


        dialog.show(width, height) { ui ->
            dialog.addCloseButton()

            val buttonHeight = 24f

            val resetSettingsButton = ui.addButton("Reset Settings", null, ui.width, buttonHeight, 0f)
            resetSettingsButton.onClick {
                val areYouSureDialog = DialogPanel(headerTitle = "Are you sure?")
                areYouSureDialog.show(380f, 80f) { _ ->
                    areYouSureDialog.addActionButtons(confirmText = "Yes", cancelText = "No", alignment = Alignment.MID)
                }
                areYouSureDialog.onConfirm {
                    dialog.forceDismiss(false)

                    unsetCargoAutoManage(selectedSubmarket)

                    openSubmarketCargoAutoManagerDialog(selectedSubmarket, instantUp = true)
                }
            }

            interactToggle = ui.addToggle(
                "Apply when player fleet interacts with this station", isChecked = cargoAutoManage.applyOnInteraction
            )
            leaveToggle = ui.addToggle(
                "Apply when the player fleet leaves this station", isChecked = cargoAutoManage.applyOnLeave
            )

            ui.addSpacer(buttonHeight)

            val heightSoFar = ui.heightSoFar
            val availablePoliciesButton = ui.addButton(
                "Available policies", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.BL_TR, 128f, 32f, 0f
            )
            availablePoliciesButton.position.belowRight(resetSettingsButton, 8f)
            ui.heightSoFar = heightSoFar
            availablePoliciesButton.onClick {
                val autoManagePoliciesDialog = DialogPanel(headerTitle = "Available policies")

                autoManagePoliciesDialog.show(430f, 800f) { externalUI ->
                    try {
                        val innerPanel = Global.getSettings().createCustom(externalUI.width, externalUI.height, null)
                        val innerUI = innerPanel.createUIElement(externalUI.width, externalUI.height - buttonHeight * 2, true)

                        val currentAutoManage = createCargoAutoManage()

                        val cargoAutoManagerPoliciesPath = "${PRIMARYDIR}CargoAutoManagerPolicies"
                        var cargoAutoManagerPoliciesJSON = runCatching {
                            if (Global.getSettings().fileExistsInCommon(cargoAutoManagerPoliciesPath))
                                Global.getSettings().readJSONFromCommon(cargoAutoManagerPoliciesPath, false)
                            else
                                JSONObject()
                        }.getOrNull()

                        if (cargoAutoManagerPoliciesJSON == null || cargoAutoManagerPoliciesJSON.length() == 0) {
                            cargoAutoManagerPoliciesJSON = JSONObject()
                            cargoAutoManagerPoliciesJSON.put("policies", JSONArray())
                        }
                        @Suppress("UNCHECKED_CAST")
                        val cargoAutoManagerPoliciesTemp = jsonArrayToList(cargoAutoManagerPoliciesJSON.getJSONArray("policies")) as List<Map<*, *>>
                        val cargoAutoManagerPolicies = cargoAutoManagerPoliciesTemp.map { loadCargoAutoManageFromMap(it, true) }.sortedBy { it.orderInList }.toMutableList()


                        cargoAutoManagerPolicies.forEach { autoManage ->
                            val name = if (
                                autoManage.copy(name = "", orderInList = 0) == currentAutoManage
                            ) {
                                autoManage.name + " (Current)"
                            } else {
                                autoManage.name
                            }

                            val buttonHeight = 32f

                            val para = innerUI.addPara(name, 8f) as UIComponentAPI
                            para.position.inTL(0f, innerUI.heightSoFar)
                            para.invoke("autoSize")
                            para.setSize(200f, para.height)
                            val applyButton = innerUI.addButton("Apply", null, Misc.getPositiveHighlightColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, 50f, buttonHeight, 0f)
                            applyButton.position.rightOfMid(para, 8f)
                            applyButton.onClick {
                                autoManagePoliciesDialog.forceDismiss()
                                dialog.forceDismiss(false)
                                saveCargoAutoManageToSubmarket(selectedSubmarket, autoManage)
                                openSubmarketCargoAutoManagerDialog(selectedSubmarket, instantUp = true)
                            }
                            applyButton.addTooltip(TooltipMakerAPI.TooltipLocation.RIGHT, 400f) {
                                it.addPara("Replaces your current policy with this one.", 0f)
                            }
                            var overwriteButton: ButtonAPI? = null

                            val removeButton = innerUI.addButton("Delete", null, Misc.getNegativeHighlightColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, 50f, buttonHeight, 0f)
                            removeButton.position.rightOfMid(applyButton, 8f)
                            removeButton.onClick {
                                val areYouSureDialog = DialogPanel(headerTitle = "Are you sure you want to remove this policy?")
                                areYouSureDialog.animation = ModalPanel.PanelAnimation.NONE
                                areYouSureDialog.show(470f, 80f) { _ ->
                                    areYouSureDialog.addActionButtons(confirmText = "Delete", cancelText = "Cancel", alignment = Alignment.MID)
                                }
                                areYouSureDialog.onConfirm {
                                    para.opacity = 0f
                                    applyButton.opacity = 0f
                                    removeButton.opacity = 0f
                                    overwriteButton?.opacity = 0f

                                    cargoAutoManagerPolicies.remove(autoManage)
                                    val mapList = cargoAutoManagerPolicies.map { saveCargoAutoManageToMap(it, true) }
                                    val json = JSONObject().put("policies", listToJsonArray(mapList))
                                    Global.getSettings().writeJSONToCommon(cargoAutoManagerPoliciesPath, json, false)
                                    //autoManagePoliciesDialog.createUI()
                                    DisplayMessage.showMessageCustom("Policy removed!")
                                }
                            }
                            removeButton.addTooltip(TooltipMakerAPI.TooltipLocation.RIGHT, 400f) {
                                it.addPara("Remove this policy from the saved policy list.", 0f)
                            }

                            overwriteButton = innerUI.addButton("Overwrite", null, Misc.getHighlightColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, 70f, buttonHeight, 0f)
                            overwriteButton.position.rightOfMid(removeButton, 8f)
                            overwriteButton.onClick {
                                val areYouSureDialog = DialogPanel(headerTitle = "Are you sure you want to overwrite this policy?")
                                areYouSureDialog.animation = ModalPanel.PanelAnimation.NONE
                                areYouSureDialog.show(500f, 80f) { _ ->
                                    areYouSureDialog.addActionButtons(confirmText = "Overwrite", cancelText = "Cancel", alignment = Alignment.MID)
                                }
                                areYouSureDialog.onConfirm {
                                    cargoAutoManagerPolicies.remove(autoManage)
                                    val currentAutoManageCopy = currentAutoManage.copy(
                                        orderInList = autoManage.orderInList,
                                        name = autoManage.name
                                    )
                                    cargoAutoManagerPolicies.add(currentAutoManageCopy)
                                    val mapList = cargoAutoManagerPolicies.map { saveCargoAutoManageToMap(it, true) }
                                    val json = JSONObject().put("policies", listToJsonArray(mapList))
                                    Global.getSettings().writeJSONToCommon(cargoAutoManagerPoliciesPath, json, false)
                                    autoManagePoliciesDialog.recreateUI()
                                    DisplayMessage.showMessageCustom("Policy overwritten!")
                                }
                            }
                            overwriteButton.addTooltip(TooltipMakerAPI.TooltipLocation.RIGHT, 400f) {
                                it.addPara("Overwrite this policy with your current policy.", 0f)
                            }

                            innerUI.heightSoFar = -applyButton.y
                        }

                        //innerUI.addPara("end point", 0f)

                        val saveCurrentButton = innerPanel.addButton("Save Current", null, Misc.getButtonTextColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, 128f, buttonHeight * 1.5f)
                        saveCurrentButton.position.inTL(0f, externalUI.height - saveCurrentButton.height - 8f)
                        saveCurrentButton.onClick {
                            val namePolicyDialog = DialogPanel(headerTitle = "Name the new policy")
                            var textField: TextFieldAPI? = null
                            namePolicyDialog.show(450f, 113f) { ui ->
                                textField = ui.addTextField(ui.width, 0f)
                                textField.grabFocus()
                                namePolicyDialog.addActionButtons(confirmText = "Save", cancelText = "Cancel", alignment = Alignment.MID)
                            }
                            namePolicyDialog.onConfirm {
                                if (textField == null) return@onConfirm
                                if (textField.text.isBlank()) {
                                    DisplayMessage.showMessageCustom("Policy name cannot be blank!")
                                    return@onConfirm
                                }
                                if (cargoAutoManagerPolicies.any { it.name == textField.text }) {
                                    DisplayMessage.showMessageCustom("Policy name already exists. No duplicates allowed!")
                                    return@onConfirm
                                }

                                val currentAutoManageCopy = currentAutoManage.copy(
                                    orderInList = cargoAutoManagerPolicies.maxByOrNull { it.orderInList }?.orderInList // Place on end
                                        ?: 0,
                                    name = textField.text
                                )
                                cargoAutoManagerPolicies.add(currentAutoManageCopy)

                                val mapList = cargoAutoManagerPolicies.map { saveCargoAutoManageToMap(it, true) }
                                val json = JSONObject().put("policies", listToJsonArray(mapList))
                                Global.getSettings().writeJSONToCommon(cargoAutoManagerPoliciesPath, json, false)
                                autoManagePoliciesDialog.recreateUI()
                                DisplayMessage.showMessageCustom("Policy saved!")
                            }
                        }

                        innerPanel.addUIElement(innerUI).inTL(0f, 0f)
                        externalUI.addCustom(innerPanel, 0f)
                    } catch (e: Exception) {
                        DisplayMessage.showError("Failed to read Cargo Auto Manager Policies", e)
                        autoManagePoliciesDialog.dismiss()
                    }
                }
            }

            ui.addComponent(panel).inTL(0f, ui.heightSoFar)
        }

        dialog.onExit {
            val cargoAutoManage = createCargoAutoManage()

            if (cargoAutoManage.isDefault()) {//If the cargo is default
                unsetCargoAutoManage(selectedSubmarket)
            } else {
                saveCargoAutoManageToSubmarket(selectedSubmarket, cargoAutoManage)
            }
        }

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
        val sprite = Global.getSettings().getSprite(iconName)
        val imageTooltip = if (sprite.textureId != 0)
            ui.beginImageWithText(iconName, rowHeight)
        else
            ui.beginImageWithText(errorIcon, rowHeight)

        val imageLabel = imageTooltip.addPara(displayName, 0f)
        if (sprite.textureId != 0 || data == null)
            imageLabel.color = Misc.getButtonTextColor()
        else
            imageLabel.color = Misc.getNegativeHighlightColor()

        val newText = ui.addImageWithText(0f)
        newText.position.inTL(xPos1, yOffset1)
        if (sprite.textureId == 0) {
            newText.addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 280f) {
                it.addPara("This item is most likely from a removed mod.\n(The sprite was null)", 0f)
            }
            val sprite = newText.getChildrenCopy().getOrNull(0)?.safeInvoke("getSprite")
            sprite?.safeInvoke("setColor", Misc.getNegativeHighlightColor())
        }
        if (data == null) {
            newText.addTooltip(TooltipMakerAPI.TooltipLocation.BELOW, 280f) {
                it.addPara("Data may have been corrupted during load.\n(data was null)", 0f)
            }
        }

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

class CargoItemSelector(val market: MarketAPI, val selectedSubmarket: SubmarketAPI) : BasePanel() {
    override fun advance(amount: Float) {
        val panelWidth = this.panel.position.width
        val panelHeight = this.panel.position.height

        // Center horizontally, position above mouse
        this.panel.position.setXAlignOffset(Global.getSettings().mouseX.toFloat() - panelWidth / 2f)
        this.panel.position.setYAlignOffset(Global.getSettings().mouseY.toFloat() + panelHeight + 20f - Global.getSettings().screenHeight)

        parent.bringComponentToTop(panel)

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

                    val cargoPanel = ReflectionMisc.getCargoPanel() ?: continue
                    ?: return

                    val dataViewPanels = cargoPanel.allChildrenWithMethod("isInvalidDropTarget")

                    val allDataChildren = dataViewPanels.mapNotNull { child ->
                        val dataView = (child.safeInvoke("getCargoDataView") as? UIPanelAPI)
                            ?: return@mapNotNull null
                        dataView.getChildrenCopy()
                    }.flatten()

                    allDataChildren.forEach { child ->
                        //if (!FBMisc.isMouseHoveringOverComponent(child)) return@forEach // This applies even when behind another panel

                        val fader = child.safeInvoke("getMouseoverHighlightFader") as? Fader ?: return@forEach
                        if (!fader.isFadingIn && fader.brightness != 1f) return@forEach

                        val stack = child.safeInvoke("getStack") as? CargoStackAPI ?: return@forEach

                        val cargoAutoManage = loadCargoAutoManageFromSubmarket(selectedSubmarket)
                            ?: CargoAutoManage.AutoManage()

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
                            CargoAutoManage.ItemAutoManage(
                                stack.type,
                                stack.data,
                                iconName,
                                stack.displayName,
                                0,
                                null,
                                false,
                                true,
                                false
                            )
                        )

                        saveCargoAutoManageToSubmarket(selectedSubmarket, cargoAutoManage)

                        forceDismiss()
                        return
                    }
                }
            }
        }
    }
}