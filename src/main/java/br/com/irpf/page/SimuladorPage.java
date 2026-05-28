package br.com.irpf.page;

import br.com.irpf.model.SimulationResult;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class SimuladorPage {

    public static final String URL = "https://www27.receita.fazenda.gov.br/simulador-irpf/";

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final JavascriptExecutor js;

    public SimuladorPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        this.js = (JavascriptExecutor) driver;
    }

    public SimuladorPage open() {
        driver.get(URL);
        wait.until(d -> "complete".equals(((JavascriptExecutor) d)
                .executeScript("return document.readyState")));
        wait.until(d -> rendimentosInputOrNull() != null);
        return this;
    }

    private WebElement rendimentosInputOrNull() {
        try {
            return (WebElement) js.executeScript(LOCATE_RENDIMENTOS_JS);
        } catch (Exception e) {
            return null;
        }
    }

    public WebElement rendimentosInput() {
        WebElement el = rendimentosInputOrNull();
        if (el == null) {
            throw new IllegalStateException("Could not locate Rendimentos input");
        }
        return el;
    }

    private static final String LOCATE_RENDIMENTOS_JS = """
            const labels = Array.from(document.querySelectorAll('label, span, div'));
            const lbl = labels.find(l => l.children.length === 0
                && (l.textContent || '').includes('Rendimentos tributáveis'));
            if (!lbl) return null;
            // Walk up looking for an input descendant that's editable
            let p = lbl;
            for (let i = 0; i < 8 && p; i++) {
                const candidate = p.querySelector('input[type=text]:not([readonly])');
                if (candidate && candidate.offsetParent !== null) return candidate;
                p = p.parentElement;
            }
            return null;
            """;

    public SimuladorPage setRendimentos(BigDecimal value) {
        WebElement input = rendimentosInput();
        input.click();
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        input.sendKeys(Keys.DELETE);
        long cents = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        input.sendKeys(Long.toString(cents));
        input.sendKeys(Keys.TAB);
        waitForRecalculation(value);
        return this;
    }

    private void waitForRecalculation(BigDecimal expectedIncome) {
        long deadline = System.currentTimeMillis() + 4000;
        String want = BrNumbers.format(expectedIncome);
        while (System.currentTimeMillis() < deadline) {
            try {
                Object inputValue = js.executeScript(
                        "const el = arguments[0]; return el ? el.value : null;",
                        rendimentosInputOrNull());
                if (inputValue != null && String.valueOf(inputValue).contains(want.split(",")[0])) {
                    break;
                }
                Thread.sleep(80);
            } catch (Exception e) {
                break;
            }
        }
        try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    public SimulationResult readResult(BigDecimal rendimentos) {
        Map<String, String> texts = (Map<String, String>) js.executeScript("""
                const wanted = {
                    deducoes: ['Deduções', 'Deducoes'],
                    base:     ['Base de cálculo', 'Base de calculo'],
                    imposto:  ['Imposto'],
                    aliquota: ['Alíquota efetiva', 'Aliquota efetiva']
                };

                function isNumberLike(t) {
                    if (!t) return false;
                    const s = t.replace(/\\s+/g,'').replace('R$','').replace('%','');
                    return /^-?\\d{1,3}(\\.\\d{3})*(,\\d+)?$|^-?\\d+(,\\d+)?$/.test(s);
                }

                function textOf(el) { return (el.textContent || '').trim(); }

                function findNearestNumber(label) {
                    const all = Array.from(document.querySelectorAll('*'));
                    const candidates = all.filter(el =>
                        el.children.length === 0
                        && textOf(el).includes(label)
                    );
                    for (const c of candidates) {
                        // Walk up looking for siblings/descendants containing a number
                        let node = c;
                        for (let i = 0; i < 6 && node; i++) {
                            const parent = node.parentElement;
                            if (!parent) break;
                            const numEl = Array.from(parent.querySelectorAll('*'))
                                .find(e => e !== c && e.children.length === 0
                                       && isNumberLike(textOf(e)));
                            if (numEl) return textOf(numEl);
                            node = parent;
                        }
                    }
                    return null;
                }

                const result = {};
                for (const [k, labels] of Object.entries(wanted)) {
                    for (const lbl of labels) {
                        const v = findNearestNumber(lbl);
                        if (v) { result[k] = v; break; }
                    }
                }
                return result;
                """);

        BigDecimal deducoes  = BrNumbers.parse(texts.get("deducoes"));
        BigDecimal base      = BrNumbers.parse(texts.get("base"));
        BigDecimal imposto   = BrNumbers.parse(texts.get("imposto"));
        BigDecimal aliquota  = BrNumbers.parse(texts.get("aliquota"));

        return new SimulationResult(rendimentos, deducoes, base, imposto, aliquota);
    }

    public SimulationResult simulate(BigDecimal rendimentos) {
        setRendimentos(rendimentos);
        return readResult(rendimentos);
    }

    public String debugDump() {
        Object out = js.executeScript("""
                const inputs = Array.from(document.querySelectorAll('input')).map(i => ({
                    type: i.type, name: i.name, id: i.id, ariaLabel: i.getAttribute('aria-label'),
                    readOnly: i.readOnly, disabled: i.disabled, visible: i.offsetParent !== null,
                    value: i.value
                }));
                const labelHits = ['Rendimentos','Deduções','Base de cálculo','Imposto','Alíquota']
                    .map(l => {
                        const el = Array.from(document.querySelectorAll('*'))
                            .find(e => e.children.length === 0
                                       && (e.textContent || '').includes(l));
                        return [l, el ? (el.outerHTML.slice(0, 200)) : null];
                    });
                return JSON.stringify({ inputs, labelHits }, null, 2);
                """);
        return String.valueOf(out);
    }

    public void selectAba(String texto) {
        WebElement tab = driver.findElement(
                By.xpath("//*[self::a or self::button or self::div][contains(normalize-space(.), '"
                        + texto + "')]"));
        tab.click();
        try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
