package SN.TestMod;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import SN.TestMod.TestClass;
import com.fs.starfarer.api.ModPlugin;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;


import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

public class TestingModPlugin extends BaseModPlugin {

    //public static TestClass testClass;
    static SaveTimer saveTimer;
    static TestClass testClass;
    static MoreLoadouts moreLoadouts;

    @Override
    public void onApplicationLoad() throws Exception {
        super.onApplicationLoad();

        saveTimer = new SaveTimer();
        testClass = new TestClass();
        moreLoadouts = new MoreLoadouts();

        //Global.getLogger(this.getClass()).info("TestingMod has been loaded");
    }

    @Override
    public void onNewGame() {
        super.onNewGame();
        // Add your code here, or delete this method (it does nothing unless you add code)
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        ListenerManagerAPI listeners = Global.getSector().getListenerManager();

        saveTimer.resetTimeSinceSave();
        Global.getSector().addTransientScript(saveTimer);
        Global.getSector().addTransientListener(saveTimer);
        listeners.addListener(saveTimer,true);

        Global.getSector().addTransientScript(testClass);


        Global.getSector().getListenerManager().addListener(moreLoadouts,true);

        //
    }

    @Override
    public void beforeGameSave() {
        super.beforeGameSave();
    }

    @Override
    public void afterGameSave() {
        super.afterGameSave();

        saveTimer.resetTimeSinceSave();

    }


    // You can add more methods from ModPlugin here. Press Control-O in Intel
}