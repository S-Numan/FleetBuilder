## 1.32.5
### Bugfixes
- Now checks for modules when checking if the player knows all the contents of a ship
- Replace fleet now works with the tournament fleets in the mod files.

## 1.32.0
### Additions
- Added SMod and DMod bars for ships, notably in the autofit UI.
- Added completely new UI for pasting fleets. This replaces both former fleet pasting elements.
- The new fleet pasting UI can be opened without cheats and allows the user to simulate a battle against the pasted fleet without consequences.
### Changes
- Remove all dialog animations by default. Make them all pop up instantly.
- Moved Cargo Auto Manager "Add Custom" button to outside the scroller tooltip, so the user no longer needs to scroll down to click it if the list of elements is too large.
- Added default Cargo Auto Manager suggestions for supplies, crew, and fuel.
- Auto toggle "Take" in Cargo Auto Manager when adding a new custom option.
- Keep custom error messages even when entering/leaving combat.
- Many more changes far too numerous for me to remember.
### Bugfixes
- Added missing officer_copied_to_clipboard_compressed string.
- Reorder in what order certain contents of members and fleets are loaded on paste to better account for stats. (E.G captains/commanders load before the Fleet Members, and thus stats such as maxCR get applied properly now)
- Automated fleet AI core's now become built in when copying if exceeding their default AI level. This is a fix to a vanilla issue, although it'd never come up normally.
- etc

## 1.31.6
- Saving Cargo Auto Manager entries is quite finicky.
### Bugfixes
- Fix Cargo Auto Manager saving special items will null data, such as the industrial evolution ability items.
- Remove autofit Apply SMods button if Ship Mastery System mod exists, to avoid conflicting functionality.

## 1.31.4
### Bugfixes
- Fix special items not saving correctly in the Cargo Auto Manager (such as blueprints)

## 1.31.1
### Changes
- Made mod removal slightly more safe, by asking the user if they are sure.
- Updated the mod picker filter to match hullmod display names that contain the input text, rather than only those that start with it.

## 1.31.0
### Additions
- Added "Upgrade weapons using extra OP" and "Strip before autofitting" to autofit toggle list.
- Added "Available policies" button in the Cargo Auto Manager. Open this to save and load a list of Cargo Auto Manager policies even between save games.
- Added a "Remove Mod" button in the dev dialog, which does as implied.
    * Removes most contents of a mod from your game, including:
    * Every mod based item (weapons, wings, ships, industries)
    * Every faction owned entity added by the mod (markets, fleets, comm relays)
    * Optionally every listener the mod adds.
    * * This is very much so not safe to use, but I left it in in case anyone wanted to live dangerously.
### Changes
- Dialogs now close on input press down, instead of on letting go.
### Bugfixes
- Fix multiple layers of UI dialogs closing when they shouldn't after hitting ESC or right click.

## 1.30.2
### Changes
- No more fleet info prepend in SaveTransfer json files.
- Backup SaveTransfer now uses compression on the entire file to save space.
### Bugfixes
- Fixed SoC data not saving in compressed fleets such as in SaveTransfer.
- Fixed holding shift to copy compressed fleet not working in fleet tab.
- Fix bad autofit functionality in the title-screen mission refit.

