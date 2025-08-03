package com.juejuecat.taste_fatigue;

import com.juejuecat.taste_fatigue.network.TFNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(TasteFatigue.MODID)
public class TasteFatigue {
    public static final String MODID = "taste_fatigue";

    public TasteFatigue(IEventBus modEventBus, ModContainer modContainer) {
        TasteFatigueConfig.load();
        modEventBus.addListener(TFNetwork::register);
        NeoForge.EVENT_BUS.register(new TasteFatigueEvents());
    }
}