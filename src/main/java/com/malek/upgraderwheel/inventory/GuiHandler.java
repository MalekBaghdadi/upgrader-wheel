package com.malek.upgraderwheel.inventory;

import com.malek.upgraderwheel.UpgraderWheel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

/**
 * The x coordinate carries the server's value-list version, so the client knows whether its copy is current.
 */
public class GuiHandler implements IGuiHandler {

    public static final int UPGRADER = 0;

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return id == UPGRADER ? new ContainerUpgrader(player.inventory, player) : null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != UPGRADER) {
            return null;
        }
        return UpgraderWheel.proxy.createClientGui(new ContainerUpgrader(player.inventory, player), x);
    }
}
