package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.SkillSpecAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipSystemSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import fleetBuilder.core.FBSettings
import fleetBuilder.util.api.VariantUtils.createErrorVariant
import fleetBuilder.util.api.kotlin.*

object LookupUtils {

    private lateinit var allDMods: Set<String>
    private lateinit var allHiddenEverywhereMods: Set<String>
    private lateinit var allVariants: List<ShipVariantAPI>
    private lateinit var hullIDToVariant: Map<String, List<ShipVariantAPI>>
    private lateinit var effectiveHullIDToVariant: Map<String, List<ShipVariantAPI>>
    private lateinit var baseHullIDToVariant: Map<String, List<ShipVariantAPI>>
    private lateinit var compatibleDLessHullIDToVariant: Map<String, List<ShipVariantAPI>>
    private lateinit var actualHullIDToVariant: Map<String, List<ShipVariantAPI>>
    private lateinit var hullIDSet: Set<String>
    private lateinit var errorVariantHullID: String
    private lateinit var IDToHullSpec: Map<String, ShipHullSpecAPI>
    private lateinit var IDToWing: Map<String, FighterWingSpecAPI>
    private lateinit var IDToWeapon: Map<String, WeaponSpecAPI>
    private lateinit var IDToHullMod: Map<String, HullModSpecAPI>
    private lateinit var IDToSkill: Map<String, SkillSpecAPI>
    private lateinit var allFactionIDs: Set<String>
    private lateinit var IDToShipSystem: Map<String, ShipSystemSpecAPI>
    private var init = false
    fun isSetup() = init

    internal fun setup() {
        val settings = Global.getSettings()

        allDMods = settings.allHullModSpecs
            .asSequence()
            .filter { it.hasTag("dmod") }
            .map { it.id }
            .toSet()

        allHiddenEverywhereMods = settings.allHullModSpecs
            .asSequence()
            .filter { it.isHiddenEverywhere }
            .map { it.id }
            .toSet()

        /*
        //val variantIdMap = settings.hullIdToVariantListMap//Does not contain every variant
        val tempVariantMap: MutableMap<String, MutableList<ShipVariantAPI>> = mutableMapOf()
        for (variantId in settings.allVariantIds) {
            val variant = settings.getVariant(variantId) ?: continue
            if (variant.source != VariantSource.STOCK) continue
            val hullId = variant.hullSpec?.getEffectiveHullId() ?: continue

            //Are modules automatically put in every variant?
            /*variant.moduleSlots.forEach { slot ->
                val moduleVariant = settings.getVariant(variant.stationModules[slot].orEmpty())
                if(moduleVariant != null) {
                    variant.setModuleVariant(slot, moduleVariant)
                } else {
                    Global.getLogger(this.javaClass).error("module variant with id ${variant.stationModules[slot]} was null")
                }
            }*/

            //getOrPut
            //Checks if tempVariantMap contains the key hullId.
            //    If yes: returns the existing list.
            //    If no: creates a new mutableListOf() and puts it into the map under hullId
            tempVariantMap.getOrPut(hullId) { mutableListOf() }.add(variant)
        }

        effectiveVariantMap = tempVariantMap.mapValues { it.value.toList() }
        */

        hullIDSet = settings.allShipHullSpecs.map { it.hullId }.toSet()
        errorVariantHullID = createErrorVariant().hullSpec.hullId
        IDToHullSpec = settings.allShipHullSpecs.associateBy { it.hullId }
        IDToWing = settings.allFighterWingSpecs.associateBy { it.id }
        IDToHullMod = settings.allHullModSpecs.associateBy { it.id }
        IDToWeapon = settings.actuallyAllWeaponSpecs.associateBy { it.weaponId }
        IDToSkill = settings.skillIds.map { settings.getSkillSpec(it) }.associateBy { it.id }
        allFactionIDs = settings.allFactionSpecs.map { it.id }.toSet()
        IDToShipSystem = settings.allShipSystemSpecs.associateBy { it.id }


        allVariants = settings.allVariantIds.mapNotNull { runCatching { settings.getVariant(it) }.getOrNull() }
        if (FBSettings.cleanGameVariantsForRemovedElements) {
            Global.getLogger(this.javaClass).info("Cleaning variants for removed weapons, wings, and hull-mods")
            allVariants.forEach { varianty ->
                try {
                    fun cleanVariant(variant: ShipVariantAPI) {
                        variant.hullMods.toList().forEach { hullMod ->
                            if (getHullModSpec(hullMod) == null) {
                                variant.completelyRemoveMod(hullMod)
                                Global.getLogger(this.javaClass).info("Cleaned missing hull-mod '$hullMod' from variant-id '${variant.hullVariantId}' of hull-id '${variant.hullSpec.hullId}'")
                            }
                        }
                        variant.nonBuiltInWeaponSlots.toList().forEach { slot ->
                            val weapon = variant.getWeaponId(slot)
                            if (getWeaponSpec(weapon) == null) {
                                variant.clearSlot(slot)
                                Global.getLogger(this.javaClass).info("Cleaned missing weapon '$weapon' from variant-id '${variant.hullVariantId}' of hull-id '${variant.hullSpec.hullId}'")
                            }
                        }
                        variant.wings.toList().forEach { wing ->
                            if (getFighterWingSpec(wing) == null) {
                                variant.wings.remove(wing)
                                Global.getLogger(this.javaClass).info("Cleaned missing wing '$wing' from variant-id '${variant.hullVariantId}' of hull-id '${variant.hullSpec.hullId}'")
                            }
                        }
                    }
                    cleanVariant(varianty)
                    varianty.getModules().forEach { (_, moduleVariant) ->
                        cleanVariant(moduleVariant)
                    }
                } catch (e: Exception) {
                    Global.getLogger(this.javaClass).error("Error while cleaning variant-id '${varianty.hullVariantId}' of hull-id '${varianty.hullSpec.hullId}'", e)
                }
            }
        }


