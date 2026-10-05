package com.casino.qa.pages;

import com.casino.qa.config.DriverProvider;
import com.casino.qa.pages.components.HeaderComponent;
import com.casino.qa.utils.ScenarioEvidenceRecorder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Page Object del historial de depositos del usuario.
 *
 * <p>Sirve como oraculo de verificacion independiente (fuera del modal) para los casos de
 * negocio: el deposito debe quedar registrado con el estado esperado. Esto cubre la tecnica de
 * caja negra "verificacion en base de datos" desde el lado de la aplicacion; en el script de
 * SQL Server se valida el mismo dato del lado del motor de datos.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DepositHistoryPage extends BasePage {

    // Filas del historial: <tr> de cualquier tabla, o elementos con token de clase del design system.
    @FindBy(xpath = "//table//tbody/tr")
    private List<WebElement> depositRows;
    @FindBy(xpath = "//*[" + "contains(concat(' ',normalize-space(@class),' '),' clmc-row ')" + "]")
    private List<WebElement> depositRowsAlt;
    @FindBy(xpath = "//*[contains(@class,'deposit') or contains(@class,'Deposit')]")
    private List<WebElement> depositRowsAlt2;

    private static final By DEPOSITS_MENU_ITEM = anyText("DEPOSITOS", "MIS DEPOSITOS", "HISTORIAL");
    private static final By DEPOSITS_MENU_ITEM_ALT =
            By.xpath("//a[contains(@href,'deposit') or contains(@href,'historial') or contains(@href,'movimientos')]");

    private final HeaderComponent header;

    public DepositHistoryPage(DriverProvider driverProvider, ScenarioEvidenceRecorder evidence, HeaderComponent header) {
        super(driverProvider, evidence);
        this.header = header;
    }

    /** Abre "Mis depositos" desde el menu de cuenta. */
    public DepositHistoryPage open() {
        header.openAccountMenu();
        findFirst(DEPOSITS_MENU_ITEM)
                .or(() -> findFirst(DEPOSITS_MENU_ITEM_ALT))
                .ifPresent(WebElement::click);
        return this;
    }

    /** Todas las filas del historial, en el orden mostrado (mas reciente primero). */
    public List<DepositRecord> records() {
        List<DepositRecord> records = new ArrayList<>();
        for (WebElement row : rows()) {
            String[] cells = row.getText().split("\\s{2,}|\\|");
            for (String cell : cells) {
                String trimmed = cell.trim();
                if (trimmed.matches("(?i)exitoso|pendiente|fallido|reembolsado|caducado")) {
                    records.add(new DepositRecord(statusFromCell(trimmed)));
                }
            }
        }
        return records;
    }

    private List<WebElement> rows() {
        for (List<WebElement> candidates : List.of(depositRows, depositRowsAlt, depositRowsAlt2)) {
            List<WebElement> found = elementsOf(candidates);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }

    private String statusFromCell(String cell) {
        if (cell.equalsIgnoreCase("exitoso")) {
            return "Exitoso";
        }
        if (cell.equalsIgnoreCase("pendiente")) {
            return "Pendiente";
        }
        if (cell.equalsIgnoreCase("fallido")) {
            return "Fallido";
        }
        if (cell.equalsIgnoreCase("reembolsado")) {
            return "Reembolsado";
        }
        return "Caducado";
    }

    /** Estado del deposito mas reciente. */
    public Optional<String> latestStatus() {
        List<WebElement> rows = rows();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(abbreviate(rows.get(0).getText()));
    }

    public boolean hasRecordWithStatus(String status) {
        return records().stream().anyMatch(record -> record.status().equalsIgnoreCase(status));
    }

    public boolean isEmpty() {
        return records().isEmpty();
    }

    /** Registro de deposito normalizado. */
    public record DepositRecord(String status) {
    }
}