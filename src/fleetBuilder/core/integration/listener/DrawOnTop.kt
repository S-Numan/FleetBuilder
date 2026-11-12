package fleetBuilder.core.integration.listener

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.MessageDisplayAPI
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.core.util.misc.DrawMessageOnTop
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.TimeKeeper


internal class DrawOnTop : EveryFrameScript, BaseEveryFrameCombatPlugin() {
    companion object {
        var curState = GameState.TITLE
            private set

        private var justLoadedGame = 0
        fun onGameLoad() {
            justLoadedGame = 2 // Delay a tick for the screen panel+
            DrawMessageOnTop.onGameLoad()
        }

        fun renderStatic() {
            DrawMessageOnTop.renderStatic()
        }

        fun advanceStatic(amount: Float) {
            if (curState == GameState.CAMPAIGN)
                DrawMessageOnTop.advanceAmount(TimeKeeper.campaignDelta(amount))
            else
                DrawMessageOnTop.advanceAmount(TimeKeeper.combatDelta(amount))
        }
    }

    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true
    override fun advance(amount: Float) {
        stateChangeChecker()
        if (curState != GameState.CAMPAIGN) return

        if (justLoadedGame > 0) {
            justLoadedGame--
            if (justLoadedGame == 0) {
                val screenPanel = ReflectionMisc.getScreenPanel()
                if (screenPanel != null && screenPanel.getChildrenCopy().none { CampaignRenderer::class.java.isInstance((it as? CustomPanelAPI)?.plugin) })
                    CampaignRenderer()
            }
        }

        advanceStatic(amount)
    }

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        stateChangeChecker()
        if (curState != GameState.TITLE && curState != GameState.COMBAT) return

        advanceStatic(amount)
    }

    override fun renderInUICoords(viewport: ViewportAPI?) {
        renderStatic()
    }

    fun stateChangeChecker() {
        val state = Global.getCurrentState()

        if (state != curState) {
            curState = state

            val screenPanel = ReflectionMisc.getScreenPanel()
            if (screenPanel != null && curState == GameState.CAMPAIGN && screenPanel.getChildrenCopy().none { CampaignRenderer::class.java.isInstance((it as? CustomPanelAPI)?.plugin) })
                CampaignRenderer()
        }
    }
}

private class CampaignRenderer : BaseCustomUIPanelPlugin() {
    private val screenPanel = ReflectionMisc.getScreenPanel()
    var panel: CustomPanelAPI

    init {
        val settings = Global.getSettings()
        panel = settings.createCustom(settings.screenWidth, settings.screenHeight, this)

        screenPanel?.addComponent(panel)?.inTL(0f, 0f)
    }

    override fun render(alphaMult: Float) {
        DrawOnTop.renderStatic()
    }

    override fun advance(amount: Float) {
        if (screenPanel == null) return

        // Move above other components if not top, but try to avoid fighting for control by excluding CustomPanelAPIs and MessageDisplayAPIs

        @Suppress("UNCHECKED_CAST")
        val children = screenPanel.getChildrenCopy()

        val lastValid = children.lastOrNull() { component ->
            when (component) {
                is CustomPanelAPI -> false
                is MessageDisplayAPI -> false
                else -> true
            }
        }

        if (lastValid !== panel) {
            screenPanel.bringComponentToTop(panel)
        }
    }
}