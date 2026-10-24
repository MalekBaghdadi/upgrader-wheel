package com.malek.upgraderwheel.network;

import com.malek.upgraderwheel.UpgraderWheel;
import com.malek.upgraderwheel.logic.Codes;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: the outcome of a spin (or why it was refused) and the chance that was used.
 */
public class PacketRollResult implements IMessage {

    public int windowId;
    public Codes.Roll code = Codes.Roll.NOT_READY;
    public double chance;

    public PacketRollResult() {
    }

    public PacketRollResult(int windowId, Codes.Roll code, double chance) {
        this.windowId = windowId;
        this.code = code;
        this.chance = chance;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeByte(code.ordinal());
        buf.writeDouble(chance);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        windowId = buf.readInt();
        code = Codes.Roll.byId(buf.readByte());
        chance = buf.readDouble();
    }

    public static class Handler implements IMessageHandler<PacketRollResult, IMessage> {
        @Override
        public IMessage onMessage(PacketRollResult message, MessageContext ctx) {
            UpgraderWheel.proxy.handleRollResult(message);
            return null;
        }
    }
}
