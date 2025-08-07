package fleetBuilder.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import starficz.addTooltip

//Global.getSector().currentlyOpenMarket.memoryWithoutUpdate.set("FBC_SubmarktID")//Fleet Builder Cargo
//TODO set a data class which stores the contents of this panel

class CargoSpreadsheetPanel(
    private val cargo: CargoAPI,
    private val width: Float,
    private val height: Float
) : CustomUIPanelPlugin {

    private val panel: CustomPanelAPI = Global.getSettings().createCustom(width, height, this)
    fun getPanel() = panel

    override fun positionChanged(position: PositionAPI?) {}
    override fun renderBelow(alphaMult: Float) {}
    override fun render(alphaMult: Float) {}
    override fun advance(amount: Float) {}
    override fun processInput(events: MutableList<InputEventAPI>?) {}
    override fun buttonPressed(buttonId: Any?) {}

    val buttonHeight = 20f

    init {

        val headerHeight = 24f
        val rowHeight = 48f
        val columnWidths = listOf(250f, 100f, 100f, 80f, 80f, 120f)
        val spacing = 10f
        val entireRowHeight = rowHeight + spacing

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

        val defaultIcon = Global.getSector().playerFaction.crest
        val comSupplies = Global.getSettings().getCommoditySpec("supplies")
        val comCrew = Global.getSettings().getCommoditySpec("crew")
        val comFuel = Global.getSettings().getCommoditySpec("fuel")

        yOffset = addStack(comSupplies.name, comSupplies.iconName, ui, rowHeight, yOffset, columnWidths, spacing)
        yOffset = addStack(comCrew.name, comCrew.iconName, ui, rowHeight, yOffset, columnWidths, spacing)
        yOffset = addStack(comFuel.name, comFuel.iconName, ui, rowHeight, yOffset, columnWidths, spacing)
        yOffset = addStack("Weapons", defaultIcon, ui, rowHeight, yOffset, columnWidths, spacing)

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
        ui: TooltipMakerAPI,
        rowHeight: Float,
        yOffset: Float,
        columnWidths: List<Float>,
        spacing: Float
    ): Float {
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
        amountField.text = ""
        amountField.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("The amount of this item to keep", 0f)
        }
        amountField.position.inTL(xPos1, yOffset1 + (rowHeight - amountField.position.height) / 2f)
        xPos1 += columnWidths[1] + spacing

        // 3. Percent text field
        val percentField = ui.addTextField(columnWidths[2], 0f)
        percentField.text = ""
        percentField.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("The percentage of the fleet's capacity of this item to keep", 0f)
        }
        percentField.position.inTL(xPos1, yOffset1 + (rowHeight - percentField.position.height) / 2f)
        xPos1 += columnWidths[2] + spacing

        // 4. Take toggle button
        val takeBtn = ui.addAreaCheckbox("Take", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), columnWidths[3], buttonHeight, 0f)
        takeBtn.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("If checked, take this item from this submarket if below the specified amount or percentage", 0f)
        }
        takeBtn.position.inTL(xPos1, yOffset1 + (rowHeight - takeBtn.position.height) / 2f)
        xPos1 += columnWidths[3] + spacing

        // 5. Put toggle button
        val putBtn = ui.addAreaCheckbox("Put", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), columnWidths[4], buttonHeight, 0f)
        putBtn.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("If checked, put this item in this submarket if above the specified amount or percentage", 0f)
        }
        putBtn.position.inTL(xPos1, yOffset1 + (rowHeight - putBtn.position.height) / 2f)
        xPos1 += columnWidths[4] + spacing

        // 6. Quick Stack toggle button
        val quickBtn = ui.addAreaCheckbox("Quick Stack", null, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), columnWidths[5], buttonHeight, 0f)
        quickBtn.addTooltip(tooltipLocation, tooltipWidth) { tooltip ->
            tooltip.addPara("If checked, puts all of this item in the submarket if it is in the submarket. If it is not, do nothing.", 0f)
        }
        quickBtn.position.inTL(xPos1, yOffset1 + (rowHeight - quickBtn.position.height) / 2f)

        yOffset1 += rowHeight + spacing
        return yOffset1
    }
}