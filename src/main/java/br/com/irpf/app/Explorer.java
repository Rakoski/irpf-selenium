package br.com.irpf.app;

import br.com.irpf.model.SimulationResult;
import br.com.irpf.page.Drivers;
import br.com.irpf.page.SimuladorPage;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;

public class Explorer {
    public static void main(String[] args) {
        WebDriver driver = Drivers.newChrome();
        try {
            SimuladorPage page = new SimuladorPage(driver).open();
            System.out.println("---- Input localizado ----");
            System.out.println("id=" + page.rendimentosInput().getAttribute("id")
                    + " value=" + page.rendimentosInput().getAttribute("value"));

            for (String v : new String[]{"0", "1000", "3000", "4000", "5000", "8000", "12000"}) {
                SimulationResult r = page.simulate(new BigDecimal(v));
                System.out.println("input=" + v + " -> " + r);
            }
        } finally {
            driver.quit();
        }
    }
}
