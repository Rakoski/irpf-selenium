package br.com.irpf.page;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class BrNumbers {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DecimalFormatSymbols SYMBOLS = new DecimalFormatSymbols(PT_BR);
    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.00", SYMBOLS);

    private BrNumbers() {}

    public static String format(BigDecimal value) {
        return FORMAT.format(value);
    }

    public static BigDecimal parse(String raw) {
        if (raw == null) return BigDecimal.ZERO;
        String cleaned = raw
                .replace("R$", "")
                .replace(" ", "")
                .replace(" ", "")
                .replace("%", "")
                .trim();
        if (cleaned.isEmpty() || cleaned.equals("-")) return BigDecimal.ZERO;
        cleaned = cleaned.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
