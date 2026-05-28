package br.com.irpf.app;

import br.com.irpf.analyzer.BracketAnalysis;
import br.com.irpf.analyzer.IrpfAnalyzer;
import br.com.irpf.model.SimulationResult;
import br.com.irpf.page.Drivers;
import br.com.irpf.page.SimuladorPage;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class IrpfScanApp {

    public static void main(String[] args) {
        BigDecimal start = new BigDecimal("1000");
        BigDecimal stop  = new BigDecimal("8000");
        BigDecimal step  = new BigDecimal("250");

        WebDriver driver = Drivers.newChrome();
        try {
            SimuladorPage page = new SimuladorPage(driver).open();

            List<SimulationResult> samples = new ArrayList<>();
            for (BigDecimal income = start;
                 income.compareTo(stop) <= 0;
                 income = income.add(step)) {
                SimulationResult r = page.simulate(income);
                samples.add(r);
                System.out.println(r);
            }

            BracketAnalysis analysis = IrpfAnalyzer.analyze(samples);
            printReport(analysis);
        } finally {
            driver.quit();
        }
    }

    private static void printReport(BracketAnalysis a) {
        System.out.println("\n===== Análise =====");
        System.out.println("Primeira renda tributada: " + a.firstTaxableIncome());
        System.out.println("\nSegmentos (faixas detectadas):");
        for (BracketAnalysis.Segment s : a.segments()) {
            System.out.printf(" - %s a %s  -> alíquota marginal %s%% (%d pontos)%n",
                    s.incomeFrom(), s.incomeTo(), s.marginalRatePct(), s.points());
        }
        System.out.println("\nMudanças de faixa:");
        for (BracketAnalysis.BracketChange c : a.bracketChanges()) {
            System.out.printf(" - em ~R$ %s: %s%% -> %s%%%n",
                    c.income(), c.previousMarginalRate(), c.newMarginalRate());
        }
        System.out.println("\nAnomalias:");
        if (a.anomalies().isEmpty()) {
            System.out.println(" (nenhuma)");
        } else {
            for (BracketAnalysis.Anomaly an : a.anomalies()) {
                System.out.printf(" - R$ %s [%s] %s%n",
                        an.income(), an.kind(), an.description());
            }
        }
    }
}
