package fleetBuilder.temporary

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.ui.autofit.AutofitSelector

class FleetGrid(
    private val members: List<FleetMemberAPI>,
    private val rows: Int,
    private val cols: Int,
    private val iconSize: Float
) : CustomUIPanelPlugin {

    private lateinit var panel: CustomPanelAPI

    fun getPanel(): CustomPanelAPI = panel

    fun init(panel: CustomPanelAPI) {
        this.panel = panel

        val spacingX = iconSize + 5f
        val spacingY = iconSize + 2f

        for ((index, member) in members.withIndex()) {
            if (index >= rows * cols) break

            val row = index / cols
            val col = index % cols

            val x = col * spacingX
            val y = panel.position.height - (row + 1) * spacingY

            val clonedVariant = member.variant.clone() as HullVariantSpec

            val shipPreview = AutofitSelector.createShipPreview(
                clonedVariant,
                iconSize,
                iconSize,
                setSchematicMode = true,
                scaleDownSmallerShips = true
            )


            // Set position relative to top-left (panel is top-left anchored)
            shipPreview.position.inTL(x, y)
            panel.addComponent(shipPreview)
        }
    }


    override fun positionChanged(position: PositionAPI) {}

    override fun renderBelow(alphaMult: Float) {}

    override fun render(alphaMult: Float) {}

    override fun advance(amount: Float) {}

    override fun processInput(events: MutableList<InputEventAPI>?) {}

    override fun buttonPressed(buttonId: Any?) {}
}