package com.malek.upgraderwheel.client;

import com.malek.upgraderwheel.logic.Codes;
import com.malek.upgraderwheel.network.PacketBetValue;

/**
 * Latest bet value the server sent, kept per window so a late packet never lands on the wrong screen.
 */
public final class ClientState {

    private static int betWindowId = -1;
    private static Codes.Bet betStatus = Codes.Bet.NOT_READY;
    private static double betValue;
    private static int betRevision;

    private ClientState() {
    }

    static void acceptBetValue(PacketBetValue message) {
        betWindowId = message.windowId;
        betStatus = message.status;
        betValue = message.value;
        betRevision++;
    }

    static boolean hasBet(int windowId) {
        return betWindowId == windowId;
    }

    static Codes.Bet betStatus(int windowId) {
        return betWindowId == windowId ? betStatus : Codes.Bet.NOT_READY;
    }

    static double betValue(int windowId) {
        return betWindowId == windowId ? betValue : 0;
    }

    /** Changes every time a new bet value arrives, so the screen can react once. */
    static int betRevision() {
        return betRevision;
    }
}
