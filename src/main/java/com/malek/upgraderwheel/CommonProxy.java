package com.malek.upgraderwheel;

import com.malek.upgraderwheel.inventory.ContainerUpgrader;
import com.malek.upgraderwheel.network.PacketBetValue;
import com.malek.upgraderwheel.network.PacketCatalog;
import com.malek.upgraderwheel.network.PacketRollResult;

/**
 * Server side (and shared) proxy. Everything that touches client classes lives in the client proxy,
 * so a dedicated server never loads them.
 */
public class CommonProxy {

    public void preInit() {
    }

    /** The client GUI for the upgrader container; null on the server. */
    public Object createClientGui(ContainerUpgrader container, int catalogVersion) {
        return null;
    }

    public void handleCatalog(PacketCatalog message) {
    }

    public void handleBetValue(PacketBetValue message) {
    }

    public void handleRollResult(PacketRollResult message) {
    }
}
