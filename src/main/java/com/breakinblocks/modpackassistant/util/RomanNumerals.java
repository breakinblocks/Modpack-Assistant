package com.breakinblocks.modpackassistant.util;

public final class RomanNumerals {
    private static final String[] THOUSANDS = {"", "M", "MM", "MMM"};
    private static final String[] HUNDREDS = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
    private static final String[] TENS = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
    private static final String[] ONES = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    private RomanNumerals() {
    }

    public static String of(int number) {
        if (number < 1 || number > 3999) {
            throw new IllegalArgumentException("Roman numerals cover 1 to 3999, got " + number);
        }
        return THOUSANDS[number / 1000]
                + HUNDREDS[(number % 1000) / 100]
                + TENS[(number % 100) / 10]
                + ONES[number % 10];
    }
}
