package fleetBuilder.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.features.CargoAutoManage
import fleetBuilder.features.ItemAutoManage
import starficz.addTooltip


class CargoAutoManageUIPlugin(
    private val prevCargoAutoManage: CargoAutoManage,
    private val width: Float,
    private val height: Float
) : CustomUIPanelPlugin {

    private val panel: CustomPanelAPI = Global.getSettings().createCustom(width, height, this)
    fun getPanel() = panel

    override fun positionChanged(position: PositionAPI?) {}
    override fun renderBelow(alphaMult: Float) {}
    override fun render(alphaMult: Float) {}

    val idFields = mutableListOf<String>()
    val iconFields = mutableListOf<String>()
    val displayNameFields = mutableListOf<String>()
    val amountFields = mutableListOf<TextFieldAPI>()
    val percentFields = mutableListOf<TextFieldAPI>()
    val takeFields = mutableListOf<ButtonAPI>()
    val putFields = mutableListOf<ButtonAPI>()
    val quickStackFields = mutableListOf<ButtonAPI>()

    val prevAmountTexts = mutableListOf<String>()
    val prevPercentTexts = mutableListOf<String>()
    val prevTakeStates = mutableListOf<Boolean>()
    val prevPutStates = mutableListOf<Boolean>()
    val prevQuickStackStates = mutableListOf<Boolean>()

    fun createCargoAutoManage(applyOnInteraction: Boolean = false, applyOnLeave: Boolean = false): CargoAutoManage {
        val itemAutoManages = mutableListOf<ItemAutoManage>()

        idFields.forEachIndexed { index, id ->
            itemAutoManages.add(
                ItemAutoManage(
                    id,
                    iconFields[index],
                    displayNameFields[index],
                    if (amountFields[index].text.isEmpty()) null else amountFields[index].text.toInt(),
                    if (percentFields[index].text.isEmpty()) null else percentFields[index].text.toDouble(),
                    takeFields[index].isChecked,
                    putFields[index].isChecked,
                    quickStackFields[index].isChecked
                )
            )
        }

        return CargoAutoManage(
            applyOnInteraction,
            applyOnLeave,
            itemAutoManages
        )
    }

    override fun advance(amount: Float) {
        val stackCount = idFields.size

        for (i in 0 until stackCount) {
            val amountField = amountFields[i]
            val percentField = percentFields[i]
            val takeButton = takeFields[i]
            val putButton = putFields[i]
            val quickStackButton = quickStackFields[i]

            // -- Amount field changed
            if (amountField.text != prevAmountTexts[i]) {
                val currentAmount = amountField.text.filter { it in '0'..'9' }
                if (amountField.text != currentAmount) {
                    amountField.text = currentAmount
                }
                prevAmountTexts[i] = currentAmount

                if (currentAmount.isNotEmpty() && percentField.text.isNotEmpty()) {
                    percentField.text = ""
                    prevPercentTexts[i] = ""
                }
            }

            // -- Percent field changed
            val rawPercent = percentField.text

            if (rawPercent != prevPercentTexts[i]) {
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

                prevPercentTexts[i] = clamped

                if (clamped.isNotEmpty() && amountField.text.isNotEmpty()) {
                    amountField.text = ""
                    prevAmountTexts[i] = ""
                }
            }


            // -- Quick Stack toggled
            val quickStackState = quickStackButton.isChecked
            if (quickStackState != prevQuickStackStates[i]) {
                prevQuickStackStates[i] = quickStackState
                if (quickStackState) {
                    if (takeButton.isChecked) takeButton.isChecked = false
                    if (putButton.isChecked) putButton.isChecked = false
                    prevTakeStates[i] = false
                    prevPutStates[i] = false
                }
            }

            // -- Take toggled
            val takeState = takeButton.isChecked
            if (takeState != prevTakeStates[i]) {
                prevTakeStates[i] = takeState
                if (takeState && quickStackButton.isChecked) {
                    quickStackButton.isChecked = false
                    prevQuickStackStates[i] = false
                }
            }

            // -- Put toggled
            val putState = putButton.isChecked
            if (putState != prevPutStates[i]) {
                prevPutStates[i] = putState
                if (putState && quickStackButton.isChecked) {
                    quickStackButton.isChecked = false
                    prevQuickStackStates[i] = false
                }
            }

        }
    }

    override fun processInput(events: MutableList<InputEventAPI>?) {}
    override fun buttonPressed(buttonId: Any?) {}

    val buttonHeight = 20f

    init {

        val headerHeight = 24f
        val rowHeight = 48f
        val columnWidths = listOf(250f, 100f, 100f, 80f, 80f, 120f)
        val spacing = 10f
        //val entireRowHeight = rowHeight + spacing

        // Create main UI container
        val ui = panel.createUIElement(width, height, true)

        // Header row
        val headers = listOf("Item", "Amount", "Percent", "Take", "Put", "Quick Stack")
        var xPos = 0f
        headers.forEachIndexed { index, text ->
            val colWidth = columnWidths[index]
            val label = ui.addSectionHeading(text, Misc.getTextColor(), Misc.getDarkPlayerColor().darker().darker(), Alignment.MID, colWidth - spacing, 0f)
            label.position.inTL(xPos, 0f)
            xPos += colWidth + spacing
        }

        // Item rows
        var yOffset = headerHeight + spacing

        if (prevCargoAutoManage.isDefault()) {
            val commodities = arrayOf(
                Global.getSettings().getCommoditySpec("supplies"),
                Global.getSettings().getCommoditySpec("crew"),
                Global.getSettings().getCommoditySpec("fuel")
            )

            commodities.forEach { commodity ->
                yOffset = addStack(commodity.name, commodity.iconName, commodity.id, "", "", false, false, false, ui, rowHeight, yOffset, columnWidths, spacing)
            }
            yOffset = addStack("Weapons and Wings", Global.getSector().playerFaction.crest, "weapon_and_wings", "", "", false, false, false, ui, rowHeight, yOffset, columnWidths, spacing)
        } else {
            prevCargoAutoManage.autoManageItems.forEach { item ->
                yOffset = addStack(
                    item.displayName, item.icon, item.id, item.amount?.toString() ?: "", item.percent?.toString()
                        ?: "", item.take, item.put, item.quickStack, ui, rowHeight, yOffset, columnWidths, spacing
                )
            }
        }
        /*for (stack in cargo.stacksCopy) {
            if (stack.isNull
                || stack.isFuelStack || stack.isSupplyStack || stack.isCrewStack
            ) continue

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

            yOffset = addStack(stack.displayName, iconName, ui, rowHeight, yOffset, columnWidths, spacing)
        }*/

        val addCustom = ui.addAreaCheckbox("Add Custom", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), columnWidths[0], rowHeight, 0f)
        addCustom.position.inTL(0f, yOffset + (rowHeight - addCustom.position.height) / 2f)

        panel.addUIElement(ui).inTL(0f, 0f)
    }

    private fun addStack(
        displayName: String,
        iconName: String,
        id: String,
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
        idFields.add(id)
        iconFields.add(iconName)
        displayNameFields.add(displayName)

        var xPos1 = 0f
        var yOffset1 = yOffset

        val imageTooltip = ui.beginImageWithText(
            iconName,
            rowHeight
        )
        val imageLabel = imageTooltip.addPara(
            displayName,
            0f,
        )
        imageLabel.color = Misc.getButtonTextColor()

        val newText = ui.addImageWithText(0f)
        newText.position.inTL(xPos1, yOffset1)


        xPos1 += columnWidths[0] + spacing

        val tooltipLocation = TooltipMakerAPI.TooltipLocation.BELOW
        val tooltipWidth = 400f

        // 2. Amount text field
        val amountField = ui.addTextField(columnWidths[1], 0f)
        amountField.text = defaultAmount
        amountFields.add(amountField)
        prevAmountTexts.add(amountField.text)
        amountField.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("The amount of this item to keep", 0f)
        }
        amountField.position.inTL(xPos1, yOffset1 + (rowHeight - amountField.position.height) / 2f)
        xPos1 += columnWidths[1] + spacing

        // 3. Percent text field
        val percentField = ui.addTextField(columnWidths[2], 0f)
        percentField.text = defaultPercent
        percentFields.add(percentField)
        prevPercentTexts.add(percentField.text)
        percentField.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("The percentage of the fleet's capacity of this item to keep", 0f)
        }
        percentField.position.inTL(xPos1, yOffset1 + (rowHeight - percentField.position.height) / 2f)
        xPos1 += columnWidths[2] + spacing

        // 4. Take toggle button
        val takeBtn = ui.addAreaCheckbox("Take", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), columnWidths[3], buttonHeight, 0f)
        takeBtn.isChecked = defaultTake
        takeFields.add(takeBtn)
        prevTakeStates.add(takeBtn.isChecked)
        takeBtn.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("If checked, take this item from this submarket if below the specified amount or percentage", 0f)
        }
        takeBtn.position.inTL(xPos1, yOffset1 + (rowHeight - takeBtn.position.height) / 2f)
        xPos1 += columnWidths[3] + spacing

        // 5. Put toggle button
        val putBtn = ui.addAreaCheckbox("Put", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), columnWidths[4], buttonHeight, 0f)
        putBtn.isChecked = defaultPut
        putFields.add(putBtn)
        prevPutStates.add(putBtn.isChecked)
        putBtn.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("If checked, put this item in this submarket if above the specified amount or percentage", 0f)
        }
        putBtn.position.inTL(xPos1, yOffset1 + (rowHeight - putBtn.position.height) / 2f)
        xPos1 += columnWidths[4] + spacing

        // 6. Quick Stack toggle button
        val quickBtn = ui.addAreaCheckbox("Quick Stack", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), columnWidths[5], buttonHeight, 0f)
        quickBtn.isChecked = defaultQuickStack
        quickStackFields.add(quickBtn)
        prevQuickStackStates.add(quickBtn.isChecked)
        quickBtn.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("If checked, puts all of this item in the submarket if it is in the submarket. If it is not, do nothing.", 0f)
        }
        quickBtn.position.inTL(xPos1, yOffset1 + (rowHeight - quickBtn.position.height) / 2f)

        yOffset1 += rowHeight + spacing
        return yOffset1
    }
}