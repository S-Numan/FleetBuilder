package fleetBuilder.core

import com.fs.starfarer.api.impl.campaign.ids.Tags

object FBConst {
    const val PRIMARY_DIR = "FleetBuilder/"
    const val LOADOUT_DIR = (PRIMARY_DIR + "LoadoutPacks/")
    const val FLEET_DIR = (PRIMARY_DIR + "Fleets/")
    const val DIRECTORY_CONFIG_FILE_NAME = "directory"

    const val COMMAND_SHUTTLE_ID = "FB_commandershuttle"
    const val STORED_OFFICER_TAG = "\$FB_stored_officer"
    const val NO_COPY_TAG = "FB_no_copy"

    const val VARIANT_MADE_IN_ERROR = "VARIANT_MADE_IN_ERROR"

    /**
     * Only applicable when copying a variant alone. Does not apply when copying a member or entire fleet.
     */
    val DEFAULT_EXCLUDE_TAGS_ON_VARIANT_COPY = setOf(
        Tags.SHIP_RECOVERABLE,
        Tags.TAG_NO_AUTOFIT,
        Tags.VARIANT_CONSISTENT_WEAPON_DROPS,
        Tags.TAG_RETAIN_SMODS_ON_RECOVERY,
        Tags.VARIANT_ALWAYS_RETAIN_SMODS_ON_SALVAGE,
        Tags.VARIANT_ALWAYS_RECOVERABLE
    )
}