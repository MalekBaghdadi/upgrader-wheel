package com.malek.upgraderwheel.network;

import com.malek.upgraderwheel.UpgraderWheel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class Network {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(UpgraderWheel.MODID);

    private Network() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(PacketCatalog.Handler.class, PacketCatalog.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketBetValue.Handler.class, PacketBetValue.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketRollResult.Handler.class, PacketRollResult.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketRequestCatalog.Handler.class, PacketRequestCatalog.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketRoll.Handler.class, PacketRoll.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketOpenWheel.Handler.class, PacketOpenWheel.class, id++, Side.SERVER);
    }
}
