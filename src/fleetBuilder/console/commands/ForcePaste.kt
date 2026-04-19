package fleetBuilder.console.commands

import com.fs.starfarer.api.Global
import fleetBuilder.core.FBMisc.isConsoleOpen
import fleetBuilder.core.FBSettings
import fleetBuilder.features.hotkeyHandler.CampaignClipboardHotkeyHandler
import fleetBuilder.util.api.kotlin.safeInvoke
import fleetBuilder.util.deferredAction.CampaignDeferredActionPlugin
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console
import org.lazywizard.console.overlay.v2.panels.ConsoleOverlayPanel

class ForcePaste : BaseCommand {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }
        if (!isConsoleOpen()) {
            Console.showMessage("Console is not open. (This check fails on the legacy version of the console)")
            return BaseCommand.CommandResult.ERROR
        }

        val instance = runCatching { ConsoleOverlayPanel.instance }.getOrNull()
        instance?.placeHolderDialog?.safeInvoke("makeOptionInstant", 0)
        instance?.close() // Close the console

        // Retry until the console dialog goes away
        CampaignDeferredActionPlugin.performLater(0f) {

            val cheatsEnabled = FBSettings.cheatsEnabledRaw()
            if (!cheatsEnabled)
                FBSettings.setCheatsEnabled(true)

            try { // Just in case it somehow fails
                CampaignClipboardHotkeyHandler.handlePasteHotkey(Global.getSector().campaignUI, null)
            } catch (e: Exception) {
                Console.showException("Failed to paste", e)
            }
            if (!cheatsEnabled)
                FBSettings.setCheatsEnabled(false)

        }

        return BaseCommand.CommandResult.SUCCESS
    }
}