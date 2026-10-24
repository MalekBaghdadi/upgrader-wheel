package com.malek.upgraderwheel.logic;

import com.malek.upgraderwheel.ModConfig;
import com.malek.upgraderwheel.UpgraderWheel;
import com.malek.upgraderwheel.inventory.GuiHandler;
import com.malek.upgraderwheel.item.ModItems;
import com.malek.upgraderwheel.value.ValueRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentKeybind;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Who may open the wheel, and how. By default (needItem = false) anyone can open it with the Upgrader key from the
 * start. With needItem = true it takes the Upgrader item: right-click it, or press the key while carrying one.
 */
@Mod.EventBusSubscriber(modid = UpgraderWheel.MODID)
public final class WheelAccess {

    public static final String KEY_NAME = "key.upgraderwheel.open";
    private static final String HINT_TAG = "upgraderwheel_key_hint";

    private WheelAccess() {
    }

    public static void open(EntityPlayer player) {
        ValueRegistry registry = ValueRegistry.getOrBuild();
        player.openGui(UpgraderWheel.instance, GuiHandler.UPGRADER, player.world, registry.version(), 0, 0);
    }

    public static void openFromKey(EntityPlayerMP player) {
        if (!player.isEntityAlive() || player.openContainer != player.inventoryContainer) {
            // Dead, or another screen with items is open
            return;
        }
        if (ModConfig.needItem && !carriesUpgrader(player)) {
            TextComponentTranslation text = new TextComponentTranslation("chat.upgraderwheel.need_item");
            text.getStyle().setColor(TextFormatting.RED);
            player.sendStatusMessage(text, true);
            return;
        }
        open(player);
    }

    private static boolean carriesUpgrader(EntityPlayer player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.UPGRADER) {
                return true;
            }
        }
        return false;
    }

    /** Tells each player once which key opens the wheel. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (ModConfig.needItem || player.world.isRemote) {
            return;
        }
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(HINT_TAG)) {
            return;
        }
        persisted.setBoolean(HINT_TAG, true);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        TextComponentTranslation text = new TextComponentTranslation("chat.upgraderwheel.key_hint", new TextComponentKeybind(KEY_NAME));
        text.getStyle().setColor(TextFormatting.GOLD);
        player.sendMessage(text);
    }
}