## 1.30.0
#### Big update!
### Additions
- Newly added Mothball Recovered Ships setting in the Misc tab. When this is true, newly recovered ships will be automatically mothballed.
- Add logs to Console and Add logs to Display Message setting. When enabled, any logs sent to starsector.log above the specified level will be put in either the ConsoleCommands console or displayed on screen.
- An autofit button now shows up in codex above the ship display; just click the button to see!
- Added a setting to disable the transponder when entering hyperspace. Yes, I know, this also exists in the QOL life pack.
- Compressed person and member on copying by default.
- Compressed fleet if holding shift on copying. Automatically enabled in save transfers to save space.
- Compressed cargo in save transfer to save space.
### Changes
- Gave the CopyFleet command the ability to work in the fleet screen UI and interactions alongside the existing mission and in combat functionality.
- Increase size of onslaught in autofit UI.
- In the Cargo Auto Manager, the "Put" toggle is now by default toggled on for new custom inputs.
- Autofit variant tooltip's position themselves to the left side if on the right side of the UI.
- Avoid showing autofit variants that the player is not aware of.
- Rearrange LunaLib settings a bit.
- Allow autofit loadout display name to be empty.
- Prevent adding more than max limit of officers by mothballing captained ships over the limit.
- Right clicking outside the autofit / PopUpUI dialogs now close the dialogs.
- Can now press F2 on an autofit loadout to open it's codex entry.
- Improved missing element handling to now more frequently report what mods were used when pasting a variant/person/fleet with missing elements
- Externalized more strings
- Reported errors by this mod now show their error through custom rendering rather than relying on the campaign or combat messages.
- Popups now opens from the middle vertically with a better animation.
- Members now roughly save and load their current hull and armor state, along with CR.
- Pressing D in the CTRL + SHIFT + D dev menu now toggles dev mode. No mouse needed. The toggle sound was changed too.
### Bugfixes
- Fix crash when opening the dev dialog when the refit screen hullmod adding dialog is open.
- Fix failed fleet copy when FleetMemberAPI name is null for some reason
- Added an intercept to forceclosedialog from Console Commands to also close this mod's dialogs. Just in case.
- Fix Cargo Auto Manager "Blueprints and ModSpecs" not consistently working.
- Fix pasting members with officers into a submarket losing the captain when the player takes the member.
- Fix isCodexOpen() still not working in some niche conditions.
- Autofit entries which have had their files removed now have their path to them removed as well. This gets rid of the WARN messages on every game launch in the log.
- Fix officers not being paste-able if they lack any skills
- Fix officers not storing in different language.
- Fix accidently using "Include Cargo" instead of "Include Credits" causing confusion.
- Finally figured out how to center the autofit flux tooltips properly. Horray!
- No longer hold some things in memory more than needed.
- Fix mission autofit failing to open if the player opened the mission tab refit screen, campaign, then the mission tab refit screen again.
- Autofit now applies in the mission tab. If it didn't already.
### Technical
- Major internal rearrangements. Separate functionality is now grouped together, like they should be.
- A small overhaul to the Popups. It will be a little different now in many ways.
- The versioning should be 2.0.0, however, as that causes a red "mod is unlikely to load correctly" on save game I'll only make that version change on the next starsector update.

## 1.27.1
- Fix copy pasted variants loading improperly after saving and loading a game.
- Minor internal tweaks.

