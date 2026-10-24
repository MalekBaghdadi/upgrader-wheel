package com.malek.upgraderwheel;

import com.malek.upgraderwheel.command.CommandUpgrader;
import com.malek.upgraderwheel.inventory.GuiHandler;
import com.malek.upgraderwheel.logic.UpgradeService;
import com.malek.upgraderwheel.network.Network;
import com.malek.upgraderwheel.value.ValueRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Upgrader Wheel: put an item on the wheel, pick a more valuable one, and spin.
 * The odds are (1 - house edge) x your item's value / the result's value.
 */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class UpgraderWheel {

    public static final String MODID = Tags.MOD_ID;
    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @Mod.Instance(Tags.MOD_ID)
    public static UpgraderWheel instance;

    @SidedProxy(clientSide = "com.malek.upgraderwheel.client.ClientProxy", serverSide = "com.malek.upgraderwheel.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.init(event.getSuggestedConfigurationFile());
        Network.register();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandUpgrader());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        // Every recipe (CraftTweaker and friends included) is in place by now
        ValueRegistry.rebuild();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ValueRegistry.clear();
        UpgradeService.clearSessionState();
    }
}
