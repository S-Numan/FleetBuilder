package fleetBuilder.features.filters.filterPanels

import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.ui.trade.CargoItemStack
import com.fs.starfarer.campaign.ui.trade.CargoStackView
import com.fs.starfarer.campaign.util.CollectionView
import fleetBuilder.core.FBMisc.getSpecialItemName
import fleetBuilder.core.FBTxt
import fleetBuilder.otherMods.starficz.ReflectionUtils.set
import fleetBuilder.util.api.CargoUtils
import fleetBuilder.util.kotlin.safeGet
import fleetBuilder.util.kotlin.safeInvoke

class CargoFilterPanel(
    width: Float,
    height: Float,
    cargoDisplay: UIPanelAPI
) : BaseFilterPanel(
    width = width,
    height = height,
    parent = cargoDisplay,
    defaultText = FBTxt.txt("ctrl_f_to_search")
) {
    var otherButtonTab: ButtonAPI? = null
    var cargoDataGridView: UIPanelAPI? = null
    var stackView: Any? = null

    init {
        otherButtonTab = cargoDisplay.safeInvoke("getSpecial") as? ButtonAPI
        cargoDataGridView = cargoDisplay.safeInvoke("getCargoDataView") as? UIPanelAPI?
        stackView = cargoDataGridView?.safeGet("stackView")

        mainPanel.position.rightOfMid(otherButtonTab, 4f).setYAlignOffset(0f)
    }

    override fun onFilterChanged(text: String) {
        filterCargoList()
    }

    override fun onMiddleMouseReset() {
        super.onMiddleMouseReset()
        filterCargoList()
    }

    private fun filterCargoList() {
        if (textField.text.isBlank() || textField.text == defaultText) {
            refreshUI()
            return
        }

        refreshUI()


        val descriptions = parseSearchTokens(textField.text)

        @Suppress("UNCHECKED_CAST")
        val formerDelegate = stackView?.safeGet("delegate") as? CollectionView.CollectionViewDelegate<CargoStackView>
        if (formerDelegate != null) {
            val delegate = object : CollectionView.CollectionViewDelegate<CargoStackView> {
                override fun shouldCreateViewFor(item: Any): Boolean {
                    if (!formerDelegate.shouldCreateViewFor(item))
                        return false
                    item as CargoItemStack

                    //Custom behavior
                    descriptions.forEach { desc ->
                        if (desc.startsWith("-")) {
                            if (item.matchesDescription(desc.removePrefix("-")))
                                return false
                        } else if (!item.matchesDescription(desc)) {
                            return false
                        }
                    }
                    return true
                }

                override fun createItemView(item: Any): CargoStackView? {
                    return formerDelegate.createItemView(item)
                }
            }

            stackView?.set("delegate", value = delegate)
        }

        cargoDataGridView?.safeInvoke("clearChildren")
    }

    private fun refreshUI() {
        //val cargo = cargoDataGridView?.safeGet("cargo") as? CargoData
        //val cargoCopy = cargo?.createCopy()
        // cargoDataGridView?.safeInvoke("setCargo", cargo)
        //if (cargo != null) {
        //stackView?.safeInvoke("sync", cargo.stacks)
        //stackView?.safeInvoke("finishSync")
        //}
        //cargoDataGridView?.safeInvoke("advanceImpl", 1.0f)
    }

    private fun CargoStackAPI.matchesDescription(desc: String): Boolean {
        return when {
            displayName.lowercase().contains(desc) -> true
            specialDataIfSpecial?.getSpecialItemName()?.lowercase()?.startsWith(desc) == true -> true
            CargoUtils.getItemTech(this)?.lowercase()?.startsWith(desc) == true -> true
            "weapon".startsWith(desc) && isWeaponStack -> true
            ("fighter".startsWith(desc) || "wing".startsWith(desc)) && isFighterWingStack -> true
            "commodity".startsWith(desc) && isCommodityStack -> true
            ("special".startsWith(desc) || "other".startsWith(desc)) && isSpecialStack -> true
            else -> false
        }
    }
}
