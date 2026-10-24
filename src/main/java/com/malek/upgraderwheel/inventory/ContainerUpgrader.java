package com.malek.upgraderwheel.inventory;

import com.malek.upgraderwheel.logic.UpgradeService;
import com.malek.upgraderwheel.network.Network;
import com.malek.upgraderwheel.network.PacketBetValue;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * One bet slot plus the player's inventory. The bet slot is not saved anywhere: whatever is in it
 * goes back to the player when the screen closes.
 */
public class ContainerUpgrader extends Container {

    // Layout shared with the GUI (pixels from the top-left corner of the window)
    public static final int BET_X = 20;
    public static final int BET_Y = 34;
    public static final int INVENTORY_TOP = 108;

    private final InventoryBasic betInventory = new InventoryBasic("Upgrader", false, 1);
    private final EntityPlayer player;
    private final SlotBet betSlot;
    private ItemStack lastSentBet = ItemStack.EMPTY;
    private boolean betSent;

    public ContainerUpgrader(InventoryPlayer playerInventory, EntityPlayer player) {
        this.player = player;
        this.betSlot = new SlotBet(betInventory, 0, BET_X, BET_Y);
        addSlotToContainer(betSlot);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_TOP + 58));
        }
    }

    public SlotBet getBetSlot() {
        return betSlot;
    }

    public ItemStack getBet() {
        return betInventory.getStackInSlot(0);
    }

    public void setBet(ItemStack stack) {
        betInventory.setInventorySlotContents(0, stack);
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return playerIn.isEntityAlive();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (player instanceof EntityPlayerMP) {
            ItemStack now = getBet();
            if (!betSent || !ItemStack.areItemStacksEqual(now, lastSentBet)) {
                betSent = true;
                lastSentBet = now.copy();
                UpgradeService.BetInfo info = UpgradeService.evaluateBet(now);
                Network.CHANNEL.sendTo(new PacketBetValue(windowId, info.status, info.value), (EntityPlayerMP) player);
            }
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return result;
        }
        ItemStack stack = slot.getStack();
        result = stack.copy();
        if (index == 0) {
            if (!mergeItemStack(stack, 1, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!mergeItemStack(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(playerIn, stack);
        return result;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        if (playerIn.world.isRemote) {
            return;
        }
        ItemStack stack = betInventory.removeStackFromSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        // A player who is logging out is already saved and marked dead: drop it instead of losing it
        if (!playerIn.isEntityAlive() || !playerIn.inventory.addItemStackToInventory(stack)) {
            if (!stack.isEmpty()) {
                playerIn.dropItem(stack, false);
            }
        }
    }
}
