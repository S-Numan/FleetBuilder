package fleetBuilder.features.filters.filterPanels

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShieldAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.FBTxt
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.allDMods
import fleetBuilder.util.api.kotlin.allSMods
import fleetBuilder.util.api.kotlin.getShipNameWithoutPrefix
import fleetBuilder.util.api.kotlin.safeInvoke
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

//Credit to Genrir's Fleet Storage Filter for being a starting point for this code

class FleetFilterPanel(
    height: Float,
    private val fleetSidePanel: UIPanelAPI
) : BaseFilterPanel(
    width = fleetSidePanel.getChildrenCopy().minByOrNull { it.x }?.width ?: 32f,
    height = height,
    parent = fleetSidePanel,
    defaultText = FBTxt.txt("ctrl_f_to_search")
) {

    private val yPad = 5f
    private val xOffset = -10f

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

        mainPanel.opacity = 0f
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
                        "modules (If a ship has modules)\n" +
                        "marines / transport\n" +
                        "fuel / tanker\n" +
                        "crew / liner\n" +
                        "cargo / freighter", 0f
            )
        }
    }

    val lowestChild = fleetSidePanel.findChildWithMethod("createStoryPointsLabel")
    val wideChild = fleetSidePanel.getChildrenCopy().minByOrNull { it.x }
    override fun advance(amount: Float) {

        try {
            if (lowestChild != null)
                mainPanel.position.belowLeft(lowestChild, yPad)

            if (wideChild != null)
                mainPanel.position.setXAlignOffset(xOffset)
        } catch (e: Exception) {
            DisplayMessage.showError("Failed to position filter panel", e)
        }

        mainPanel.opacity = 1f


        if (textField.hasFocus() || !textField.enabled) {
            if (textField.text != defaultText) { // On focus
                val fleetPanel = ReflectionMisc.getFleetPanel() ?: return
                //Unfocus textField if mouse is inside fleetPanel
                if (Global.getSettings().mouseX > fleetPanel.x) {
                    if (textField.enabled && textField.hasFocus()) {
                        //ReflectionMisc.updateFleetPanelContents()
                        textField.safeInvoke("releaseFocus", null)
                        textField.enabled = false
                    }
                } else if (!textField.hasFocus()) {
                    textField.enabled = true
                    val text = textField.text
                    textField.text = defaultText
                    textField.grabFocus(false)
                    textField.text = text
                }
            }
        }

        super.advance(amount)
    }

    override fun processInput(events: List<InputEventAPI>) {
        super.processInput(events)

        if (textField.enabled)
            return
        if (Mouse.isButtonDown(0)) {
            textField.enabled = true
            return
        }
        events.forEach { event ->
            if (event.isConsumed) return@forEach
            if (event.isKeyDownEvent && event.eventValue == Keyboard.KEY_ESCAPE) {
                textField.enabled = true
                resetText()
                ReflectionMisc.updateFleetPanelContents()
                event.consume()
                return
            }
        }
        textField.enabled = true
        textField.grabFocus(false)
        textField.safeInvoke("processInput", events)
        textField.safeInvoke("releaseFocus", null)
        textField.enabled = false
    }

    override fun handleFocus() {
        if (!textField.enabled)
            textField.grabFocus(false)
        super.handleFocus()
        if (!textField.enabled)
            textField.safeInvoke("releaseFocus", null)
    }

    override fun onFilterChanged(text: String) {
        ReflectionMisc.updateFleetPanelContents()
    }

    override fun onMiddleMouseReset() {
        super.onMiddleMouseReset()
        ReflectionMisc.updateFleetPanelContents()
        textField.enabled = true
    }

    private fun filterFleetGrid() {
        val fleetPanel = ReflectionMisc.getFleetPanel() ?: return
        if (textField.text.isBlank() || textField.text == defaultText) return

        val fleetGrid = fleetPanel.findChildWithMethod("removeItem") ?: return

        @Suppress("UNCHECKED_CAST")
        val items = fleetGrid.safeInvoke("getItems") as? List<UIPanelAPI?> ?: return

        val descriptions = parseSearchTokens(textField.text)

        descriptions.forEach { desc ->
            val toRemove = mutableListOf<UIPanelAPI>()

            items.forEach { item ->
                val member = item?.safeInvoke("getMember") as? FleetMemberAPI ?: return@forEach

                if (desc.startsWith("-")) {
                    if (member.matchesDescription(desc.removePrefix("-")))
                        toRemove.add(item)
                } else if (!member.matchesDescription(desc)) {
                    toRemove.add(item)
                }
            }

            //toRemove.forEach { fleetPanel.safeInvoke("removeListItemFor", it, true) }
            toRemove.forEach { fleetGrid.safeInvoke("removeItem", it) }
        }

        fleetGrid.safeInvoke("collapseEmptySlots")
    }

    private fun FleetMemberAPI.matchesDescription(desc: String): Boolean {
        return when {
            // Names
            //hullSpec.getCompatibleDLessHull(true).lowercase().contains(desc) -> true
            hullSpec.hullName.lowercase().contains(desc) -> true
            getShipNameWithoutPrefix().lowercase().startsWith(desc) -> true
            hullSpec.manufacturer.lowercase().contains(desc) -> true

            // Types
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

            // Ship systems
            Global.getSettings().allShipSystemSpecs.find { it.id == hullSpec.shipSystemId }?.name?.lowercase()?.contains(desc) == true -> true

            hullSpec.shipDefenseId.isNotEmpty() && hullSpec.shipDefenseId != "phasecloak" && Global.getSettings().allShipSystemSpecs.find { it.id == hullSpec.shipDefenseId }?.name?.lowercase()?.contains(desc) == true -> true

            variant.stationModules.isNotEmpty() && ("modules".startsWith(desc)) -> true

            else -> false
        }
    }

}