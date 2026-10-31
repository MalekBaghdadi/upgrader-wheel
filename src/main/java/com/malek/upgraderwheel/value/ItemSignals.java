package com.malek.upgraderwheel.value;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.IRarity;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Set;

/**
 * What the game itself says about an item: armor points, damage, durability, tool level, food, burn time, rarity.
 * These are independent of recipes, so they can check recipe values (a sword with diamond damage made of
 * "32-value" scraps means the scraps are mispriced) and price gear that no recipe makes.
 * Every getter is defensive: mods do odd things when asked about stats outside a world.
 */
public final class ItemSignals {

    public final String kind;
    public final double armor;
    public final double toughness;
    public final double damage;
    public final double speed;
    public final int durability;
    public final int harvest;
    public final int food;
    public final double saturation;
    public final int burn;
    public final String rarity;
    public final int stack;

    private ItemSignals(String kind, double armor, double toughness, double damage, double speed, int durability, int harvest,
                        int food, double saturation, int burn, String rarity, int stack) {
        this.kind = kind;
        this.armor = armor;
        this.toughness = toughness;
        this.damage = damage;
        this.speed = speed;
        this.durability = durability;
        this.harvest = harvest;
        this.food = food;
        this.saturation = saturation;
        this.burn = burn;
        this.rarity = rarity;
        this.stack = stack;
    }

    @Nullable
    private static Class<?> baubleClass;
    private static boolean baubleLooked;

    @Nullable
    private static Class<?> bauble() {
        if (!baubleLooked) {
            baubleLooked = true;
            try {
                baubleClass = Class.forName("baubles.api.IBauble", false, ItemSignals.class.getClassLoader());
            } catch (Throwable ignored) {
                baubleClass = null;
            }
        }
        return baubleClass;
    }

    public static boolean isBauble(Item item) {
        Class<?> c = bauble();
        return c != null && c.isInstance(item);
    }

    public static ItemSignals of(ItemStack stack) {
        Item item = stack.getItem();
        EntityEquipmentSlot armorSlot = armorSlot(stack);
        double armor = 0;
        double toughness = 0;
        if (armorSlot != null) {
            armor = attribute(stack, armorSlot, SharedMonsterAttributes.ARMOR.getName());
            toughness = attribute(stack, armorSlot, SharedMonsterAttributes.ARMOR_TOUGHNESS.getName());
            if (armor <= 0 && item instanceof ItemArmor) {
                armor = ((ItemArmor) item).damageReduceAmount;
                toughness = ((ItemArmor) item).toughness;
            }
        }
        double damage = attribute(stack, EntityEquipmentSlot.MAINHAND, SharedMonsterAttributes.ATTACK_DAMAGE.getName());
        double speed = attribute(stack, EntityEquipmentSlot.MAINHAND, SharedMonsterAttributes.ATTACK_SPEED.getName());
        int durability = 0;
        try {
            if (stack.isItemStackDamageable()) {
                durability = Math.max(0, stack.getMaxDamage());
            }
        } catch (Throwable ignored) {
            // Keep 0
        }
        int harvest = -1;
        try {
            Set<String> classes = item.getToolClasses(stack);
            for (String toolClass : classes) {
                harvest = Math.max(harvest, item.getHarvestLevel(stack, toolClass, null, null));
            }
        } catch (Throwable ignored) {
            // Not a tool, or a tool that needs a player
        }
        int food = 0;
        double saturation = 0;
        if (item instanceof ItemFood) {
            try {
                food = ((ItemFood) item).getHealAmount(stack);
                saturation = ((ItemFood) item).getSaturationModifier(stack);
            } catch (Throwable ignored) {
                // Keep 0
            }
        }
        int burn = 0;
        try {
            burn = Math.max(0, TileEntityFurnace.getItemBurnTime(stack));
        } catch (Throwable ignored) {
            // Keep 0
        }
        String rarity = "common";
        try {
            IRarity r = item.getForgeRarity(stack);
            if (r != null) {
                rarity = r.getName().toLowerCase(Locale.ROOT).replace(' ', '_');
            }
        } catch (Throwable ignored) {
            // Client-only rarity code
        }
        int max = 64;
        try {
            max = stack.getMaxStackSize();
        } catch (Throwable ignored) {
            // Keep 64
        }

        String kind;
        if (ValueRegistry.potionForm(item) >= 0) {
            kind = "potion";
        } else if (armorSlot != null && (armor > 0 || toughness > 0)) {
            kind = "armor_" + armorSlot.getName();
        } else if (isBauble(item)) {
            kind = "bauble";
        } else if (item instanceof ItemBow) {
            kind = "bow";
        } else if (isShield(stack)) {
            kind = "shield";
        } else if (harvest >= 0 && durability > 0) {
            kind = "tool";
        } else if ((item instanceof ItemSword || damage > 0) && durability > 0) {
            kind = "weapon";
        } else if (item instanceof ItemFood) {
            kind = "food";
        } else if (item instanceof ItemBlock) {
            kind = "block";
        } else if (durability > 0) {
            kind = "equipment";
        } else {
            kind = "item";
        }
        return new ItemSignals(kind, armor, toughness, damage, speed, durability, harvest, food, saturation, burn, rarity, max);
    }

    @Nullable
    private static EntityEquipmentSlot armorSlot(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemArmor) {
            return ((ItemArmor) item).armorType;
        }
        try {
            EntityEquipmentSlot slot = item.getEquipmentSlot(stack);
            if (slot != null && slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR) {
                return slot;
            }
        } catch (Throwable ignored) {
            // Fall through
        }
        return null;
    }

    private static boolean isShield(ItemStack stack) {
        try {
            return stack.getItem().isShield(stack, null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Sum of the flat (operation 0) modifiers an item gives for one attribute in one slot. */
    static double attribute(ItemStack stack, EntityEquipmentSlot slot, String name) {
        double total = 0;
        try {
            for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(name)) {
                if (modifier.getOperation() == 0) {
                    total += modifier.getAmount();
                }
            }
        } catch (Throwable ignored) {
            // Modded attribute code that needs a player or a world
        }
        return total;
    }

    /** CSV header for the columns {@link #csv()} writes. */
    static final String CSV_HEADER = "kind,armor,toughness,damage,speed,durability,harvest,food,saturation,burn,rarity,stack";

    String csv() {
        return kind + "," + num(armor) + "," + num(toughness) + "," + num(damage) + "," + num(speed) + "," + durability + ","
                + (harvest < 0 ? "" : Integer.toString(harvest)) + "," + food + "," + num(saturation) + "," + burn + "," + rarity + "," + stack;
    }

    private static String num(double v) {
        if (v == Math.rint(v)) {
            return Long.toString((long) v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
