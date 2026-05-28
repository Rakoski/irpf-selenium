package br.com.irpf.analyzer;

import java.math.BigDecimal;
import java.util.List;

public record BracketAnalysis(
        List<BracketChange> bracketChanges,
        List<Anomaly> anomalies,
        List<Segment> segments,
        BigDecimal firstTaxableIncome
) {
    public record BracketChange(BigDecimal income, BigDecimal previousMarginalRate, BigDecimal newMarginalRate) {}
    public record Anomaly(BigDecimal income, String kind, String description) {}
    public record Segment(BigDecimal incomeFrom, BigDecimal incomeTo, BigDecimal marginalRatePct, int points) {}
}
