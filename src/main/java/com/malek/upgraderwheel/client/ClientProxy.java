package com.malek.upgraderwheel.client;

import com.malek.upgraderwheel.CommonProxy;
import com.malek.upgraderwheel.inventory.ContainerUpgrader;
import com.malek.upgraderwheel.network.PacketBetValue;
import com.malek.upgraderwheel.network.PacketCatalog;
import com.malek.upgraderwheel.network.PacketRollResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(ClientEvents.OPEN_KEY);
    }

    @Override
    public Object createClientGui(ContainerUpgrader container, int catalogVersion) {
        return new GuiUpgrader(container, catalogVersion);
    }

    @Override
    public void handleCatalog(PacketCatalog message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (ClientCatalog.accept(message)) {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiUpgrader) {
                    ((GuiUpgrader) screen).onCatalogReady();
                }
            }
        });
    }

    @Override
    public void handleBetValue(PacketBetValue message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientState.acceptBetValue(message));
    }

    @Override
    public void handleRollResult(PacketRollResult message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen instanceof GuiUpgrader) {
                ((GuiUpgrader) screen).onRollResult(message);
            }
        });
    }
}
