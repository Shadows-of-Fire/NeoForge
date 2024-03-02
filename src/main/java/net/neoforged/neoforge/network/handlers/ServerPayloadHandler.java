/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.network.handlers;

import net.neoforged.neoforge.network.configuration.SyncRegistries;
import net.neoforged.neoforge.network.configuration.SyncTierSortingRegistry;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.payload.FrozenRegistrySyncCompletedPayload;
import net.neoforged.neoforge.network.payload.TierSortingRegistrySyncCompletePayload;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ServerPayloadHandler {
    private static final ServerPayloadHandler INSTANCE = new ServerPayloadHandler();

    public static ServerPayloadHandler getInstance() {
        return INSTANCE;
    }

    private ServerPayloadHandler() {}

    public void handle(FrozenRegistrySyncCompletedPayload payload, IPayloadContext context) {
        context.finishCurrentTask(SyncRegistries.TYPE);
    }

    public void handle(TierSortingRegistrySyncCompletePayload payload, IPayloadContext context) {
        context.finishCurrentTask(SyncTierSortingRegistry.TYPE);
    }
}
