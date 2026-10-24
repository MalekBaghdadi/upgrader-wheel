package com.malek.upgraderwheel.item;

import com.google.gson.JsonObject;
import com.malek.upgraderwheel.ModConfig;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.function.BooleanSupplier;

/** Recipe condition "upgraderwheel:item_mode": the Upgrader recipe only exists when the config asks for the item. */
public class ItemModeCondition implements IConditionFactory {

    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        return () -> ModConfig.needItem;
    }
}
