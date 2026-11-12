package fleetkit.core.config

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Tags

object FTVariables {
    const val PREFIX = "FT"

    const val MOD_ID = "SN_FleetToolkit"

    private const val PRIMARY_DIR_OLD = "FleetBuilder/"
    private const val PRIMARY_DIR_NEW = "FleetToolkit/"

    val primaryDir: String by lazy {
        val oldExists = Global.getSettings()
            .fileExistsInCommon("${PRIMARY_DIR_OLD}HullModsToNeverSave")
        if (oldExists) PRIMARY_DIR_OLD else PRIMARY_DIR_NEW
    }

    val loadoutDir: String by lazy { "${primaryDir}LoadoutPacks/" }
    val fleetDir: String by lazy { "${primaryDir}Fleets/" }

    const val DIRECTORY_CONFIG_FILE_NAME = "directory"

    const val COMMAND_SHUTTLE_ID = "${PREFIX}_commandershuttle"
    const val STORED_OFFICER_TAG = "\$${PREFIX}_stored_officer"
    const val NO_COPY_TAG = "${PREFIX}_no_copy"

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