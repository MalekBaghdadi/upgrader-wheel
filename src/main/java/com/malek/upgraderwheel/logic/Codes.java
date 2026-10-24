package com.malek.upgraderwheel.logic;

/**
 * Small status codes sent between server and client. The client turns them into translated text.
 */
public final class Codes {

    private Codes() {
    }

    /** Why a stack in the bet slot can or cannot be bet. */
    public enum Bet {
        OK, EMPTY, BLACKLISTED, HAS_CONTENTS, NO_VALUE, NOT_READY;

        public static Bet byId(int id) {
            Bet[] all = values();
            return id >= 0 && id < all.length ? all[id] : NOT_READY;
        }

        public String langKey() {
            return "gui.upgraderwheel.bet." + name().toLowerCase();
        }
    }

    /** Outcome of a spin request. */
    public enum Roll {
        WIN, LOSE, NO_BET, BET_NOT_ALLOWED, BAD_TARGET, ODDS_TOO_LOW, RATE_LIMITED, NOT_READY;

        public static Roll byId(int id) {
            Roll[] all = values();
            return id >= 0 && id < all.length ? all[id] : NOT_READY;
        }

        public boolean isResult() {
            return this == WIN || this == LOSE;
        }

        public String langKey() {
            return "gui.upgraderwheel.roll." + name().toLowerCase();
        }
    }
}
