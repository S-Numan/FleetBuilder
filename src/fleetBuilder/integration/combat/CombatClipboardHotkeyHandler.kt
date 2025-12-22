package fleetBuilder.integration.combat

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.SharedUnlockData
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.mission.FleetSide
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.FBTxt
import fleetBuilder.config.ModSettings
import fleetBuilder.config.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.persistence.member.DataMember
import fleetBuilder.persistence.member.DataMember.buildMemberFull
import fleetBuilder.persistence.variant.DataVariant
import fleetBuilder.persistence.variant.DataVariant.buildVariantFull
import fleetBuilder.util.*
import fleetBuilder.util.FBMisc.handleRefitCopy
import fleetBuilder.util.FBMisc.handleRefitPaste
import fleetBuilder.util.ReflectionMisc.getCodexDialog
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.reportMissingElementsIfAny
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal class CombatClipboardHotkeyHandler : EveryFrameCombatPlugin {

    override fun processInputPreCoreControls(
        amount: Float,
        events: List<InputEventAPI>
    ) {
        if (!fleetClipboardHotkeyHandler) return

        for (event in events) {
            if (event.isConsumed) continue
            if (event.eventType == InputEventType.KEY_DOWN) {
                if (event.isCtrlDown) {
                    if (event.eventValue == Keyboard.KEY_C) {
                        try {
                            val codex = getCodexDialog()

                            if (codex != null) {
                                ClipboardMisc.codexEntryToClipboard(codex)
                                event.consume(); continue
                            }
                            if (handleRefitCopy(event.isShiftDown))
                                event.consume()

                        } catch (e: Exception) {
                            DisplayMessage.showError(FBTxt.txt("mod_hotkey_failed", ModSettings.modName), e)
                        }
                    } else if (event.eventValue == Keyboard.KEY_V || event.eventValue == Keyboard.KEY_D) {
                        if (event.isShiftDown && event.eventValue == Keyboard.KEY_D && !DialogUtil.isPopUpUIOpen() && !ReflectionMisc.isCodexOpen()) {
                            Dialogs.createDevModeDialog()
                            event.consume()
                            continue
                        }
                        val engine = Global.getCombatEngine() ?: return
                        if (engine.isSimulation || (Global.getCurrentState() == GameState.COMBAT && ModSettings.cheatsEnabled())) {
                            pasteShipIntoCombat(engine, event)
                            event.consume()
                            continue
                        } else if (event.eventValue == Keyboard.KEY_V) {
                            if (Global.getCurrentState() != GameState.COMBAT && !ReflectionMisc.isCodexOpen() && !DialogUtil.isPopUpUIOpen())
                                if (handleRefitPaste())
                                    event.consume()

                            continue
                        }
                    }
                }
            }
        }
    }

    override fun advance(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {

    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {

    }

    override fun renderInUICoords(viewport: ViewportAPI?) {

    }


    @Deprecated("Deprecated in Java")
    override fun init(engine: CombatEngineAPI?) {

    }

    fun isPositionFree(engine: CombatEngineAPI, x: Float, y: Float, radius: Float): Boolean {
        for (ship in engine.ships) {
            //if (ship.isPhased) continue

            val dx = ship.location.x - x
            val dy = ship.location.y - y
            val distanceSquared = dx * dx + dy * dy
            val minDistance = ship.collisionRadius + radius

            if (distanceSquared < minDistance * minDistance) {
                return false
            }
        }
        return true
    }

    /**
     * Finds a free location near a target point where a ship of the given radius could spawn.
     *
     * It first tries the starting location and then searches in an expanding spiral.
     *
     * @param engine Combat engine
     * @param x Target X
     * @param y Target Y
     * @param radius Collision radius of the ship
     * @param maxAttempts Maximum positions to check
     * @param stepDistance Distance to step out each spiral iteration
     * @return Vector2f of a free location, or null if none found
     */
    fun findFreeSpawnLocation(
        engine: CombatEngineAPI,
        x: Float,
        y: Float,
        radius: Float,
        maxAttempts: Int = 1000,
        stepDistance: Float = 20f
    ): Vector2f? {
        // Try the original point first
        if (isPositionFree(engine, x, y, radius)) return Vector2f(x, y)

        var attempt = 0
        val angleStep = Math.PI / 6.0  // 30 degrees
        var distance = stepDistance

        while (attempt < maxAttempts) {
            val angle = angleStep * attempt
            val nx = x + (cos(angle) * distance).toFloat()
            val ny = y + (sin(angle) * distance).toFloat()

            if (isPositionFree(engine, nx, ny, radius)) return Vector2f(nx, ny)

            // Increase distance slightly every full rotation
            if (attempt % 12 == 0) { // 12 steps ~ 360 degrees
                distance += stepDistance
            }

            attempt++
        }

        // No free spot found
        return null
    }

    fun getDeployedFleetPoints(engine: CombatEngineAPI, side: FleetSide): Float {
        val manager = engine.getFleetManager(side)
        var total = 0f

        for (member in manager.deployedCopy) {
            total += member.fleetPointCost.toFloat()
        }

        return total
    }

    private fun pasteShipIntoCombat(
        engine: CombatEngineAPI,
        event: InputEventAPI
    ) {
        if (ReflectionMisc.isCodexOpen() || engine.combatUI.isShowingCommandUI)
            return

        var element: Any? = null

        if (event.eventValue == Keyboard.KEY_V) {
            val data = ClipboardMisc.extractDataFromClipboard() ?: return
            if (data is DataVariant.ParsedVariantData || data is DataMember.ParsedMemberData) {
                //
            } else {
                DisplayMessage.showMessage(FBTxt.txt("data_valid_but_no_member_variant"), Color.YELLOW)
                return
            }

            element = data
        }

        val missing = MissingElements()
        var member: FleetMemberAPI? = null

        when (element) {
            is DataVariant.ParsedVariantData -> {
                val variant = buildVariantFull(element, missing = missing)
                member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant)
                member.crewComposition.addCrew(member.neededCrew)
                member.repairTracker.cr = 0.7f
            }

            is DataMember.ParsedMemberData -> {
                if (element.variantData != null)
                    member = buildMemberFull(element, missing = missing)
                member!!.crewComposition.addCrew(member.neededCrew)
            }
        }
        if (member == null)
            return

        if (missing.hullIds.isNotEmpty()) {
            DisplayMessage.showError("Could not find HullSpec with ID '${missing.hullIds.first()}'")
            return
        }

        val viewport = engine.viewport ?: return
        val sx = Mouse.getX().toFloat()
        val sy = Mouse.getY().toFloat()
        val worldX = viewport.convertScreenXToWorldX(sx)
        val worldY = viewport.convertScreenYToWorldY(sy)
        var loc = Vector2f(worldX, worldY)

        if (!ModSettings.cheatsEnabled()) {
            if (member.hullSpec.hasTag("codex_unlockable")) {
                if (!SharedUnlockData.get().isPlayerAwareOfShip(member.hullId)) {
                    DisplayMessage.showMessage("Cannot spawn ship of hull '${member.hullSpec.hullName}'. The player is not aware of this hull.", Color.YELLOW)
                    return
                }
            } else if (member.hullSpec.hints.contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX) || member.hullSpec.hasTag(Tags.HIDE_IN_CODEX)
                || member.variant.hints.contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX) || member.variant.hasTag(Tags.HIDE_IN_CODEX)
            ) {
                DisplayMessage.showMessage("Cannot spawn ship of hull '${member.hullSpec.hullName}'. It is hidden in the codex which suggests it cannot be simulated.", Color.YELLOW)
                return
            } else if (getDeployedFleetPoints(engine, FleetSide.ENEMY) + member.deployCost > Global.getSettings().battleSize / 2f * 1.2f) {
                DisplayMessage.showMessage("Cannot spawn ship of hull '${member.hullSpec.hullName}'. Would exceed Fleet Point limit.", Color.YELLOW)
                return
            }
        }
        if (!isPositionFree(engine, loc.x, loc.y, member.hullSpec.collisionRadius / 2f)) {
            loc = findFreeSpawnLocation(engine, loc.x, loc.y, member.hullSpec.collisionRadius) ?: run {
                DisplayMessage.showMessage("Cannot spawn ship of hull '${member.hullSpec.hullName}'. Cannot find free location.", Color.YELLOW)
                return
            }
        }

        reportMissingElementsIfAny(missing)

        fun spawnAt(
            engine: CombatEngineAPI,
            member: FleetMemberAPI,
            side: FleetSide,
            loc: Vector2f
        ) {
            val fm = engine.getFleetManager(side)

            var facing: Float

            // Player ship location
            //val player = engine.playerShip
            //if (player == null) {
            facing = -90f
            /*} else {
                                        val px = player.location.x
                                        val py = player.location.y

                                        // Angle from spawn location → player ship
                                        facing = Vector2f.sub(
                                            Vector2f(px, py),
                                            loc,
                                            null
                                        ).let { vec ->
                                            Math.toDegrees(atan2(vec.y.toDouble(), vec.x.toDouble())).toFloat()
                                        }
                                    }*/

            // Spawn with that facing direction
            member.owner = side.ordinal
            val cr = member.repairTracker.cr

            val suppress: Boolean = engine.getFleetManager(side).isSuppressDeploymentMessages
            engine.getFleetManager(side).isSuppressDeploymentMessages = true

            val ship = fm.spawnFleetMember(member, loc, facing, 0f)

            engine.getFleetManager(side).isSuppressDeploymentMessages = suppress

            ship.crAtDeployment = cr
            ship.currentCR = cr
            ship.owner = member.owner
            ship.shipAI.forceCircumstanceEvaluation()

            /*engine.addFloatingText(
                                        loc,
                                        "Spawned: ${member.hullSpec.hullName}",
                                        18f,
                                        Color.CYAN,
                                        ship,
                                        0f,
                                        0f
                                    )*/
        }

        spawnAt(engine, member, FleetSide.ENEMY, loc)
    }
}