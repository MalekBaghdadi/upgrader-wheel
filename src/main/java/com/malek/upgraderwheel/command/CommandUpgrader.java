package com.malek.upgraderwheel.command;

import com.malek.upgraderwheel.ModConfig;
import com.malek.upgraderwheel.value.ValueRegistry;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /upgrader value          value of the held item and where it comes from (anyone)
 * /upgrader set <value>    pin the held item's value in the config
 * /upgrader unset          remove that pin
 * /upgrader reload         re-read the config and re-price everything
 * /upgrader dump           write every value to config/upgraderwheel-values.csv
 * Everything but "value" needs op, or being the owner of a single-player world (no cheats needed).
 */
public class CommandUpgrader extends CommandBase {

    @Override
    public String getName() {
        return "upgrader";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/upgrader <value|set <value>|unset|reload|dump>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "value":
                showValue(sender);
                return;
            case "set":
                requireAdmin(server, sender);
                if (args.length < 2) {
                    throw new WrongUsageException("/upgrader set <value>   e.g. /upgrader set 2d  (2 diamonds)");
                }
                double value = ModConfig.parseValue(args[1]);
                if (Double.isNaN(value)) {
                    throw new CommandException("'" + args[1] + "' is not a value. Use a number, or 2d = 2 diamonds, 3i = 3 iron, 1g = 1 gold, 1e = 1 emerald.");
                }
                setValue(sender, value);
                return;
            case "unset":
                requireAdmin(server, sender);
                setValue(sender, null);
                return;
            case "reload":
                requireAdmin(server, sender);
                ModConfig.reload();
                ValueRegistry registry = ValueRegistry.rebuild();
                send(sender, TextFormatting.GREEN + "Upgrader reloaded: " + registry.summary());
                return;
            case "dump":
                requireAdmin(server, sender);
                dump(sender);
                return;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    /** Ops, and the owner of a single-player world even with cheats off (it is their own world). */
    private void requireAdmin(MinecraftServer server, ICommandSender sender) throws CommandException {
        if (sender.canUseCommand(2, getName())) {
            return;
        }
        Entity entity = sender.getCommandSenderEntity();
        if (server != null && server.isSinglePlayer() && entity instanceof EntityPlayerMP
                && entity.getName().equals(server.getServerOwner())) {
            return;
        }
        throw new CommandException("commands.generic.permission");
    }

    private static ItemStack heldStack(ICommandSender sender) throws CommandException {
        Entity entity = sender.getCommandSenderEntity();
        if (!(entity instanceof EntityPlayerMP)) {
            throw new CommandException("Only a player can use this (it reads the item in your main hand).");
        }
        ItemStack stack = ((EntityPlayerMP) entity).getHeldItemMainhand();
        if (stack.isEmpty()) {
            throw new CommandException("Hold an item in your main hand first.");
        }
        return stack;
    }

    private static String idOf(ItemStack stack) {
        String id = String.valueOf(stack.getItem().getRegistryName());
        return stack.getHasSubtypes() ? id + ":" + stack.getMetadata() : id;
    }

    private void showValue(ICommandSender sender) throws CommandException {
        ItemStack stack = heldStack(sender);
        ValueRegistry registry = ValueRegistry.getOrBuild();
        ValueRegistry.Explanation explanation = registry.explain(stack);
        send(sender, TextFormatting.GOLD + stack.getDisplayName() + TextFormatting.GRAY + " (" + idOf(stack) + ")");
        send(sender, TextFormatting.GRAY + "Value each: " + TextFormatting.WHITE + ModConfig.formatNumber(ValueRegistry.roundValue(explanation.unitValue))
                + TextFormatting.GRAY + " from " + (explanation.source == null ? "unknown" : explanation.source.description));
        if (explanation.recipe != null) {
            send(sender, TextFormatting.GRAY + "Recipe: " + TextFormatting.WHITE + explanation.recipe);
        }
        double stackValue = registry.stackValue(stack);
        if (Math.abs(stackValue - explanation.unitValue) > 1.0E-6) {
            send(sender, TextFormatting.GRAY + "This stack as a bet (count, durability, enchantments): " + TextFormatting.WHITE
                    + ModConfig.formatNumber(ValueRegistry.roundValue(stackValue)));
        }
        send(sender, TextFormatting.GRAY + (explanation.listed ? "Can be won." : "Not offered as a result (no creative tab, NBT, blacklisted or below minTargetValue)."));
    }

    private void setValue(ICommandSender sender, @Nullable Double value) throws CommandException {
        ItemStack stack = heldStack(sender);
        String id = idOf(stack);
        boolean removed = ModConfig.setOverride(id, value);
        ValueRegistry registry = ValueRegistry.rebuild();
        if (value == null) {
            send(sender, removed ? TextFormatting.GREEN + "Removed the override for " + id + ". " + registry.summary()
                    : TextFormatting.YELLOW + "There was no override for " + id + ".");
        } else {
            send(sender, TextFormatting.GREEN + "Set " + id + " = " + ModConfig.formatNumber(value) + " in the config and re-priced everything.");
        }
    }

    private void dump(ICommandSender sender) throws CommandException {
        try {
            File file = ValueRegistry.getOrBuild().writeCsv();
            send(sender, TextFormatting.GREEN + "Wrote " + file.getPath());
        } catch (IOException e) {
            throw new CommandException("Could not write the values file: " + e.getMessage());
        }
    }

    private static void send(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "value", "set", "unset", "reload", "dump");
        }
        return Collections.emptyList();
    }
}
