package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.characters.PersonAPI
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.serialization.person.PersonSettings
import fleetBuilder.util.api.PersonUtils


/**
 * Creates an exact copy of this person.
 *
 * Note that memKey reference types will copy the reference, not the object itself.
 */
fun PersonAPI.clone(filterParsed: Boolean = false): PersonAPI {
    return DataPerson.clonePerson(this, filterParsed = filterParsed)
}

/**
 * Creates a copy of this person with the specified settings.
 *
 * Will apply a filter pass based on the settings, enabling this person to be serializable. This will remove non value type memKeys if present
 */
fun PersonAPI.clone(settings: PersonSettings): PersonAPI {
    return DataPerson.clonePerson(this, settings = settings)
}

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