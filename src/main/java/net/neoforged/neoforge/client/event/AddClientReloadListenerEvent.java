/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.event.SortedReloadListenerEvent;
import net.neoforged.neoforge.resource.VanillaClientListeners;
import org.jetbrains.annotations.ApiStatus;

/**
 * This event allows mods to register client-side reload listeners to the resource manager.
 * This event is fired once during the construction of the {@link Minecraft} instance.
 * <p>
 * This event is only fired on the {@linkplain LogicalSide#CLIENT logical client}.
 * 
 * @see {@link AddReloadListenerEvent} for registering server-side reload listeners.
 */
public class AddClientReloadListenerEvent extends SortedReloadListenerEvent implements IModBusEvent {
    @ApiStatus.Internal
    public AddClientReloadListenerEvent(ReloadableResourceManager resourceManager) {
        super(resourceManager.getListeners(), AddClientReloadListenerEvent::lookupName);
    }

    private static ResourceLocation lookupName(PreparableReloadListener listener) {
        ResourceLocation key = VanillaClientListeners.getNameForClass(listener.getClass());
        if (key == null) {
            if (listener.getClass().getPackageName().startsWith("net.minecraft")) {
                throw new IllegalArgumentException("A key for the reload listener " + listener + " was not provided in VanillaClientListeners!");
            } else {
                throw new IllegalArgumentException("A non-vanilla reload listener " + listener + " was added via mixin before the AddClientReloadListenerEvent! Mod-added listeners must go through the event.");
            }
        }
        return key;
    }
}
