package fleetBuilder.autofit

import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.plugins.AutofitPlugin

class MLAvailableWeapon(private val id: String,
                        private val weaponspec: WeaponSpecAPI,
                        private val source: CargoAPI,
                        private val submarket: SubmarketAPI?
                    ): AutofitPlugin.AvailableWeapon {


    override fun getId(): String {
        return id
    }

    override fun getSource(): CargoAPI {
        return source
    }

    override fun getQuantity(): Int {
        return source.getNumWeapons(id)
    }

    override fun setQuantity(x: Int) {
        if(x > quantity) {
            source.addWeapons(id, x - quantity)
        } else if(x < quantity) {
            source.removeWeapons(id, quantity - x)
        } else {
            return
        }
    }


    override fun getSpec(): WeaponSpecAPI {
        return weaponspec
    }

    override fun getOPCost(stats: MutableCharacterStatsAPI?, shipStats: MutableShipStatsAPI?): Float {
        return weaponspec.getOrdnancePointCost(stats, shipStats)
    }


    override fun getSubmarket(): SubmarketAPI? {
        return submarket
    }
    override fun getPrice(): Float {
        if(submarket != null) {
            return weaponspec.baseValue * (1 + submarket.tariff)
        } else {
            return weaponspec.baseValue
        }
    }
}