package br.com.irpf;

import br.com.irpf.analyzer.BracketAnalysis;
import br.com.irpf.analyzer.IrpfAnalyzer;
import br.com.irpf.model.SimulationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IrpfAnalyzerTest {

    @Test
    void detectsBracketsAndAnomalies() {
        List<SimulationResult> samples = List.of(
                sample("1000", "0"),
                sample("1500", "0"),
                sample("2000", "0"),
                sample("2500", "50"),
                sample("3000", "100"),
                sample("3500", "200"),
                sample("4000", "300"),
                sample("5000", "500")
        );
        BracketAnalysis a = IrpfAnalyzer.analyze(samples);
        assertEquals(3, a.segments().size(), "Esperava 3 segmentos");
        assertEquals(0, a.segments().get(0).marginalRatePct().compareTo(new BigDecimal("0.00")));
        assertEquals(0, a.segments().get(1).marginalRatePct().compareTo(new BigDecimal("10.00")));
        assertEquals(0, a.segments().get(2).marginalRatePct().compareTo(new BigDecimal("20.00")));
        assertEquals(0, new BigDecimal("2500").compareTo(a.firstTaxableIncome()));
        assertTrue(a.anomalies().isEmpty(), "Não deveriam existir anomalias: " + a.anomalies());
    }

    @Test
    void flagsNonMonotonicTax() {
        List<SimulationResult> samples = List.of(
                sample("1000", "100"),
                sample("2000", "50")
        );
        BracketAnalysis a = IrpfAnalyzer.analyze(samples);
        assertTrue(a.anomalies().stream().anyMatch(x -> x.kind().equals("NON_MONOTONIC_TAX")));
    }

    private static SimulationResult sample(String income, String tax) {
        BigDecimal inc = new BigDecimal(income);
        BigDecimal t = new BigDecimal(tax);
        BigDecimal aliq = inc.signum() == 0
                ? BigDecimal.ZERO
                : t.multiply(BigDecimal.valueOf(100))
                   .divide(inc, 2, java.math.RoundingMode.HALF_UP);
        return new SimulationResult(inc, new BigDecimal("607.20"),
                inc.subtract(new BigDecimal("607.20")).max(BigDecimal.ZERO),
                t, aliq);
    }
}
