package com.malek.upgraderwheel.logic;

/**
 * The odds formula, shared by the server (which rolls) and the client (which only displays).
 * Same shape as Item Upgrader by viniHNS (MIT): base / target x (1 - house edge), capped.
 */
public final class UpgradeMath {

    private UpgradeMath() {
    }

    /** Chance in percent before the cap. Can exceed 100 for downgrades; 0 when either value is unknown. */
    public static double rawChance(double betValue, double targetValue, double houseEdge) {
        if (betValue <= 0 || targetValue <= 0) {
            return 0;
        }
        return betValue / targetValue * (1 - houseEdge) * 100.0;
    }

    /** Chance in percent the roll actually uses. */
    public static double chance(double betValue, double targetValue, double houseEdge, double maxChance) {
        return Math.min(maxChance, rawChance(betValue, targetValue, houseEdge));
    }

    /** Units of a stackable result that land closest to the wanted chance, between 1 and maxAmount. */
    public static int amountForChance(double betValue, double unitValue, double houseEdge, double wantedChance, int maxAmount) {
        if (betValue <= 0 || unitValue <= 0 || wantedChance <= 0 || maxAmount <= 1) {
            return 1;
        }
        double wantedValue = betValue * (1 - houseEdge) * 100.0 / wantedChance;
        long amount = Math.round(wantedValue / unitValue);
        return (int) Math.max(1, Math.min(maxAmount, amount));
    }
}
