package fleetBuilder.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.combat.ShieldAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.allDMods
import fleetBuilder.util.allSMods
import fleetBuilder.util.getShipNameWithoutPrefix
import org.lwjgl.input.Mouse
import starficz.*
import starficz.ReflectionUtils.invoke

//Credit to Genrir's Fleet Storage Filter for being a starting point for this code

class FleetFilterPanel(
    height: Float,
    private var fleetSidePanel: UIPanelAPI
) : CustomUIPanelPlugin {

    private val defaultText = "Search for a ship"
    private var mainPanel: CustomPanelAPI

    private var textField: TextFieldAPI
    private lateinit var prevString: String

    fun resetText() {
        textField.text = defaultText
        prevString = defaultText
    }


    private val yPad = 5f
    private val xOffset = -10f

    private var advanceInit = false

    companion object {
        var fleetPanelFilterCallback: (() -> Unit)? = null
        fun removePreviousIfAny() {
            fleetPanelFilterCallback?.let { ReflectionMisc.removePostUpdateFleetPanelCallback(it) }
        }
    }

    init {
        // Remove the previous one, if any
        removePreviousIfAny()
        // Create the new one
        fleetPanelFilterCallback = { filterFleetGrid() }
        // Register the new one
        ReflectionMisc.addPostUpdateFleetPanelCallback(fleetPanelFilterCallback!!)

        val width = fleetSidePanel.getChildrenCopy().minByOrNull { it.x }?.width ?: 32f

        mainPanel = Global.getSettings().createCustom(width, height, this)
        mainPanel.opacity = 0f

        val tooltip = mainPanel.createUIElement(width, height, false)
        textField = tooltip.addTextField(width, height, Fonts.DEFAULT_SMALL, 0f)
        textField.isLimitByStringWidth = false
        textField.maxChars = 255 // Prevent game freeze if pasted element is too large
        resetText()

        mainPanel.addUIElement(tooltip).inTL(0f, 0f)
        fleetSidePanel.addComponent(mainPanel)//.inBR(xPad, yPad)

        mainPanel.addTooltip(TooltipMakerAPI.TooltipLocation.RIGHT, 500f) {
            it.addPara(
                "Valid Inputs:\n" +
                        "\n" +
                        "\n" +
                        "Name of the ship hull (E.G “Hammerhead”)\n" +
                        "Name of the ship (E.G “Apologies To Goddard”)\n" +
                        "Design Type (E.G “Midline”)\n" +
                        "Name of the ship system (E.G “Maneuvering Jets”)\n" +
                        "\n" +
                        "smodded (Has SMods)\n" +
                        "dmodded (Has DMods)\n" +
                        "officer (Has Officer)\n" +
                        "\n" +
                        "combat (If a ship adds to the combat ship deployment point cost)\n" +
                        "civilian\n" +
                        "carrier\n" +
                        "phase\n" +
                        "shields\n" +
                        "frigate\n" +
                        "destroyer\n" +
                        "cruiser\n" +
                        "capital\n" +
                        "automated\n" +
                        "marines / transport\n" +
                        "fuel / tanker\n" +
                        "crew / liner\n" +
                        "cargo / freighter", 0f
            )
        }
    }

    override fun advance(amount: Float) {
        if (!advanceInit) {
            advanceInit = true

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
            if (textField.text == defaultText) {//On focus
                textField.text = ""
                prevString = ""
            } else {
                val fleetPanel = ReflectionMisc.getFleetPanel() ?: return
                //Unfocus textField if mouse is inside fleetPanel
                if (Global.getSettings().mouseX > fleetPanel.x) {
                    textField.invoke("releaseFocus", null)
                }
            }
        } else if (textField.text == "") {//Left focus with no input?
            resetText()
            return
        }

        if (textField.text == prevString) {
            return
        }

        ReflectionMisc.updateFleetPanelContents()

        prevString = textField.text
    }

    private fun filterFleetGrid() {
        val fleetPanel = ReflectionMisc.getFleetPanel() ?: return

        if (textField.text.isNullOrEmpty() || textField.text == defaultText) return

        val fleetGrid = fleetPanel.findChildWithMethod("removeItem") ?: return

        @Suppress("UNCHECKED_CAST")
        val items = fleetGrid.invoke("getItems") as? List<UIPanelAPI?> ?: return

        val regex = Regex("""(?:[^\s"]+|"[^"]*"|'[^']*')+""")//Non-space tokens or quoted strings with "double" or 'single' quotes
        val descriptions = regex.findAll(textField.text.lowercase())
            .map { match ->//Trim quotes, keep the dash
                val token = match.value
                val hasInitialDash = token.startsWith("-") && token.length > 1 && token[1] != '-'
                val core = if (hasInitialDash) token.substring(1) else token
                val trimmed = core.trim('"', '\'', '-') // Trim quotes and dashes
                if (hasInitialDash) "-$trimmed" else trimmed
            }
            .filter { it.isNotBlank() }
            .toList()

        descriptions.forEach { desc ->
            val itemsToRemove = mutableListOf<UIPanelAPI?>()
            items.forEach { item ->
                val member = item?.invoke("getMember") as? FleetMemberAPI ?: return@forEach
                if (desc.startsWith("-")) {
                    if (member.matchesDescription(desc.removePrefix("-")))
                        itemsToRemove.add(item)
                } else if (!member.matchesDescription(desc)) {
                    itemsToRemove.add(item)
                }
            }

            itemsToRemove.forEach { fleetGrid.invoke("removeItem", it) }
        }

        fleetGrid.invoke("collapseEmptySlots")
    }

    private fun FleetMemberAPI.matchesDescription(desc: String): Boolean {
        return when {
            //Names
            //hullSpec.getCompatibleDLessHullId().lowercase().contains(desc) -> true
            hullSpec.hullName.lowercase().contains(desc) -> true
            getShipNameWithoutPrefix().lowercase().startsWith(desc) -> true
            hullSpec.manufacturer.lowercase().startsWith(desc) -> true

            //Types
            !(hullSpec.isCivilianNonCarrier || variant.hasHullMod("civgrade")) && !variant.hasHullMod("militarized_subsystems") && "combat".startsWith(desc) -> true
            hullSpec.isCivilianNonCarrier && !variant.hasHullMod("militarized_subsystems") && "civilian".startsWith(desc) -> true
            isCarrier && "carrier".startsWith(desc) -> true
            isPhaseShip && "phase".startsWith(desc) -> true
            !isPhaseShip && (hullSpec.shieldType == ShieldAPI.ShieldType.OMNI || hullSpec.shieldType == ShieldAPI.ShieldType.FRONT) && !variant.hasHullMod("shield_shunt") && "shields".startsWith(desc) -> true
            isFrigate && "frigate".startsWith(desc) -> true
            isDestroyer && "destroyer".startsWith(desc) -> true
            isCruiser && "cruiser".startsWith(desc) -> true
            isCapital && "capital".startsWith(desc) -> true
            Misc.isAutomated(this) && "automated".startsWith(desc) -> true
            variant.isTransport && ("transport".startsWith(desc) || "marines".startsWith(desc)) -> true
            variant.isTanker && ("tanker".startsWith(desc) || "fuel".startsWith(desc)) -> true
            variant.isLiner && ("liner".startsWith(desc) || "crew".startsWith(desc)) -> true
            variant.isFreighter && ("freighter".startsWith(desc) || "cargo".startsWith(desc)) -> true
            isStation && "station".startsWith(desc) -> true

            //
            variant.allSMods().isNotEmpty() && "smodded".startsWith(desc) -> true
            variant.allDMods().isNotEmpty() && "dmodded".startsWith(desc) -> true
            !captain.isDefault && ("officered".startsWith(desc) || "captained".startsWith(desc)) -> true

            //
            Global.getSettings().allShipSystemSpecs.find { it.id == hullSpec.shipSystemId }?.name?.lowercase()?.startsWith(desc) == true -> true
            hullSpec.shipDefenseId.isNotEmpty() && hullSpec.shipDefenseId != "phasecloak" && Global.getSettings().allShipSystemSpecs.find { it.id == hullSpec.shipDefenseId }?.name?.lowercase()?.startsWith(desc) == true -> true
            else -> false
        }
    }

    override fun positionChanged(position: PositionAPI) = Unit

    override fun renderBelow(alphaMult: Float) = Unit

    override fun render(alphaMult: Float) = Unit

    override fun processInput(events: List<InputEventAPI>) {
        if (Mouse.isButtonDown(2)) {
            resetText()
            ReflectionMisc.updateFleetPanelContents()
        }
    }

    override fun buttonPressed(buttonId: Any?) {

    }

}