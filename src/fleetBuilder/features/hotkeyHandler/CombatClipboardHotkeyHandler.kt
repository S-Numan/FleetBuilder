package fleetBuilder.features.hotkeyHandler

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.SharedUnlockData
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.mission.FleetSide
import fleetBuilder.core.FBSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.ClipboardMisc
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.reportMissingElementsIfAny
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.ui.customPanel.DialogUtils
import fleetBuilder.util.FBTxt
import fleetBuilder.util.ReflectionMisc
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

internal class CombatClipboardHotkeyHandler : BaseEveryFrameCombatPlugin() {

    override fun processInputPreCoreControls(
        amount: Float,
        events: List<InputEventAPI>
    ) {
        if (!FBSettings.fleetClipboardHotkeyHandler) return

        for (event in events) {
            if (event.isConsumed) continue
            if (event.eventType == InputEventType.KEY_DOWN) {
                if (event.isCtrlDown) {
                    if (event.eventValue == Keyboard.KEY_C) {
                        try {
                            val codex = ReflectionMisc.getCodexDialog()

                            if (codex != null) {
                                ClipboardMisc.codexEntryToClipboard(codex)
                                event.consume(); continue
                            }
                            if (ClipboardHotkeyHandlerUtils.handleRefitCopy(event.isShiftDown)) {
                                event.consume(); continue
                            }
                        } catch (e: Exception) {
                            DisplayMessage.showError(FBTxt.txt("mod_hotkey_failed", FBSettings.getModName()), e)
                        }
                    } else if (event.eventValue == Keyboard.KEY_V || event.eventValue == Keyboard.KEY_D) {
                        if (event.isShiftDown && event.eventValue == Keyboard.KEY_D && !DialogUtils.isPopUpPanelOpen() && !ReflectionMisc.isCodexOpen()) {
                            HotkeyHandlerDialogs.createDevModeDialog()
                            event.consume(); continue
                        }
                        val engine = Global.getCombatEngine() ?: return
                        if (engine.isSimulation || (Global.getCurrentState() == GameState.COMBAT && FBSettings.cheatsEnabled())) {
                            pasteShipIntoCombat(engine, event)
                            event.consume(); continue
                        } else if (event.eventValue == Keyboard.KEY_V) {
                            if (Global.getCurrentState() != GameState.COMBAT && !ReflectionMisc.isCodexOpen() && !DialogUtils.isPopUpPanelOpen()) {
                                if (ClipboardHotkeyHandlerUtils.handleRefitPaste()) {
                                    event.consume(); continue
                                }
                            }
                        }
                    }
                }
            }
        }
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

        val missing = MissingElements()

        if (event.eventValue == Keyboard.KEY_V) {
            val data = ClipboardMisc.extractDataFromClipboard(missing) ?: return
            if (data is DataVariant.ParsedVariantData || data is DataMember.ParsedMemberData) {
                //
            } else {
                DisplayMessage.showMessage(FBTxt.txt("data_valid_but_no_member_variant"), Color.YELLOW)
                return
            }

            element = data
        }
        var member: FleetMemberAPI? = null

        when (element) {
            is DataVariant.ParsedVariantData -> {
                val variant = DataVariant.buildVariantFull(element, missing = missing)
                member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant)
                member.crewComposition.addCrew(member.neededCrew)
                member.repairTracker.cr = 0.7f
            }

            is DataMember.ParsedMemberData -> {
                if (element.variantData != null)
                    member = DataMember.buildMemberFull(element, missing = missing)
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

        if (!FBSettings.cheatsEnabled()) {
            if (member.hullSpec.hasTag(Tags.NO_SIM) || member.variant.hasTag(Tags.NO_SIM)) {
                DisplayMessage.showMessage(FBTxt.txt("cannot_spawn_ship_of_hull", member.hullSpec.hullName) + FBTxt.txt("ship_no_sim"), Color.YELLOW)
                return
            }
            if (member.hullSpec.hasTag(Tags.CODEX_UNLOCKABLE)) {
                if (!SharedUnlockData.get().isPlayerAwareOfShip(member.hullId)) {
                    DisplayMessage.showMessage(FBTxt.txt("cannot_spawn_ship_of_hull", member.hullSpec.hullName) + FBTxt.txt("ship_player_not_aware"), Color.YELLOW)
                    return
                }
            } else if (member.hullSpec.hints.contains(ShipTypeHints.HIDE_IN_CODEX) || member.hullSpec.hasTag(Tags.HIDE_IN_CODEX)
                || member.variant.hints.contains(ShipTypeHints.HIDE_IN_CODEX) || member.variant.hasTag(Tags.HIDE_IN_CODEX)
                || member.hullSpec.hasTag(Tags.RESTRICTED)
            ) {
                DisplayMessage.showMessage(FBTxt.txt("cannot_spawn_ship_of_hull", member.hullSpec.hullName) + FBTxt.txt("ship_hidden_in_codex"), Color.YELLOW)
                return
            }
            if (getDeployedFleetPoints(engine, FleetSide.ENEMY) + member.deployCost > Global.getSettings().battleSize / 2f * 1.2f) {
                DisplayMessage.showMessage(FBTxt.txt("cannot_spawn_ship_of_hull", member.hullSpec.hullName) + FBTxt.txt("ship_exceed_fleet_limit"), Color.YELLOW)
                return
            }
            /*if (member.hullSpec.hints.contains(ShipTypeHints.STATION)) {
                DisplayMessage.showMessage(FBTxt.txt("cannot_spawn_ship_of_hull", member.hullSpec.hullName) + FBTxt.txt("ship_no_station"), Color.YELLOW)
                return
            }*/
        }
        if (!isPositionFree(engine, loc.x, loc.y, member.hullSpec.collisionRadius / 2f)) {
            loc = findFreeSpawnLocation(engine, loc.x, loc.y, member.hullSpec.collisionRadius) ?: run {
                DisplayMessage.showMessage(FBTxt.txt("cannot_spawn_ship_of_hull", member.hullSpec.hullName) + FBTxt.txt("ship_no_free_location"), Color.YELLOW)
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