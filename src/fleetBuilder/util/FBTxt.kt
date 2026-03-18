package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import fleetBuilder.core.ModSettings
import org.magiclib.util.StringCreator
import java.awt.Color
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

object FBTxt {
    fun txt(id: String): String {
        return Global.getSettings().getString(ModSettings.getModID(), id)
    }

    fun txt(id: String, vararg args: Any?): String {
        return String.format(Global.getSettings().getString(ModSettings.getModID(), id), *args)
    }

    /**
     * Returns a translated string based on the given base key and count.
     * The key is suffixed with "_zero", "_one", or "_many" depending on the count.
     * If args are provided, the translated string is formatted with the args.
     * If args are not provided, the count is included as the sole arg.
     * If count is 0 and key "_zero" does not exist, or count is 1 and key "_one" does not exist, fallback to _many.
     *
     * @param baseKey the base key to use for translation
     * @param count the count to determine the correct key suffix
     * @param args optional arguments to format the translated string with
     * @return the translated string
     */
    fun txtPlural(baseKey: String, count: Int, vararg args: Any): String {
        fun pluralKey(count: Int): String = when (count) {
            0 -> "${baseKey}_zero"
            1 -> "${baseKey}_one"
            else -> "${baseKey}_many"
        }

        val key = pluralKey(count)

        return runCatching {
            if (args.isEmpty()) FBTxt.txt(key, count) else FBTxt.txt(key, *args)
        }.getOrElse {
            // fallback to _many if key does not exist
            if (args.isEmpty()) FBTxt.txt("${baseKey}_many", count) else FBTxt.txt("${baseKey}_many", *args)
        }
    }


    /**
     * If the input is an empty string, returns null. Otherwise, returns the input.
     */
    fun nullStringIfEmpty(input: String?): String? {
        return if (!input.isNullOrEmpty()) input else null
    }

    /**
     * Replaces all instances of the given regex with the string returned from stringCreator.
     * The difference from normal String.replaceAll is that stringCreator is only run if a match is found.
     */
    fun replaceAllIfPresent(
        stringToReplace: String,
        regex: String,
        stringCreator: StringCreator
    ): String {
        return if (stringToReplace.lowercase(Locale.getDefault()).contains(regex.lowercase(Locale.getDefault()))) {
            try {
                val replacement: String = stringCreator.create()
                Pattern.compile(Pattern.quote(regex), Pattern.CASE_INSENSITIVE)
                    .matcher(stringToReplace)
                    .replaceAll(replacement)
            } catch (e: Exception) {
                Global.getLogger(FBTxt::class.java)
                    .error("Error thrown while replacing $stringToReplace", e)
                Pattern.compile(Pattern.quote(regex), Pattern.CASE_INSENSITIVE)
                    .matcher(stringToReplace)
                    .replaceAll("null")
            }
        } else {
            stringToReplace
        }
    }

    /**
     * If the string is longer than the given length, returns the string truncated to the given length with "..." appended.
     *
     * Note that "..." is 3 characters, so the returned string will be a max of length + 3.
     *
     * @since 1.3.0
     */
    fun ellipsizeStringAfterLength(str: String, length: Int): String {
        return if (str.length <= length) {
            str
        } else {
            str.substring(0, length) + "..."
        }
    }

    private val highlightPattern: Pattern = Pattern.compile("==(.*?)==", Pattern.DOTALL)
    private val uppercaseFirstPattern: Pattern = Pattern.compile(".*(?<!\\\\)\\^(.).*")

    /**
     * Takes a string with the format "This is a ==highlighted word== string." and returns [MagicDisplayableText].
     */
    fun createMagicDisplayableText(str: String?): MagicDisplayableText {
        return MagicDisplayableText(str)
    }

    /**
     * Uses [MagicDisplayableText] to add a paragraph to the given [com.fs.starfarer.api.campaign.TextPanelAPI].
     * \n may be used to add multiple paragraphs.
     * You can use Misc.getTextColor() and Misc.getHighlightColor() to get default colors.
     */
    fun addPara(
        text: TextPanelAPI,
        str: String?,
        textColor: Color,
        highlightColor: Color
    ) {
        if (str.isNullOrEmpty()) {
            text.addPara("")
            return
        }

        val paras = str.split("\\n".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()

        for (para in paras) {
            val magicText = MagicDisplayableText(para)

            text.addPara(
                magicText.format,
                textColor,
                highlightColor,
                *magicText.highlights
            )
        }
    }

    /**
     * Uses [MagicDisplayableText] to add a paragraph to the given [com.fs.starfarer.api.ui.TooltipMakerAPI].
     * \n may be used to add multiple paragraphs.
     * You can use Misc.getTextColor() and Misc.getHighlightColor() to get default colors.
     */
    fun addPara(
        text: TooltipMakerAPI,
        str: String?,
        padding: Float,
        textColor: Color,
        highlightColor: Color
    ) {
        if (str.isNullOrEmpty()) {
            text.addPara("", padding)
            return
        }

        val paras = str.split("\\n".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()

        for (para in paras) {
            val magicText = MagicDisplayableText(para)

            text.addPara(
                magicText.format,
                padding,
                textColor,
                highlightColor,
                *magicText.highlights
            )
        }
    }

    private fun replaceStringHighlightsWithSymbol(str: String?): String {
        var format: String = highlightPattern.matcher(str ?: "").replaceAll("%s")
        var uppercaseMatcher: Matcher = uppercaseFirstPattern.matcher(format)

        while (uppercaseMatcher.matches()) {
            format = format.substring(0, uppercaseMatcher.start(1) - 1) +
                    uppercaseMatcher.group(1).uppercase(Locale.getDefault()) +
                    format.substring(uppercaseMatcher.end(1))
            uppercaseMatcher = uppercaseFirstPattern.matcher(format)
        }

        return format
    }

    private fun getTextMatches(str: String?, pattern: Pattern): Array<String> {
        val allMatches = mutableListOf<String>()

        val m: Matcher = pattern.matcher(str)
        while (m.find()) {
            allMatches.add(m.group(1))
        }
        return allMatches.toTypedArray()
    }

    /**
     * Takes a string with the format "This is a ==highlighted== sentence with ==words==."
     */
    class MagicDisplayableText(
        var originalText: String?
    ) {
        /**
         * The text with the highlights replaced by '%s'.
         */
        var format: String? = replaceStringHighlightsWithSymbol(originalText)

        /**
         * An array of the highlighted parts of the string.
         */
        var highlights: Array<String> = getTextMatches(originalText, highlightPattern)
    }
}