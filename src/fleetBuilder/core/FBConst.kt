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

    const val FB_ERROR_TAG = "FB_ERR"

    val DEFAULT_EXCLUDE_TAGS_ON_VARIANT_COPY = setOf(Tags.SHIP_RECOVERABLE, Tags.TAG_RETAIN_SMODS_ON_RECOVERY, Tags.TAG_NO_AUTOFIT, Tags.VARIANT_CONSISTENT_WEAPON_DROPS)
}