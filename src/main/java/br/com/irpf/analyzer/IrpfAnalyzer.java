package br.com.irpf.analyzer;

import br.com.irpf.model.SimulationResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class IrpfAnalyzer {

    private static final BigDecimal MARGINAL_TOLERANCE = new BigDecimal("0.0050"); // 0.5 p.p.
    private static final BigDecimal ALIQUOTA_TOLERANCE = new BigDecimal("0.05");   // 0.05 %

    private IrpfAnalyzer() {}

    public static BracketAnalysis analyze(List<SimulationResult> samples) {
        List<SimulationResult> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparing(SimulationResult::rendimentos));

        List<BracketAnalysis.Anomaly> anomalies = detectAnomalies(sorted);
        List<BracketAnalysis.Segment> segments = buildSegments(sorted);
        List<BracketAnalysis.BracketChange> changes = bracketChanges(segments);
        BigDecimal firstTaxable = firstTaxableIncome(sorted);

        return new BracketAnalysis(changes, anomalies, segments, firstTaxable);
    }

    private static List<BracketAnalysis.Anomaly> detectAnomalies(List<SimulationResult> sorted) {
        List<BracketAnalysis.Anomaly> out = new ArrayList<>();
        SimulationResult prev = null;
        for (SimulationResult r : sorted) {
            if (r.imposto().signum() < 0) {
                out.add(new BracketAnalysis.Anomaly(r.rendimentos(), "NEGATIVE_TAX",
                        "Imposto negativo: " + r.imposto()));
            }
            if (r.baseCalculo().signum() == 0 && r.imposto().signum() > 0) {
                out.add(new BracketAnalysis.Anomaly(r.rendimentos(), "TAX_WITH_ZERO_BASE",
                        "Imposto > 0 com base de cálculo zero"));
            }
            BigDecimal calc = r.aliquotaEfetivaCalculada();
            if (r.aliquotaEfetiva().subtract(calc).abs().compareTo(ALIQUOTA_TOLERANCE) > 0
                    && r.rendimentos().signum() > 0) {
                out.add(new BracketAnalysis.Anomaly(r.rendimentos(), "ALIQUOTA_MISMATCH",
                        "Alíquota informada " + r.aliquotaEfetiva()
                                + "% difere da calculada " + calc + "%"));
            }
            if (prev != null && r.imposto().compareTo(prev.imposto()) < 0
                    && r.rendimentos().compareTo(prev.rendimentos()) > 0) {
                out.add(new BracketAnalysis.Anomaly(r.rendimentos(), "NON_MONOTONIC_TAX",
                        "Imposto diminuiu apesar do aumento da renda"));
            }
            prev = r;
        }
        return out;
    }

    private static List<BracketAnalysis.Segment> buildSegments(List<SimulationResult> sorted) {
        List<BracketAnalysis.Segment> segments = new ArrayList<>();
        if (sorted.size() < 2) return segments;

        BigDecimal segStartIncome = sorted.get(0).rendimentos();
        BigDecimal segMarginal = marginal(sorted.get(0), sorted.get(1));
        BigDecimal segMarginalSum = segMarginal;
        int segCount = 1;
        BigDecimal segEndIncome = sorted.get(1).rendimentos();

        for (int i = 2; i < sorted.size(); i++) {
            BigDecimal m = marginal(sorted.get(i - 1), sorted.get(i));
            BigDecimal segAvg = segMarginalSum.divide(BigDecimal.valueOf(segCount),
                    new MathContext(8));
            if (m.subtract(segAvg).abs().compareTo(MARGINAL_TOLERANCE) <= 0) {
                segMarginalSum = segMarginalSum.add(m);
                segCount++;
                segEndIncome = sorted.get(i).rendimentos();
            } else {
                segments.add(new BracketAnalysis.Segment(
                        segStartIncome, segEndIncome,
                        pct(segAvg), segCount + 1));
                segStartIncome = sorted.get(i - 1).rendimentos();
                segEndIncome = sorted.get(i).rendimentos();
                segMarginal = m;
                segMarginalSum = m;
                segCount = 1;
            }
        }
        BigDecimal segAvg = segMarginalSum.divide(BigDecimal.valueOf(segCount), new MathContext(8));
        segments.add(new BracketAnalysis.Segment(segStartIncome, segEndIncome,
                pct(segAvg), segCount + 1));
        return segments;
    }

    private static List<BracketAnalysis.BracketChange> bracketChanges(List<BracketAnalysis.Segment> segments) {
        List<BracketAnalysis.BracketChange> out = new ArrayList<>();
        for (int i = 1; i < segments.size(); i++) {
            BracketAnalysis.Segment prev = segments.get(i - 1);
            BracketAnalysis.Segment cur = segments.get(i);
            out.add(new BracketAnalysis.BracketChange(
                    cur.incomeFrom(), prev.marginalRatePct(), cur.marginalRatePct()));
        }
        return out;
    }

    private static BigDecimal firstTaxableIncome(List<SimulationResult> sorted) {
        for (SimulationResult r : sorted) {
            if (r.imposto().signum() > 0) return r.rendimentos();
        }
        return null;
    }

    private static BigDecimal marginal(SimulationResult a, SimulationResult b) {
        BigDecimal dIncome = b.rendimentos().subtract(a.rendimentos());
        if (dIncome.signum() == 0) return BigDecimal.ZERO;
        BigDecimal dTax = b.imposto().subtract(a.imposto());
        return dTax.divide(dIncome, new MathContext(10));
    }

    private static BigDecimal pct(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
