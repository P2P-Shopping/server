package com.p2ps.util;

import com.p2ps.lists.exception.ListValidationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuantityParser {

    // Extract the numeric part
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]+)?\\s*$");

    private static final double MAX_ALLOWED_VALUE = 9999.0;


    public enum Unit {
        G(1.0, "g", "weight"),
        KG(1000.0, "kg", "weight"),
        ML(1.0, "ml", "volume"),
        L(1000.0, "l", "volume"),
        PCS(1.0, "buc", "pieces");

        final double baseMultiplier;
        final String symbol;
        final String family;

        Unit(double baseMultiplier, String symbol, String family) {
            this.baseMultiplier = baseMultiplier;
            this.symbol = symbol;
            this.family = family;
        }

        public static Unit fromString(String str) {
            if (str == null || str.isBlank()) return PCS;
            return switch (str.toLowerCase().trim()) {
                case "g", "grame", "gr" -> G;
                case "kg", "kilograme", "kilo" -> KG;
                case "ml", "mililitri" -> ML;
                case "l", "litri", "litru" -> L;
                case "buc", "bucati", "pcs", "piece" -> PCS;
                default -> PCS;
            };
        }
    }

    // Save parsed quantity
    public record ParsedQuantity(double value, Unit unit) {}

    public static ParsedQuantity parse(String quantityStr) {
        if (quantityStr == null || quantityStr.trim().isEmpty()) {
            return new ParsedQuantity(1.0, Unit.PCS);
        }

        Matcher matcher = QUANTITY_PATTERN.matcher(quantityStr);
        if (!matcher.matches()) {
            throw new ListValidationException("Quantity format is NOT valid: " + quantityStr);
        }

        double value = Double.parseDouble(matcher.group(1));

        if (value <= 0) {
            throw new ListValidationException("Quantity must be a positive number.");
        }
        if (value > MAX_ALLOWED_VALUE) {
            throw new ListValidationException("Quantity is greater than the maximum accepted limit (" + MAX_ALLOWED_VALUE + ").");
        }

        String unitStr = matcher.group(2);
        return new ParsedQuantity(value, Unit.fromString(unitStr));
    }


    public static String addQuantities(String q1, String q2) {
        ParsedQuantity parsed1 = parse(q1);
        ParsedQuantity parsed2 = parse(q2);

        if (!parsed1.unit().family.equals(parsed2.unit().family)) {
            return q2;
        }

        double totalBaseValue = (parsed1.value() * parsed1.unit().baseMultiplier) +
                (parsed2.value() * parsed2.unit().baseMultiplier);

        if (totalBaseValue > MAX_ALLOWED_VALUE * 1000) {
            throw new ListValidationException("Total sum of quantity is too big to be processed.");
        }

        return formatToOptimalUnit(totalBaseValue, parsed1.unit().family);
    }


    private static String formatToOptimalUnit(double baseValue, String family) {
        if ("weight".equals(family)) {
            if (baseValue >= 1000) {
                return formatNumber(baseValue / 1000.0) + " kg";
            }
            return formatNumber(baseValue) + " g";
        } else if ("volume".equals(family)) {
            if (baseValue >= 1000) {
                return formatNumber(baseValue / 1000.0) + " l";
            }
            return formatNumber(baseValue) + " ml";
        } else {
            return formatNumber(baseValue) + " buc";
        }
    }

    private static String formatNumber(double value) {
        if (value == (long) value) {
            return String.format("%d", (long) value);
        }
        return String.format("%s", value).replace(",", ".");
    }
}