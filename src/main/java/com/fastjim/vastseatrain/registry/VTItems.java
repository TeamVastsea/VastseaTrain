package com.fastjim.vastseatrain.registry;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.item.TrackBlueprintItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class VTItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VastseaTrain.MOD_ID);

    public static final RegistryObject<Item> TRACK_BLUEPRINT = ITEMS.register("track_blueprint",
            () -> new TrackBlueprintItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> RAIL = ITEMS.register("rail",
            () -> new BlockItem(VTBlocks.RAIL.get(), new Item.Properties()));

    public static final RegistryObject<Item> RAIL_SLAB = ITEMS.register("rail_slab",
            () -> new BlockItem(VTBlocks.RAIL_SLAB.get(), new Item.Properties()));

    private VTItems() {
    }
}
