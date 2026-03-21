package fleetBuilder.features.filters.filterPanels

import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.ui.UITable
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.util.safeInvoke

class ModPickerFilterPanel(
    width: Float,
    height: Float,
    private val parentPanel: UIPanelAPI,
    private val modPicker: UIPanelAPI
) : BaseFilterPanel(
    width = width,
    height = height,
    parent = parentPanel,
    defaultText = "Search for a modspec"
) {

    init {
        mainPanel.position.inTL(0f, 5f)
    }

    override fun onFilterChanged(text: String) {
        filterModPickerList()
    }

    override fun onMiddleMouseReset() {
        filterModPickerList()
    }

    private fun filterModPickerList() {
        if (textField.text.isBlank() || textField.text == defaultText) {
            refreshUI()
            return
        }

        refreshUI()

        val modPickerTable = modPicker.getChildrenCopy().find { it is UITable }
        val list = modPickerTable?.safeInvoke("getList")
        val items = list?.safeInvoke("getItems") as? List<Any?> ?: return

        val descriptions = parseSearchTokens(textField.text)

        descriptions.forEach { desc ->
            val toRemove = mutableListOf<Any?>()

            items.forEach { item ->
                val tooltip = item?.safeInvoke("getTooltip") ?: return@forEach
                val modSpecField = tooltip.getFieldsMatching(
                    fieldAssignableTo = HullModSpecAPI::class.java
                )
                val modSpec = modSpecField.getOrNull(0)?.get(tooltip) as? HullModSpecAPI
                    ?: return@forEach

                if (desc.startsWith("-")) {
                    if (modSpec.matchesDescription(desc.removePrefix("-")))
                        toRemove.add(item)
                } else if (!modSpec.matchesDescription(desc)) {
                    toRemove.add(item)
                }
            }

            toRemove.forEach { list.safeInvoke("removeItem", it) }
        }

        list.safeInvoke("collapseEmptySlots")
    }

    private fun refreshUI() {
        modPicker.safeInvoke("createUI")
        modPicker.safeInvoke("restoreTags")
        parentPanel.addComponent(mainPanel)
    }

    private fun HullModSpecAPI.matchesDescription(desc: String): Boolean {
        return when {
            displayName.lowercase().contains(desc) -> true
            manufacturer.lowercase().startsWith(desc) -> true
            else -> false
        }
    }
}
