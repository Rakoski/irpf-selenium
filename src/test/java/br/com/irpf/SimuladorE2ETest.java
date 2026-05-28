package br.com.irpf;

import br.com.irpf.analyzer.BracketAnalysis;
import br.com.irpf.analyzer.IrpfAnalyzer;
import br.com.irpf.model.SimulationResult;
import br.com.irpf.page.Drivers;
import br.com.irpf.page.SimuladorPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimuladorE2ETest {

    private WebDriver driver;
    private SimuladorPage page;

    @BeforeAll
    void setUp() {
        driver = Drivers.newChrome();
        page = new SimuladorPage(driver).open();
    }

    @AfterAll
    void tearDown() {
        if (driver != null) driver.quit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "100", "500", "1000", "1500", "2000"})
    @DisplayName("Valores baixos não devem gerar imposto")
    void minimumValuesProduceNoTax(String income) {
        SimulationResult r = page.simulate(new BigDecimal(income));
        assertEquals(0, r.imposto().signum(),
                "Renda " + income + " não deveria gerar imposto, mas veio " + r.imposto());
        assertEquals(0, r.aliquotaEfetiva().signum(),
                "Alíquota efetiva deveria ser 0 para renda " + income);
    }

    @Test
    @DisplayName("Renda zerada deve produzir imposto zero e dedução padrão")
    void zeroIncomeProducesZeroTax() {
        SimulationResult r = page.simulate(BigDecimal.ZERO);
        assertEquals(0, r.imposto().signum(),
                "Imposto deveria ser 0 com renda 0, veio " + r.imposto());
        assertEquals(0, r.baseCalculo().signum(),
                "Base de cálculo deveria ser 0, veio " + r.baseCalculo());
        assertTrue(r.deducoes().signum() > 0, "Deduções padrão deveriam ser maiores que zero");
    }

    @Test
    @DisplayName("Imposto deve crescer ou permanecer estável com o aumento da renda")
    void taxIsMonotonic() {
        BigDecimal[] grid = {
                new BigDecimal("1000"),  new BigDecimal("2500"),
                new BigDecimal("3500"),  new BigDecimal("4500"),
                new BigDecimal("5500"),  new BigDecimal("7000"),
                new BigDecimal("10000"), new BigDecimal("15000"),
                new BigDecimal("25000")
        };
        BigDecimal lastTax = new BigDecimal("-1");
        for (BigDecimal income : grid) {
            SimulationResult r = page.simulate(income);
            assertTrue(r.imposto().compareTo(lastTax) >= 0,
                    "Imposto diminuiu ao passar para renda " + income
                            + " (antes " + lastTax + ", agora " + r.imposto() + ")");
            lastTax = r.imposto();
        }
    }

    @Test
    @DisplayName("Alíquota efetiva exibida deve bater com imposto/renda")
    void effectiveRateMatchesComputed() {
        SimulationResult r = page.simulate(new BigDecimal("6000"));
        BigDecimal calc = r.aliquotaEfetivaCalculada();
        assertTrue(r.aliquotaEfetiva().subtract(calc).abs().compareTo(new BigDecimal("0.05")) <= 0,
                "Alíquota informada " + r.aliquotaEfetiva()
                        + "% diverge da calculada " + calc + "%");
    }

    @Test
    @DisplayName("Análise deve detectar ao menos uma mudança de faixa entre R$1k e R$12k")
    void analyzerDetectsBracketChanges() {
        List<SimulationResult> samples = new ArrayList<>();
        BigDecimal step = new BigDecimal("500");
        for (BigDecimal income = new BigDecimal("1000");
             income.compareTo(new BigDecimal("12000")) <= 0;
             income = income.add(step)) {
            samples.add(page.simulate(income));
        }
        BracketAnalysis analysis = IrpfAnalyzer.analyze(samples);

        assertFalse(analysis.bracketChanges().isEmpty(),
                "Esperava detectar mudanças de faixa, mas nenhuma foi encontrada");
        assertTrue(analysis.anomalies().stream()
                .noneMatch(a -> a.kind().equals("NEGATIVE_TAX")
                             || a.kind().equals("NON_MONOTONIC_TAX")),
                "Não deveriam existir anomalias críticas: " + analysis.anomalies());

        boolean hasZero = analysis.segments().stream()
                .anyMatch(s -> s.marginalRatePct().compareTo(new BigDecimal("0.50")) < 0);
        boolean hasTaxed = analysis.segments().stream()
                .anyMatch(s -> s.marginalRatePct().compareTo(new BigDecimal("5.00")) > 0);
        assertTrue(hasZero, "Esperava uma faixa isenta (~0%)");
        assertTrue(hasTaxed, "Esperava ao menos uma faixa tributada (>5%)");
    }

    @Test
    @DisplayName("Busca binária localiza limite da primeira faixa tributada (precisão R$1)")
    void binarySearchFindsFirstBracketBoundary() {
        BigDecimal low = new BigDecimal("3000");
        BigDecimal high = new BigDecimal("9000");

        assertEquals(0, page.simulate(low).imposto().signum(),
                "Renda baixa de partida deveria ser isenta");
        assertTrue(page.simulate(high).imposto().signum() > 0,
                "Renda alta de partida deveria ser tributada");

        while (high.subtract(low).compareTo(BigDecimal.ONE) > 0) {
            BigDecimal mid = low.add(high)
                    .divide(new BigDecimal(2), 2, java.math.RoundingMode.HALF_UP);
            SimulationResult r = page.simulate(mid);
            if (r.imposto().signum() > 0) {
                high = mid;
            } else {
                low = mid;
            }
        }
        System.out.println("Limite estimado da primeira faixa tributada: ~R$ " + high);
        assertTrue(high.compareTo(new BigDecimal("3500")) > 0
                && high.compareTo(new BigDecimal("8000")) < 0,
                "Limite encontrado fora do esperado: " + high);
    }
}
