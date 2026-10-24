package com.malek.upgraderwheel.network;

import com.malek.upgraderwheel.UpgraderWheel;
import com.malek.upgraderwheel.value.ValueRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/**
 * Server to client: one chunk of the result list (item, potion type, value, max stack) plus the odds settings.
 */
public class PacketCatalog implements IMessage {

    public int version;
    public int chunk;
    public int chunkCount;
    public double houseEdge;
    public double minChance;
    public double maxChance;
    public int[] itemIds = new int[0];
    public short[] metas = new short[0];
    public double[] values = new double[0];
    public byte[] maxStacks = new byte[0];
    /** Potion type registry name per entry, "" for items that are not potions. */
    public String[] potions = new String[0];

    public PacketCatalog() {
    }

    public PacketCatalog(int version, int chunk, int chunkCount, double houseEdge, double minChance, double maxChance,
                         List<ValueRegistry.CatalogEntry> entries) {
        this.version = version;
        this.chunk = chunk;
        this.chunkCount = chunkCount;
        this.houseEdge = houseEdge;
        this.minChance = minChance;
        this.maxChance = maxChance;
        int n = entries.size();
        this.itemIds = new int[n];
        this.metas = new short[n];
        this.values = new double[n];
        this.maxStacks = new byte[n];
        this.potions = new String[n];
        for (int i = 0; i < n; i++) {
            ValueRegistry.CatalogEntry entry = entries.get(i);
            itemIds[i] = Item.getIdFromItem(entry.item);
            metas[i] = (short) entry.meta;
            values[i] = entry.value;
            maxStacks[i] = (byte) entry.maxStack;
            potions[i] = entry.potionId();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(version);
        buf.writeInt(chunk);
        buf.writeInt(chunkCount);
        buf.writeDouble(houseEdge);
        buf.writeDouble(minChance);
        buf.writeDouble(maxChance);
        buf.writeInt(itemIds.length);
        for (int i = 0; i < itemIds.length; i++) {
            buf.writeInt(itemIds[i]);
            buf.writeShort(metas[i]);
            buf.writeDouble(values[i]);
            buf.writeByte(maxStacks[i]);
            ByteBufUtils.writeUTF8String(buf, potions[i] == null ? "" : potions[i]);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        version = buf.readInt();
        chunk = buf.readInt();
        chunkCount = buf.readInt();
        houseEdge = buf.readDouble();
        minChance = buf.readDouble();
        maxChance = buf.readDouble();
        int n = Math.max(0, Math.min(buf.readInt(), 100000));
        itemIds = new int[n];
        metas = new short[n];
        values = new double[n];
        maxStacks = new byte[n];
        potions = new String[n];
        for (int i = 0; i < n; i++) {
            itemIds[i] = buf.readInt();
            metas[i] = buf.readShort();
            values[i] = buf.readDouble();
            maxStacks[i] = buf.readByte();
            potions[i] = ByteBufUtils.readUTF8String(buf);
        }
    }

    public static class Handler implements IMessageHandler<PacketCatalog, IMessage> {
        @Override
        public IMessage onMessage(PacketCatalog message, MessageContext ctx) {
            UpgraderWheel.proxy.handleCatalog(message);
            return null;
        }
    }
}
