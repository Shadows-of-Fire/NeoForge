package net.neoforged.neoforge.resource;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;

/**
 * Keys for Neo-added resource listeners, for use in dependency ordering in the relevant events.
 * 
 * @see {@link VanillaClientListeners} for vanilla client listener names.
 * @see {@link VanillaServerListeners} for vanilla server listener names.
 */
public class NeoListenerNames {

    // Server Listeners

    public static final ResourceLocation LOOT_MODIFIERS = key("loot_modifiers");

    public static final ResourceLocation DATA_MAPS = key("data_maps");

    // Client Listeners

    public static final ResourceLocation OBJ_LOADER = key("obj_loader");

    public static final ResourceLocation ENTITY_ANIMATIONS = key("entity_animations");

    private static ResourceLocation key(String path) {
        return ResourceLocation.fromNamespaceAndPath(NeoForgeVersion.MOD_ID, path);
    }
}
