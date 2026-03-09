package fleetBuilder.serialization.person

import fleetBuilder.serialization.SerializationUtils.metaSep

object CompressedPerson {
    fun isCompressedPerson(comp: String): Boolean {
        val metaIndexStart = comp.indexOf(metaSep)
        if (metaIndexStart == -1) return false

        val metaVersion = comp.getOrNull(metaIndexStart + 1)
        return metaVersion?.equals('p', ignoreCase = true) == true
    }
}