## 1.27.0
- CTRL + V in combat to paste the copied variant/member into combat. This requires cheats to be on if in regular combat, but you can mostly do it freely in the simulator with only some exceptions (E.G: ships you wouldn't be able to simulate anyway).
- No longer pause the game after exiting a dialog in the campaign if you were not originally paused.
- Added ConsoleCommand RemoveIdleOfficers which removes all officers in your fleet which are currently not captaining any ship.
- Added a Misc setting to stop error messages from being shown at the top of the screen if it is true.
- Fix crash that may occur on game launch if your loadout directory json is malformed. Also show dialog on game start informing user of issue.
- Fix crash if you remove or malform a loadout directory json while the game is running.
- Prevent titlescreen messages from sticking around after leaving the titlescreen.
- Fix pasting a variant/member into combat opening the import loadout dialog.
- Add a scrollbar to the autofit options which will only show if there is not enough space to display all of the options.
- Further translation support, although still not finished.
### Technical
- Dialogs can now be made before the game has started completely, and will be shown once the game has started.
- Removed the old PopUpUI code and finished refactoring a few dialogs to use the newer code. (This took many hours ...)

## 1.26.1
- Fix crash that may occur on save game. return value of "ShipHullSpecAPI.getBaseHull()" is null.

## 1.26.0
- SaveTransfer's 'Include Ability Bar' option now also saves and loads abilities. E.G: on a new save you can get your slipsurge ability with a save transfer.
- Fix the codex button not spawning ships with modules. Ships now spawn with modules.
- Fix the autofit code from crashing the game if certain modules do not exist.

## 1.25.4
- Patch CargoAutoManageUIPlugin's custom cargo selection to work in Windows.

## 1.25.3
- Sweeping safety improvements across the mod. Many things that would cause a crash now only display an error and continue on anyway. This should hopefully make any crashes much less frequent.
- Prevent some possible error spam by preventing the same error from being displayed more than once every 4 seconds. E.G to prevent spam if an error occurs every frame.
- Exclude Starship Legends hullmods from being copied.

## 1.25.0
- Remove all credits/cargo/blueprints/hullmods on loading a save transfer if relevant toggle is checked. This is to effectively replace the current save, instead of the previous behavior of appending to.
- Save Transfer now also saves and loads the contents of the ability bar.
- Fix officer creation failing to give the officer enough XP to max out their level.
- An untested fix to prevent crashing when loading a save transfer with an industry_bp which the industry of no longer exists.
- Tags starting with the hash character '#' will now not be saved when saving a variant or officer.
- Fix Copy Fleet command failing to save titlescreen mission commanders.
- Fix autofit failing to handle variants with variantID's starting with the same prefix as the ship directory. (E.G a variantID like (DF_DF_sunder_Hull) would cause autofit to not work right)
- When Cheats are enabled, unassign player from ship is automatically enabled.
- A long list of other small backend changes and simplifications I cannot remember.

## 1.24.0
- Patch to fix CTRL + V pasting with the new ConsoleCommands 4.0
- Variant comparison now ignores weapon group slots without weapons.
- Internally moved all imported (CTRL + V'ed in) variants.
### Technical
- Refactored autofit variant storage handling.
- Removed the IN prefix. Migrating all contents to the DF prefix.
- Custom autofit now only accesses variants within the default prefix.

## 1.23.2
- Fix saving variants with more than one mod involved.

## 1.23.0
- Autofit highlight and outline now pays attention to wings too. It previously did not.
- Fix the incorrect autofit hull showing up in some cases
- Copied variants are now compressed strings by default. Hold shift to copy variants as a json instead.
- Saved autofit variants store themselves as compressed strings now, for space efficiency. Mod remains compatible with existing uncompressed variants.
- Other tweaks of lesser note.

## 1.22.1
- Added some code to further assure the nexerelin version checker dialog does not appear on CTRL + V. The annoyance.

## 1.22.0
- Added LunaLib Enable Cheats setting.
- Inform user to enable the cheat setting if they want to use the more cheaty commands (such as pasting fleets).
- Prevent game freeze if pasted string into fleet filter is too large by capping the allowed character count.
- Added messages after appending to or replacing your fleet, for clarity.
- If the unassign player setting is on, put the player in the commander shuttle when storing/selling the player piloted ship to avoid the player officer replacing another.
- Highlight the autofit slot if it is equivalent, but do not outline it when the match is due solely to an sModdedBuiltIn that the variant’s hullSpec lacks. (See Mad Rockerpiper MIDAS from Roider Union)

## 1.21.1
- Fixed paste into fleet message being backwards.

## 1.21.0
- Fixed person memkeys failing to save. (Including combat chatter character)
- Support for pasting members and variants onto the campaign layer.
- Unfinished porting strings to strings.json, for translation support.

## 1.20.0
- Removed duplicate entries in the cargo saving JSON. Backwards compatible.

## 1.19.0
- Fixed right clicking hullmods removing the built in hullmod too, when the hullmod is an sModdedBuiltIn.
- Presumably fixed crash after save on gigantic save files.

### Autofit
- Reserve the first four slots by default to prevent your saved loadout positioning from being messed up. This can be disabled in the mod settings.
- When stripping the loadout, the logic did not inform the autofit applier that the weapon existed again. This has been fixed.
- Do not save SModded Built Ins which aren’t actually built into the ship hull. (E.G Mad Rockerpiper MIDAS from Roider Union)
- No longer applies SMods when there isn't enough OP to apply the hullmod in the first place. (For some modded ships that have 0 OP)

## 1.18.0
- Allowed copying and importing variants in the titlescreen missions.
- Do not open the refit dialog when the wing or weapon selector dialogs are open.
- PersonAPI now saves integer memKeys
- Prevent copying of the commander shuttle.
- Commander shuttle improvements.
- Fix compressed variants not functioning.

## 1.17.1
- Prevent the Nexerelin version checker dialog from opening when pasting fleets into the campaign.
- Fix being able to interact with outside elements when in dialog.
- Improve Roider Union MIDAS support when saving and loading a variant.
- Avoid showing non-relevant toggles in the missions tab. (which is all of them)

## 1.17.0
- Autofit variants that differ in flux stats or weapon groups but are the same otherwise now render a little icon showing that difference.
- Don't set the player's faction on replacing your fleet with another.
- Fix fleet commander not being saved on saving a fleet, if the ship the commander was piloting was excluded for whatever reason.
- Tweak to the autofit menu to not save specific variant tags. This should make autofitting more consistent in the future.
- Moved several features outside of the Hotkey Handler.
- Improvements to built in DMod handling.
- A bunch of minor tweaks here and there.
### Technical
- Another big refactor of all the serialization functions (excluding cargo). I've got to stop doing this. The end user wont notice a thing.

## 1.16.0
- Saving and loading should now support Combat Chatter.
- Fix niche situation whereas the autofit menu fails to get the variant when viewing a skin of a hull, which happens to be a dmodded skin and thus posses a dParentHullId.
- Fix saving variants with built in wings and non built in wings at the same time.
- Fix being unable to CTRL click copy the current variant.
- Fix stored AI core officers being added to the fleet officers on recovery from submarket.
- Fix accidental saving of the stored officer memKey
- Fleet saving can now save and load the fleet's faction.
- Simplified the Save Transfer dialog
- Show message when Cargo Auto Manager manages your cargo. For clarity.
- Fleet panel now updates when appending or replacing fleet.

## 1.15.0
- A complete revamp of the Autofit UI. Save compatible.
- Created the Mod Picker Filter, similar to the Fleet Filter, but for hull mods.
- Fix dev mode codex button failing to add variant's and members
- Fix scrollbar on Cargo Auto Manager.
- Fix force autofit sometimes failing to remove SMods.
- Added "Repair and Set Max CR" to campaign fleet spawning dialog.

## 1.14.0
- Added Cargo Auto Manager. Right click your storage submarket to open a dialog that allows you to automatically take and store cargo items.
- Fix aggression_doctrine not being applied correctly.
- Titlescreen message now implemented properly.
- Added a tooltip to the Codex Button.
- Added a tooltip to the Fleet Screen Filter.
- Quote Support for Fleet Screen Filter.
- Moved the /SaveTransfer folder to be inside the /FleetBuilder folder for organization purposes.
- Improved JSON reading to skip '#' comments instead of failing.
- Basic variant compression support. Hold 'Shift" while copying a variant in the refit screen to compress the data into a smaller format.
- Fix ship modules failing to save after force applying via the autofit ui.
- "pick_random_variant" boolean for JSON variants. Instead of specifying the contents of a variant, simply use `"pick_random_variant": true`, and a random variant of the hullID will be selected.
- Fixed codex not being acquried properly when clicking a ship in the missions tabs.
- Various other undocumented fixes.
### Technical
- A notable amount of backend changes

## 1.13.1
- Fixed faction priority messing up player autofit

## 1.13.0
- Now supports the Second-in-Command mod when saving/loading fleets (including save transfers) due to request. May need testing.
- Fixed partially broken missing element reporter
- Properly report missing cargo and blueprints now.
- Better handling for loading ships from missing mods.
- Added "Fulfill cargo, fuel, crew, and repair for fleet" toggle when pasting a fleet into yours.
- Store officers now works with IndEvo Repair Docks.
- Autofit now properly prevents you from removing hullmods which require a dock

## 1.12.0
- Fix the CommandShuttle being unable to jump even with other members present.
- Minor performance optimizations
- Small dialog improvements across the board.
- CopyFleet console command, to copy fleets in combat. (Mostly useful for copying mission fleets such as tournament fleets)
- Added fleet filter keyword: "combat", and changed functionality of the "civilian" keyword. "combat" applies to ships that count towards combat ship FP.
- Made compatible with officers from the tahlan Lostech ships. (that is to say, disabled officer storage if they are of that type, as tahlan does it it's own way)

## 1.11.0
- Add officer creator dialog via CTRL + O
- Loadout importing UI
- Add Save Transfer dialog via CTRL + I
- Add Developer Mode dialog via CTRL + SHIFT + D
- Better missing elements reporting
- Legacy flagship loading now puts the flagship at the start of the fleet on loading a fleet

## 1.10.0
- Configurable UI pop ups on using CTRL V to paste a fleet into the campaign or fleet screen.
- Properly handle saving built in DMods. Such as the Lion's Guard "Special Modifications"
- Hidden hullMods are now saved by default, compared to the previous behavior of not doing that. This makes magiclib paintjobs work.
- A few other small bug fixes.
### Technical
- Completely refactor all the serialization loading except cargo AGAIN.

## 1.9.0
- Fix handling the somewhat badly implemented Emergent Threats mod's variants.
### Technical
- A sweeping refactor
- On Emergent Threat: their threat ships have one more built in wing than actual, and add/remove hullmods in applyEffectsBeforeShipCreation causing a concurrent modification exception. Best I can do is add a try catch to prevent a crash.

## 1.8.0
- CTRL + C in the fleet screen now copies only the visible fleet members. If you copy your fleet with a Fleet Filter on, it'll only copy those applicable to the filter. Additionally, it copies fleets in the submarkets for sale.
- Fix SModded fleet filter keyword.
- Autofit now properly highlights the Mad Roider hullmod (roider_midas)
- Rearanged LunaSettings and improved grammar here and there
- Added a mod icon!
- Fix several bugs related to loading a PersonAPI(officer) from json
### Technical
- Refactored all the serialization functions (excluding cargo).
- Uses 'isFlagship' to denote a commander instead of a seperate commander entry. If possible.
- Settings for getting a fleet from a JSON now follow the same Settings paramters as all the other serialization functions.
- More settings for every serialization function (excluding cargo)

## 1.7.1
- Disabled the Fleet Screen Filter by default. To avoid adding undesired UI without the user wanting it.
- The Fleet Filter now stays when the fleet panel updates, such as when pasting a new ship into the fleet.

## 1.7.0
- 'Store Officers In Cargo' setting now exists. When this is toggled to true in the settings: Officers will stay in their ship when moving their ship to storage, thus removing them from your fleet. You can later get that ship from storage, along with the officer.
- Enabled the Fleet Screen Filter by default
- Added "civilian" keyword to the filter
- Added "officered"/"captained" keyword to the filter, to filter for ships that have captains.
- More robust save loading. Loading blueprints and hullmods now handles missing elements. Loading members now shows what ship was missing in the ship name.
- Fix improper outlining (again)
- CTRL V to paste now pastes in the viewed submarket, if the user is currently viewing a submarket rather than their own fleet.
- Proper creation of AI Core officers on paste

## 1.6.1
- Improved fleet filtering functionality. More options and aliases have been added.
- More accurate autofit outlining.
- CopySave and LoadSave now copy and load from the clipboard instead of a file.
- CopySave and LoadSave arguments are now exposed. You can now specify things such as -no-rep to not load reputation. -no-player to not load the player's skills/levels. -no-fleet to not load the player's fleet. ETC
- Cargo serialization now handles missing elements as well.

## 1.6.0
- Improve variant comparing logic. The autofit UI now ignores DMods and treats SMods as regular mods when deciding what to highlight.
- Autofit UI additionally outlines your loadout with red or green if it is better or worse. Got a DMod? It'll be outlined red. Got An SMod? It'll be outlined green. Loadout has SMod but yours does not? It'll be outlined red. ETC
- Added the fleet screen filter. Enter text into it and those that do not satisfy the criteria will be filtered out, UI only.
- New hotkey: In dev mode, you can now right click a hullmod to get rid of it even if it doesn't give you the option to. Be gone, SMods.
- New hotkey: CTRL + V in the simulator to add the copied fleet/member/variant to the simulator reserves. CTRL + D to add your own fleet to the simulator reserves.
- A notable amount of backend changes.

## 1.5.2
- Fix AI cores turning into fleet officers when pasting a ship captained by one.
- Fix DMod saving

## 1.5.1
- Fighter wings did not save when saving a variant. They are now saved.

## 1.5.0
- Hidden hullmods are now handled.
- /saves/common/FleetBuilder/HullModsToNeverSave.data is now a file that exists. Put a hullmod id in the list, and it'll never be saved.
### Technical
- Complete revamp of internal saving settings.

## 1.4.1
- Forgot to change the mod version. It is now changed.
### Technical
- Rename OfficerSerialization to PersonSerialization
- Store custom data when saving person/officer to JSON instead of the previous manual behavior.

## 1.4.0
- Unscuffed the combat(titlescreen mission) autofit adder.
- The loadout importer now supports importing from a pasted fleet member by extracting the member's variant data.
- Fix getEffectiveHullId(). This resolves the issue of the wrong variant type appearing in the autofit UI sometimes.
- JSON variant saving is now custom: hullmods like S-mods are stored only once, under sMods, not duplicated in hullMods or permaMods.
- The state of AI cores being built in is now saved.
- Enable/disable the codex button now in the LunaSettings
### Technical
- Added OfficerAssignmentListener
- Changed ship directory formatting. The pre 1.4.0 format will automatically convert to the new format to preserve your loadouts.
- The scripts/listeners of the autofit menu, hotkey handler, and codex button now are no longer added as a script/listener if disabled.
- Hotkey handler refactor
- An additional, notable amount of backend changes, though less impactful

## 1.3.5
- Codex button now supports adding hullmods to your faction
- Codex button supports giving the blueprint for the viewed entry by holding CTRL when clicking.

## 1.3.4
- Moved mod_info to top-most json. No more duplicate mod_info entries for each and every variant in a fleet.

## 1.3.3
- Made LunaLib dependency optional
- Added includeCommanderAsOfficer to saveFleetToJson, to allow avoiding adding the commander as an officer.

## 1.3.2
- Tracks the time a variant was saved to the custom autofit UI, for future use. Does nothing as of now.

## 1.3.0
- Refactor
- Added a button you can press to add the codex entry you're looking at to your fleet

## 1.2.0
- Added version checker functionality
- Added CHANGELOGS.md
