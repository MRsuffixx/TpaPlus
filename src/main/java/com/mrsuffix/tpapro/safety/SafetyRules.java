package com.mrsuffix.tpapro.safety;

public final class SafetyRules {
    public enum Hazard { NONE, LAVA, FIRE, CAMPFIRE, CACTUS, MAGMA, POWDER_SNOW, BERRY_BUSH, VOID, SUFFOCATION, FALL }
    public record Cell(boolean passable, boolean solid, Hazard hazard) { }
    public record Options(boolean requireSolidGround, boolean preventLava, boolean preventFire,
                          boolean preventCampfire, boolean preventCactus, boolean preventMagma,
                          boolean preventPowderSnow, boolean preventBerryBush, boolean preventVoid,
                          boolean preventSuffocation) { }

    public Hazard validate(Cell below, Cell feet, Cell head, boolean belowWorldMinimum, boolean fallTooFar, Options options) {
        if (options.preventVoid() && belowWorldMinimum) return Hazard.VOID;
        if (options.preventSuffocation() && (!feet.passable() || !head.passable())) return Hazard.SUFFOCATION;
        Hazard hazard = firstPrevented(below, feet, head, options);
        if (hazard != Hazard.NONE) return hazard;
        if (options.requireSolidGround() && !below.solid()) return Hazard.FALL;
        if (fallTooFar) return Hazard.FALL;
        return Hazard.NONE;
    }

    private Hazard firstPrevented(Cell below, Cell feet, Cell head, Options o) {
        for (Cell cell : new Cell[]{below, feet, head}) {
            Hazard h = cell.hazard();
            if (h == Hazard.LAVA && o.preventLava() || h == Hazard.FIRE && o.preventFire()
                    || h == Hazard.CAMPFIRE && o.preventCampfire() || h == Hazard.CACTUS && o.preventCactus()
                    || h == Hazard.MAGMA && o.preventMagma() || h == Hazard.POWDER_SNOW && o.preventPowderSnow()
                    || h == Hazard.BERRY_BUSH && o.preventBerryBush()) return h;
        }
        return Hazard.NONE;
    }
}
