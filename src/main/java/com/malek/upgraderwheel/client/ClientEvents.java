package com.malek.upgraderwheel.client;

import com.malek.upgraderwheel.UpgraderWheel;
import com.malek.upgraderwheel.item.ModItems;
import com.malek.upgraderwheel.logic.WheelAccess;
import com.malek.upgraderwheel.network.Network;
import com.malek.upgraderwheel.network.PacketOpenWheel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = UpgraderWheel.MODID)
public final class ClientEvents {

    /** Opens the wheel; also closes it when the search box is not being typed in. */
    public static final KeyBinding OPEN_KEY = new KeyBinding(WheelAccess.KEY_NAME, KeyConflictContext.IN_GAME,
            Keyboard.KEY_U, "key.categories.upgraderwheel");

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        while (OPEN_KEY.isPressed()) {
            if (mc.player != null && mc.currentScreen == null) {
                Network.CHANNEL.sendToServer(new PacketOpenWheel());
            }
        }
    }

    /** While the wheel is open, item tooltips show what each listed item is worth. */
    @SubscribeEvent
    public static void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiUpgrader) || event.getItemStack().isEmpty()) {
            return;
        }
        ClientCatalog.Entry entry = ClientCatalog.find(event.getItemStack());
        if (entry != null && !event.getToolTip().isEmpty()) {
            String line = net.minecraft.util.text.TextFormatting.GRAY + net.minecraft.client.resources.I18n.format(
                    "gui.upgraderwheel.value_each", GuiDraw.value(entry.value));
            if (!event.getToolTip().contains(line)) {
                event.getToolTip().add(line);
            }
        }
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ModItems.UPGRADER, 0,
                new ModelResourceLocation(ModItems.UPGRADER.getRegistryName(), "inventory"));
    }
}
