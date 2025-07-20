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
