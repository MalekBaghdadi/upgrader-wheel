package com.malek.upgraderwheel.item;

import com.malek.upgraderwheel.UpgraderWheel;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = UpgraderWheel.MODID)
public final class ModItems {

    public static final ItemUpgrader UPGRADER = new ItemUpgrader();

    private ModItems() {
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(UPGRADER);
    }
}
