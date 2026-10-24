package com.malek.upgraderwheel.network;

import com.malek.upgraderwheel.logic.WheelAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the player pressed the Upgrader key. The server decides whether they may open the wheel.
 */
public class PacketOpenWheel implements IMessage {

    public PacketOpenWheel() {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketOpenWheel, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenWheel message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> WheelAccess.openFromKey(player));
            return null;
        }
    }
}
