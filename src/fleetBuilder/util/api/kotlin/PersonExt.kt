package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.characters.PersonAPI
import fleetBuilder.util.api.PersonUtils

fun PersonAPI.getMaxOfficerLevel(): Int {
    return PersonUtils.getMaxOfficerLevel(this)
}

fun PersonAPI.getMaxOfficerEliteSkills(): Int {
    return PersonUtils.getMaxOfficerEliteSkills(this)
}

/**
 * Checks if the person has any skills with a level greater than 0.
 */
fun PersonAPI.hasAnySkill(): Boolean {
    for (skill in stats.skillsCopy) {
        if (skill.level > 0f) {
            return true
        }
    }
    return false
}