package com.yashjit.scarlet.hex.town;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignText;

/**
 * What the signs of the town's shops and public buildings say. A sign's kind is its piece's paint.
 */
public final class Signs {

    public static final int LIBRARY = 0;
    public static final int CHAPEL = 1;
    public static final int SCHOOL = 2;
    public static final int TOWN_HALL = 3;
    public static final int DINER = 4;
    public static final int PHARMACY = 5;
    public static final int GROCERY = 6;
    public static final int BAKERY = 7;
    public static final int HARDWARE = 8;
    public static final int BARBER = 9;
    public static final int ICE_CREAM = 10;
    public static final int POST_OFFICE = 11;
    public static final int FIVE_AND_DIME = 12;
    public static final int FARM_STAND = 13;
    /** The kinds a shop on main street can be. */
    public static final int[] SHOPS = {DINER, PHARMACY, GROCERY, BAKERY, HARDWARE, BARBER, ICE_CREAM, POST_OFFICE, FIVE_AND_DIME};

    private static final String[] KEYS = {"library", "chapel", "school", "town_hall", "diner", "pharmacy", "grocery", "bakery", "hardware",
            "barber", "ice_cream", "post_office", "five_and_dime", "farm_stand"};
    private static final String[][] FALLBACK = {
            {"PUBLIC", "LIBRARY"}, {"COMMUNITY", "CHURCH"}, {"ELEMENTARY", "SCHOOL"}, {"TOWN", "HALL"}, {"DINER", "OPEN"},
            {"PHARMACY", "& SODA"}, {"GROCERY", "FRESH FOOD"}, {"BAKERY", "FRESH BREAD"}, {"HARDWARE", "& TOOLS"}, {"BARBER", "SHOP"},
            {"ICE CREAM", "PARLOR"}, {"POST", "OFFICE"}, {"FIVE", "& DIME"}, {"FARM", "STAND"}};

    private Signs() {
    }

    public static SignText text(int kind) {
        int k = Math.floorMod(kind, KEYS.length);
        String key = "sign.scarlet." + KEYS[k];
        List<Component> lines = List.of(Component.empty(), Component.translatableWithFallback(key + ".top", FALLBACK[k][0]),
                Component.translatableWithFallback(key + ".bottom", FALLBACK[k][1]), Component.empty());
        return new SignText(lines, lines, DyeColor.BLACK, false);
    }
}
