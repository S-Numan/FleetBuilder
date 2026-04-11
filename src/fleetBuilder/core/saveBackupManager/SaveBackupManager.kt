package fleetBuilder.core.saveBackupManager

import com.fs.starfarer.api.Global
import fleetBuilder.core.FBConst
import fleetBuilder.serialization.PlayerSaveUtils

object SaveBackupManager {
    private const val MAX_SLOT = 6
    private const val MIN_SLOT = 1
    private const val MAX_BACKUPS = 3

    fun createBackup() {
        try {
            val compSave = PlayerSaveUtils.createSaveJson(superCompressSave = true)

            if (compSave.length >= 1000000) {
                Global.getLogger(this::class.java)
                    .warn("FleetBuilder: Cannot create Backup Save, Backup Save is too large.")
                return
            }

            val base = "${FBConst.PRIMARY_DIR}/SaveTransfer"

            val newest = getNewestSlot(base)

            val nextSlot = when {
                newest == null -> MAX_SLOT
                newest == MIN_SLOT -> MAX_SLOT
                else -> newest - 1
            }

            // Write new save
            Global.getSettings().writeTextFileToCommon(
                "$base/lastSave-$nextSlot",
                compSave
            )

            // Cleanup AFTER write (safe now)
            cleanupOldest(base, nextSlot)

        } catch (e: Exception) {
            Global.getLogger(this::class.java)
                .error("FleetBuilder: Backup Save failed.", e)
        }
    }

    private fun cleanupOldest(base: String, newest: Int) {
        val existingCount = countExisting(base)
        if (existingCount <= MAX_BACKUPS) return

        val oldest = wrapSlot(newest + MAX_BACKUPS)

        try {
            Global.getSettings().deleteTextFileFromCommon("$base/lastSave-$oldest")
        } catch (_: Exception) {
        }
    }

    private fun getNewestSlot(base: String): Int? {
        for (start in MAX_SLOT downTo MIN_SLOT) {
            if (Global.getSettings().fileExistsInCommon("$base/lastSave-$start")) {

                var current = start

                while (true) {
                    val next = if (current == MIN_SLOT) MAX_SLOT else current - 1

                    if (!Global.getSettings().fileExistsInCommon("$base/lastSave-$next")) {
                        return current
                    }

                    current = next
                }
            }
        }
        return null
    }

    private fun countExisting(base: String): Int {
        var count = 0
        for (slot in MAX_SLOT downTo MIN_SLOT) {
            if (Global.getSettings().fileExistsInCommon("$base/lastSave-$slot")) {
                count++
            }
        }
        return count
    }

    private fun wrapSlot(value: Int): Int {
        var v = value
        while (v > MAX_SLOT) v -= MAX_SLOT
        return v
    }

    fun getNewestBackupPath(): String? {
        val base = "${FBConst.PRIMARY_DIR}/SaveTransfer"

        for (start in MAX_SLOT downTo MIN_SLOT) {
            if (!Global.getSettings().fileExistsInCommon("$base/lastSave-$start")) continue

            var current = start

            while (true) {
                val next = if (current == MIN_SLOT) MAX_SLOT else current - 1

                if (!Global.getSettings().fileExistsInCommon("$base/lastSave-$next")) {
                    return "$base/lastSave-$current"
                }

                current = next
            }
        }

        return null
    }
}