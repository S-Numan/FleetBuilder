package fleetBuilder.ui.autofit

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.magiclib.kotlin.setAlpha
import starficz.*
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.invoke
import java.awt.Color

/**
 * Original author
 * @author Starficz
 */
internal object AutofitPanelCreator {
    private val AUTOFIT_BUTTON_COLOR = Color(240, 160, 0, 130)
    private val AUTOFIT_BUTTON_TEXT_COLOR = AUTOFIT_BUTTON_COLOR.brighter().setAlpha(255)
    fun toggleAutofitButton(refitTab: UIPanelAPI, inCampaign: Boolean) {
        val refitPanel = refitTab.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI ?: return
        val statsAndHullmodsPanel = refitPanel.findChildWithMethod("getColorFor") as? UIPanelAPI ?: return
        val hullmodsPanel =
            statsAndHullmodsPanel.findChildWithMethod("removeNotApplicableMods") as? UIPanelAPI ?: return

        /*refitPanel.getChildrenCopy()
        val testVar = refitPanel.findChildWithMethod("getVariantMap") as? UIPanelAPI ?: return
        if(testVar == null) {

        }*/

        val fleetMember = refitPanel.invoke("getMember") as? FleetMemberAPI
        val existingElements = hullmodsPanel.getChildrenCopy()
        val lastElement = existingElements.lastOrNull() ?: return // if children is empty, return

        //val paintjobButton = existingElements.filterIsInstance<ButtonAPI>().firstOrNull { button ->
        //    button.customData is String && button.customData == "REFIT_BUTTON"
        //}

        val coreUI = refitPanel.invoke("getCoreUI") as? UIPanelAPI ?: return

        val curPaintjobPanel = coreUI.getChildrenCopy().filterIsInstance<CustomPanelAPI>().firstOrNull { panel ->
            panel.plugin != null && panel.plugin is AutofitPanel.AutofitPanelPlugin
        }

        // button should be not exist on modules, ships with a perma paintjob, or ships without any possible paintjobs
        //val buttonShouldNotExist = !MagicPaintjobManager.isEnabled || fleetMember == null ||
        //        MagicPaintjobManager.getCurrentShipPaintjob(fleetMember)?.isPermanent == true ||
        //        MagicPaintjobManager.getPaintjobsForHull(fleetMember.hullSpec).isEmpty()
        //val buttonShouldNotExist = fleetMember == null
        //if(curPaintjobPanel != null && buttonShouldNotExist) coreUI.removeComponent(curPaintjobPanel)
        //if(curPaintjobPanel != null || buttonShouldNotExist) return//Don't open a second panel if one is already present
        if (curPaintjobPanel != null || fleetMember == null) {
            coreUI.removeComponent(curPaintjobPanel)
            Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
            return
        }
        Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)

        /*
                // return if button already exists, or should not exist
                if (paintjobButton != null && buttonShouldNotExist) hullmodsPanel.removeComponent(paintjobButton)
                if (paintjobButton != null || buttonShouldNotExist) return

                // addHullmods button should always exist in hullmodsPanel
                val addButton = existingElements.filter { ReflectionUtils.hasMethodOfName("getText", it) }.find {
                    (ReflectionUtils.invoke("getText", it) as String).contains("Add")
                } ?: return

                // make a new button
                val newPaintjobButton = hullmodsPanel.addButton(
                    "Refit Button",
                    "REFIT_BUTTON",
                    PAINTJOB_BUTTON_TEXT_COLOR,
                    PAINTJOB_BUTTON_COLOR,
                    Alignment.MID,
                    CutStyle.ALL,
                    Font.ORBITRON_20,
                    addButton.width,
                    addButton.height
                )

                newPaintjobButton.position.belowLeft(lastElement, 3f)
                newPaintjobButton.setShortcut(Keyboard.KEY_Z, true)
                newPaintjobButton.onClick {*/
        // width/height calcs here are to match vanilla's hullmod panel sizes when screen size grow/shrink
        //val width = if(inCampaign) (refitTab.width - 343).coerceIn(667f, 700f) else 667f
        //val height = if(inCampaign) (refitTab.height - 12).coerceIn(722f, 800f) else 722f
        val width = (refitTab.width - 343 + 32).coerceIn(667f, 700f + 213f + 32)
        val height = (refitTab.height - 12).coerceIn(722f, 800f + 26f + 16f + 2f)
        val paintjobPanel =
            AutofitPanel.createMagicAutofitPanel(refitTab, refitPanel, coreUI as CoreUIAPI, width, height)

        coreUI.addComponent(paintjobPanel)

        // the numbers might look like magic, but they are actually offsets from where the vanilla refit panel ends up.
        // the other calcs here do ensure correct relative placement
        //val xOffset = if(inCampaign) (refitTab.width - 1037).coerceIn(6f, 213f) else 6f
        val xOffset = 6f
        paintjobPanel.xAlignOffset = refitTab.left - paintjobPanel.left + xOffset
        paintjobPanel.yAlignOffset = refitTab.top - paintjobPanel.top - 6

        // add back button here to make sure its lined up with existing button
        /*val goBackButton = paintjobPanel.addButton(
            "Go Back",
            null,
            PAINTJOB_BUTTON_TEXT_COLOR,
            PAINTJOB_BUTTON_COLOR,
            Alignment.MID,
            CutStyle.ALL,
            Font.ORBITRON_20,
            50f,
            50f
        )

        //goBackButton.xAlignOffset = newPaintjobButton.left - goBackButton.left
        //goBackButton.yAlignOffset = newPaintjobButton.bottom - goBackButton.bottom
        //goBackButton.setShortcut(Keyboard.KEY_Z, true)
        goBackButton.onClick {
            paintjobPanel.parent?.removeComponent(paintjobPanel)
        }*/
        //}
    }
}