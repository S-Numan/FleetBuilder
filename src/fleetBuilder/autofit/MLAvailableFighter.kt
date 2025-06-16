package fleetBuilder.autofit

import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.plugins.AutofitPlugin

class MLAvailableFighter(private val id: String,
                         private val wingspec: FighterWingSpecAPI,
                         private val source: CargoAPI,
                         private val submarket: SubmarketAPI?
) : AutofitPlugin.AvailableFighter {

    override fun getId(): String {
        return id
    }

    override fun getSource(): CargoAPI {
        return source
    }

    override fun getQuantity(): Int {
        return source.getNumFighters(id)
    }

    override fun setQuantity(x: Int) {
        if(x > quantity) {
            source.addFighters(id, x - quantity)
        } else if(x < quantity) {
            source.removeFighters(id, quantity - x)
        } else {
            return
        }

    }


    override fun getWingSpec(): FighterWingSpecAPI {
        return wingspec
    }


    override fun getSubmarket(): SubmarketAPI? {
        return submarket
    }
    override fun getPrice(): Float {
        if(submarket != null) {
            return wingspec.baseValue * (1 + submarket.tariff)
        } else {
            return wingspec.baseValue
        }
    }
}