        hullIDToVariant = allVariants.groupBy { it.hullSpec.hullId }
        effectiveHullIDToVariant = allVariants.groupBy { it.hullSpec.getEffectiveHullId() }
        baseHullIDToVariant = allVariants.groupBy { it.hullSpec.baseHullId }
        compatibleDLessHullIDToVariant = allVariants.groupBy { it.hullSpec.getCompatibleDLessHullId() }
        actualHullIDToVariant = allVariants.groupBy { it.hullSpec.getActualHullId() }

        init = true
    }

    @JvmStatic
    fun getVariantsForEffectiveHullSpec(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        return effectiveHullIDToVariant[hullSpec.getEffectiveHullId()].orEmpty().map { it.clone() }
    }

    //fun getVariantsForHullSpec(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
    //     return hullIDToVariant[hullSpec.hullId].orEmpty().map { it.clone() }
    //}

    @JvmStatic
    fun getVariantsForBaseHullSpec(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        return hullIDToVariant[hullSpec.baseHullId].orEmpty().map { it.clone() }
    }

    @JvmStatic
    fun getVariantsForCompatibleDLessHullSpec(
        hullSpec: ShipHullSpecAPI
    ): List<ShipVariantAPI> {
        return compatibleDLessHullIDToVariant[hullSpec.getCompatibleDLessHullId()].orEmpty().map {
            it.clone()
        }
    }

    @JvmStatic
    fun getVariantsForActualHullSpec(
        hullSpec: ShipHullSpecAPI
    ): List<ShipVariantAPI> {
        return actualHullIDToVariant[hullSpec.getActualHullId()].orEmpty().map {
            it.clone()
        }
    }

    @JvmStatic
    fun getHullSpec(hullId: String) = IDToHullSpec[hullId]

    @JvmStatic
    fun getHullIDSet(): Set<String> = IDToHullSpec.keys
    fun getFighterWingSpec(wingId: String) = IDToWing[wingId]

    @JvmStatic
    fun getFighterWingIDSet(): Set<String> = IDToWing.keys

    @JvmStatic
    fun getWeaponSpec(weaponId: String) = IDToWeapon[weaponId]

    @JvmStatic
    fun getActuallyAllWeaponSpecIDSet(): Set<String> = IDToWeapon.keys

    @JvmStatic
    fun getHullModSpec(hullModId: String) = IDToHullMod[hullModId]

    @JvmStatic
    fun getHullModIDSet(): Set<String> = IDToHullMod.keys

    @JvmStatic
    fun getSkillSpec(skillId: String) = IDToSkill[skillId]

    @JvmStatic
    fun getAllSkillSpecs(): Collection<SkillSpecAPI> = IDToSkill.values

    @JvmStatic
    fun getAllDMods(): Set<String> = allDMods

    @JvmStatic
    fun getAllHiddenEverywhereMods(): Set<String> = allHiddenEverywhereMods

    //@JvmStatic
    //fun getAllVariants(): Set<ShipVariantAPI> = allVariants
    @JvmStatic
    fun getAllFactionIDs(): Set<String> = allFactionIDs

    @JvmStatic
    fun getShipSystemSpec(systemId: String): ShipSystemSpecAPI? = IDToShipSystem[systemId]

    internal fun getErrorVariantHullID() = errorVariantHullID

    //Is this needed? - Numan
    //No - Future Numan
    //fun reportFleetMemberVariantSaved(member: FleetMemberAPI, dockedAt: MarketAPI?) {

    //Here sets the variant ID after a variant is saved.

    /*val idIfNone = makeVariantID(member.variant)

    var matchingVariant: ShipVariantAPI? = null

    for (dir in LoadoutManager.getShipDirectories()) {
        val hullspecVariants = getLoadoutVariantsForHullspec(dir.prefix, member.variant.hullSpec)
        for (hullspecVariant in hullspecVariants) {
            if (compareVariantContents(
                    member.variant,
                    hullspecVariant,
                    CompareOptions(tags = false)
                )
            ) {//If the variants are equal
                matchingVariant = hullspecVariant
                break
            }
        }
        if (matchingVariant != null)
            break
    }

    if (matchingVariant == null) { // If not matching loadout variants
        matchingVariant = getCoreVariantsForEffectiveHullspec(member.hullSpec).find { candidate -> // Try looking in the base game?
            compareVariantContents(candidate, member.variant, CompareOptions(tags = false))
        }
    }

    member.variant.hullVariantId = when {
        matchingVariant != null -> {
            member.variant.moduleSlots.forEach { slot ->
                member.variant.getModuleVariant(slot).hullVariantId =
                    matchingVariant.getModuleVariant(slot).hullVariantId
            }
            matchingVariant.hullVariantId
        }

        else -> {
            member.variant.moduleSlots.forEach { slot ->
                member.variant.getModuleVariant(slot).hullVariantId = "${idIfNone}_$slot"
            }
            idIfNone
        }
    }*/
    //}
}