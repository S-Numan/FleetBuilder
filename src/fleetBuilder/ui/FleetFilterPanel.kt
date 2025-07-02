package fleetBuilder.ui

import MagicLib.width
import MagicLib.x
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.*
import fleetBuilder.util.MISC
import fleetBuilder.util.findChildWithMethod
import fleetBuilder.util.getChildrenCopy
import fleetBuilder.util.getShipNameWithoutPrefix
import starficz.ReflectionUtils.invoke

//Credit to Genrir's Fleet Storage Filter for being a starting point for this code

class FleetFilterPanel(
    height: Float,
    private val fleetPanel: UIPanelAPI,
    private val fleetSidePanel: UIPanelAPI
) : CustomUIPanelPlugin {

    private val defaultText = "Search for a ship"
    private var mainPanel: CustomPanelAPI
    private var textField: TextFieldAPI
    private var prevString: String = defaultText

    private val yPad = 5f
    private val xOffset = -10f

    private var initTick = false

    init {

        val width = fleetSidePanel.getChildrenCopy().minByOrNull { it.x }?.width ?: 32f

        mainPanel = Global.getSettings().createCustom(width, height, this)
        mainPanel.opacity = 0f

        val tooltip = mainPanel.createUIElement(width, height, false)
        textField = tooltip.addTextField(width, height, Fonts.DEFAULT_SMALL, 0f)
        textField.text = defaultText

        mainPanel.addUIElement(tooltip).inTL(0f, 0f)
        fleetSidePanel.addComponent(mainPanel)//.inBR(xPad, yPad)
    }

    override fun advance(amount: Float) {
        if (!initTick) {
            initTick = true

            //Reposition again in case other mods mess with the UI
            val lowestChild = fleetSidePanel.findChildWithMethod("createStoryPointsLabel")
            val wideChild = fleetSidePanel.getChildrenCopy().minByOrNull { it.x }
            if (lowestChild != null)
                mainPanel.position.belowLeft(lowestChild, yPad)
            if (wideChild != null)
                mainPanel.position.setXAlignOffset(xOffset)
            mainPanel.opacity = 1f
        }

        if (textField.hasFocus()) {
            if (textField.text == defaultText) {
                textField.text = ""
                prevString = ""
            } else {
                //Unfocus textField if mouse is inside fleetPanel
                if (Global.getSettings().mouseX > fleetPanel.x) {
                    textField.invoke("releaseFocus", null)
                }
            }

        }

        if (textField.text == prevString) {
            return
        }

        MISC.updateFleetPanelContents()

        val fleetGrid = fleetPanel.findChildWithMethod("removeItem") ?: return

        @Suppress("UNCHECKED_CAST")
        val items = fleetGrid.invoke("getItems") as? List<UIPanelAPI?> ?: return

        val descriptions = textField.text.lowercase().split(" ").filter { it != "" }

        descriptions.forEach { desc ->
            val itemsToRemove = mutableListOf<UIPanelAPI?>()
            items.forEach { item ->
                val member = item?.invoke("getMember") as? FleetMemberAPI ?: return@forEach
                if (desc.startsWith("-")) {
                    if (member.matchesDescription(desc.removePrefix("-")))
                        itemsToRemove.add(item)
                } else if (!member.matchesDescription(desc))
                    itemsToRemove.add(item)
            }

            itemsToRemove.forEach { fleetGrid.invoke("removeItem", it) }

            fleetGrid.invoke("collapseEmptySlots")
        }

        prevString = textField.text
    }

    private fun FleetMemberAPI.matchesDescription(desc: String): Boolean {
        return when {
            //hullSpec.getCompatibleDLessHullId().lowercase().contains(desc) -> true
            hullSpec.hullName.lowercase().contains(desc) -> true
            getShipNameWithoutPrefix().lowercase().startsWith(desc) -> true
            hullSpec.manufacturer.lowercase().startsWith(desc) -> true
            isCivilian && "civilian".startsWith(desc) -> true
            isCarrier && "carrier".startsWith(desc) -> true
            isPhaseShip && "phase".startsWith(desc) -> true
            isFrigate && "frigate".startsWith(desc) -> true
            isDestroyer && "destroyer".startsWith(desc) -> true
            isCruiser && "cruiser".startsWith(desc) -> true
            isCapital && "capital".startsWith(desc) -> true
            variant.hasHullMod("automated") && "automated".startsWith(desc) -> true

            else -> false
        }
    }

    override fun positionChanged(position: PositionAPI) = Unit

    override fun renderBelow(alphaMult: Float) = Unit

    override fun render(alphaMult: Float) = Unit

    override fun processInput(events: List<InputEventAPI?>?) = Unit

    override fun buttonPressed(buttonId: Any?) {

    }

}