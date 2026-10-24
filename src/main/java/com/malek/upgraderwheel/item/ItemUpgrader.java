package com.malek.upgraderwheel.item;

import com.malek.upgraderwheel.UpgraderWheel;
import com.malek.upgraderwheel.logic.WheelAccess;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Right-click to open the upgrade wheel. Not used up.
 */
public class ItemUpgrader extends Item {

    public ItemUpgrader() {
        setRegistryName(UpgraderWheel.MODID, "upgrader");
        setTranslationKey(UpgraderWheel.MODID + ".upgrader");
        setCreativeTab(CreativeTabs.TOOLS);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!world.isRemote) {
            WheelAccess.open(player);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("item.upgraderwheel.upgrader.tooltip"));
    }
}
