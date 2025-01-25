/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.event;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.SortedReloadListenerEvent;
import net.neoforged.neoforge.resource.VanillaClientListeners;

/**
 * Fired to allow mods to register their reload listeners on the client-side resource manager.
 * This event is fired once during the construction of the {@link Minecraft} instance.
 *
 * <p>For registering reload listeners on the server-side resource manager, see {@link AddReloadListenerEvent}.</p>
 *
 * <p>This event is not {@linkplain ICancellableEvent cancellable}, and does not {@linkplain HasResult have a result}.</p>
 *
 * <p>This event is fired on the mod-specific event bus, only on the {@linkplain LogicalSide#CLIENT logical client}.</p>
 */
public class AddClientReloadListenerEvent extends SortedReloadListenerEvent implements IModBusEvent {

    @ApiStatus.Internal
    public AddClientReloadListenerEvent(ReloadableResourceManager resourceManager) {
        super(resourceManager.listeners, AddClientReloadListenerEvent::lookupName);
    }

    private static ResourceLocation lookupName(PreparableReloadListener listener) {
        ResourceLocation key = VanillaClientListeners.getNameForClass(listener.getClass());
        if (key == null) {
            if (listener.getClass().getPackageName().startsWith("net.minecraft")) {
                throw new IllegalArgumentException("A key for the reload listener " + listener + " was not provided in VanillaClientListeners!");
            } else {
                throw new IllegalArgumentException("A non-vanilla reload listener " + listener + " was added via mixin before the AddReloadListenerEvent! Mod-added listeners must go through the event.");
            }
        }
        return key;
    }
}
