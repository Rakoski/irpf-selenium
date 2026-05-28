package br.com.irpf.page;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public final class Drivers {

    private Drivers() {}

    public static WebDriver newChrome() {
        ChromeOptions opts = new ChromeOptions();
        boolean headless = !"false".equalsIgnoreCase(System.getProperty("headless", "true"));
        if (headless) {
            opts.addArguments("--headless=new");
        }
        opts.addArguments(
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1366,900",
                "--lang=pt-BR"
        );
        return new ChromeDriver(opts);
    }
}
