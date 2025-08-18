package starficz


/**
 * A comprehensive enum of every font used in Starsector
 */
enum class Font {
    // Arial Fonts
    ARIAL_10,             // arial10.fnt
    ARIAL_12_BOLD,        // arial12bold.fnt
    ARIAL_16_BOLD,        // arial16bold.fnt

    // Insignia Fonts
    INSIGNIA_15,          // insignia15LTaa.fnt
    INSIGNIA_16,          // insignia16.fnt
    INSIGNIA_16_A,        // insignia16a.fnt
    INSIGNIA_21,          // insignia21LTaa.fnt
    INSIGNIA_25,          // insignia25LTaa.fnt

    // Orbitron Fonts
    ORBITRON_10,          // orbitron10.fnt
    ORBITRON_12,          // orbitron12.fnt
    ORBITRON_12_CONDENSED,// orbitron12condensed.fnt
    ORBITRON_20,          // orbitron20.fnt
    ORBITRON_20_AA,       // orbitron20aa.fnt
    ORBITRON_20_BOLD,     // orbitron20bold.fnt
    ORBITRON_20_AA_BOLD,  // orbitron20aabold.fnt
    ORBITRON_24,          // orbitron24aa.fnt
    ORBITRON_24_BOLD,     // orbitron24aabold.fnt

    // Small Fonts
    SMALL_FONTS_8,        // small_fonts8.fnt

    // Victor Fonts
    VICTOR_10,            // victor10.fnt
    VICTOR_12,            // victor12.fnt
    VICTOR_14,            // victor14.fnt
    VICTOR_16,            // victor16.fnt
    VICTOR_21,            // victor21.fnt
}

fun getFontPath(font: Font): String {
    return when (font) {
        // Arial
        Font.ARIAL_10 -> "graphics/fonts/arial10.fnt"
        Font.ARIAL_12_BOLD -> "graphics/fonts/arial12bold.fnt"
        Font.ARIAL_16_BOLD -> "graphics/fonts/arial16bold.fnt"

        // Insignia
        Font.INSIGNIA_15 -> "graphics/fonts/insignia15LTaa.fnt"
        Font.INSIGNIA_16 -> "graphics/fonts/insignia16.fnt"
        Font.INSIGNIA_16_A -> "graphics/fonts/insignia16a.fnt"
        Font.INSIGNIA_21 -> "graphics/fonts/insignia21LTaa.fnt"
        Font.INSIGNIA_25 -> "graphics/fonts/insignia25LTaa.fnt"

        // Orbitron
        Font.ORBITRON_10 -> "graphics/fonts/orbitron10.fnt"
        Font.ORBITRON_12 -> "graphics/fonts/orbitron12.fnt"
        Font.ORBITRON_12_CONDENSED -> "graphics/fonts/orbitron12condensed.fnt"
        Font.ORBITRON_20 -> "graphics/fonts/orbitron20.fnt"
        Font.ORBITRON_20_AA -> "graphics/fonts/orbitron20aa.fnt"
        Font.ORBITRON_20_BOLD -> "graphics/fonts/orbitron20bold.fnt"
        Font.ORBITRON_20_AA_BOLD -> "graphics/fonts/orbitron20aabold.fnt"
        Font.ORBITRON_24 -> "graphics/fonts/orbitron24aa.fnt"
        Font.ORBITRON_24_BOLD -> "graphics/fonts/orbitron24aabold.fnt"

        // Small Fonts
        Font.SMALL_FONTS_8 -> "graphics/fonts/small_fonts8.fnt"

        // Victor
        Font.VICTOR_10 -> "graphics/fonts/victor10.fnt"
        Font.VICTOR_12 -> "graphics/fonts/victor12.fnt"
        Font.VICTOR_14 -> "graphics/fonts/victor14.fnt"
        Font.VICTOR_16 -> "graphics/fonts/victor16.fnt"
        Font.VICTOR_21 -> "graphics/fonts/victor21.fnt"
    }
}