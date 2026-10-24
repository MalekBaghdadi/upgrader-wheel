package com.malek.upgraderwheel.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

/**
 * The bet slot. The client hides it while the wheel spins so the outcome is not spoiled
 * by the slot updating before the needle stops.
 */
public class SlotBet extends Slot {

    private boolean hidden;

    public SlotBet(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isHidden() {
        return hidden;
    }

    @Override
    public boolean isEnabled() {
        return !hidden;
    }
}
