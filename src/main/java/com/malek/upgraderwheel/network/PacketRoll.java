package com.malek.upgraderwheel.network;

import com.malek.upgraderwheel.logic.UpgradeService;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: spin for this result. The server re-checks everything and rolls.
 */
public class PacketRoll implements IMessage {

    public int windowId;
    public int itemId;
    public int meta;
    public int amount;

    public PacketRoll() {
    }

    public PacketRoll(int windowId, int itemId, int meta, int amount) {
        this.windowId = windowId;
        this.itemId = itemId;
        this.meta = meta;
        this.amount = amount;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeInt(itemId);
        buf.writeShort(meta);
        buf.writeShort(amount);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        windowId = buf.readInt();
        itemId = buf.readInt();
        meta = buf.readUnsignedShort();
        amount = buf.readUnsignedShort();
    }

    public static class Handler implements IMessageHandler<PacketRoll, IMessage> {
        @Override
        public IMessage onMessage(PacketRoll message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() ->
                    UpgradeService.handleRoll(player, message.windowId, message.itemId, message.meta, message.amount));
            return null;
        }
    }
}
