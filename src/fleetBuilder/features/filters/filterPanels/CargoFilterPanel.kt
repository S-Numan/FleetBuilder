package fleetBuilder.features.filters.filterPanels

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.ui.trade.CargoItemStack
import com.fs.starfarer.campaign.ui.trade.CargoStackView
import com.fs.starfarer.campaign.util.CollectionView
import fleetBuilder.util.safeGet
import fleetBuilder.util.safeInvoke
import starficz.ReflectionUtils.set

class CargoFilterPanel(
    width: Float,
    height: Float,
    cargoDisplay: UIPanelAPI
) : BaseFilterPanel(
    width = width,
    height = height,
    parent = cargoDisplay,
    defaultText = "Search"
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
            displayName.lowercase().startsWith(desc) -> true
            specialDataIfSpecial?.getSpecialItemName()?.lowercase()?.startsWith(desc) == true -> true
            getItemTech()?.lowercase()?.startsWith(desc) == true -> true
            "weapon".startsWith(desc) && isWeaponStack -> true
            ("fighter".startsWith(desc) || "wing".startsWith(desc)) && isFighterWingStack -> true
            "commodity".startsWith(desc) && isCommodityStack -> true
            ("special".startsWith(desc) || "other".startsWith(desc)) && isSpecialStack -> true
            else -> false
        }
    }

    private fun SpecialItemData.getSpecialItemName(): String? {
        return when (id) {
            "fighter_bp" ->
                Global.getSettings().allFighterWingSpecs.find { it.id == data }?.wingName
            "weapon_bp" ->
                Global.getSettings().allWeaponSpecs.find { it.weaponId == data }?.weaponName
            "ship_bp" ->
                Global.getSettings().allShipHullSpecs.find { it.hullId == data }?.hullName
            "modspec" ->
                Global.getSettings().allHullModSpecs.find { it.id == data }?.displayName
            "industry_bp" ->
                Global.getSettings().allIndustrySpecs.find { it.id == data }?.name

            else -> null
        }
    }

    private fun SpecialItemData.getSpecialItemTech(): String? {
        return when (id) {
            "fighter_bp" ->
                Global.getSettings().allFighterWingSpecs.find { it.id == data }?.variant?.hullSpec?.manufacturer
            "weapon_bp" ->
                Global.getSettings().allWeaponSpecs.find { it.weaponId == data }?.manufacturer
            "ship_bp" ->
                Global.getSettings().allShipHullSpecs.find { it.hullId == data }?.manufacturer
            "modspec" ->
                Global.getSettings().allHullModSpecs.find { it.id == data }?.manufacturer

            else -> null
        }
    }

    private fun CargoStackAPI.getItemTech(): String? {
        return when {
            weaponSpecIfWeapon != null -> weaponSpecIfWeapon.manufacturer
            fighterWingSpecIfWing != null -> fighterWingSpecIfWing.variant.hullSpec.manufacturer
            hullModSpecIfHullMod != null -> hullModSpecIfHullMod.manufacturer
            specialItemSpecIfSpecial != null -> specialItemSpecIfSpecial.manufacturer
            specialDataIfSpecial != null -> specialDataIfSpecial.getSpecialItemTech()
            else -> return null
        }
    }
}
