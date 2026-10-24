package com.malek.upgraderwheel.network;

import com.malek.upgraderwheel.logic.UpgradeService;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: "my copy of the result list is missing or out of date, send it".
 */
public class PacketRequestCatalog implements IMessage {

    public PacketRequestCatalog() {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketRequestCatalog, IMessage> {
        @Override
        public IMessage onMessage(PacketRequestCatalog message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> UpgradeService.sendCatalog(player));
            return null;
        }
    }
}
