package com.malek.upgraderwheel.network;

import com.malek.upgraderwheel.UpgraderWheel;
import com.malek.upgraderwheel.logic.Codes;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: what the item in the bet slot is worth (the server is the only one who prices bets).
 */
public class PacketBetValue implements IMessage {

    public int windowId;
    public Codes.Bet status = Codes.Bet.NOT_READY;
    public double value;

    public PacketBetValue() {
    }

    public PacketBetValue(int windowId, Codes.Bet status, double value) {
        this.windowId = windowId;
        this.status = status;
        this.value = value;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeByte(status.ordinal());
        buf.writeDouble(value);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        windowId = buf.readInt();
        status = Codes.Bet.byId(buf.readByte());
        value = buf.readDouble();
    }

    public static class Handler implements IMessageHandler<PacketBetValue, IMessage> {
        @Override
        public IMessage onMessage(PacketBetValue message, MessageContext ctx) {
            UpgraderWheel.proxy.handleBetValue(message);
            return null;
        }
    }
}
