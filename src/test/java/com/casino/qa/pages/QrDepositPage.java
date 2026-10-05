package com.casino.qa.pages;

import com.casino.qa.config.DriverProvider;
import com.casino.qa.utils.ScenarioEvidenceRecorder;
import com.casino.qa.utils.ScenarioState;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Page Object de la pasarela de pago "Pago con QR".
 *
 * <p>Es el corazon del reto. Encapsula la lectura del QR generado, su tiempo de generacion
 * (metrica no funcional), la vigencia, el estado de la transaccion y la reactivacion del mismo
 * QR (control de seguridad).</p>
 *
 * <p>Decisiones de diseno relevantes:</p>
 * <ul>
 *   <li>El payload del QR se obtiene por el DOM cuando esta expuesto. Si el portal no lo
 *       muestra, {@link #qrPayload()} devuelve {@link Optional#empty()} y la validacion del
 *       formato en {@link #isQrPayloadWellFormed()} devuelve {@code false}: es la pagina la que
 *       reporta "no hay payload", no una asercion.</li>
 *   <li>La espera del estado final es un polling con intervalo configurable, no un
 *       {@code sleep}: refleja como funciona la pasarela (webhook del proveedor).</li>
 * </ul>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class QrDepositPage extends BasePage {

    /**
     * Formato generico de un payload de pasarela QR.
     *
     * <p>Incluye el espacio porque EMVCo lo permite dentro de los campos de texto (por ejemplo el
     * nombre del comercio, "5923Atlantic City PEN 50.00"): un charset sin espacios rechazaria
     * payloads perfectamente validos.</p>
     */
    private static final Pattern QR_PAYLOAD_FORMAT =
            Pattern.compile("^[0-9A-Za-z\\-._~:/?#\\[\\]@!$&'()*+,;=% ]+$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int MIN_QR_PAYLOAD_LENGTH = 8;

    /** Cabecera obligatoria de un QR EMVCo (ISO 20022 / EMVCo MPM). */
    private static final String EMVCO_HEADER = "000201";
    /** Template 52: nombre del comercio. Su presencia confirma que el QR trae datos de cobro. */
    private static final String EMVCO_MERCHANT_NAME_TAG = "5802";
    /** Template 63: CRC16 que todo QR EMVCo cierra con "6304" + 4 digitos hexadecimales. */
    private static final Pattern EMVCO_CRC_TRAILER = Pattern.compile("6304[0-9A-Fa-f]{4}$");
    /** Template 8801: referencia del comercio, el identificador de la operacion de cobro. */
    private static final String EMVCO_MERCHANT_REFERENCE_TAG = "8801";
/** Tags cuyo campo de longitud ocupa cuatro digitos en lugar de dos. */
    private static final Set<String> EMVCO_NESTED_TAGS = Set.of("26", "62", "63", "64", "65", "80");

    /** Motivos por los que el modulo puede rechazar una operacion. */
    private static final List<String> QR_ERROR_KEYWORDS = List.of(
            "ERROR", "RECHAZ", "FALLID", "TIMEOUT", "EXPIR", "VENCID", "INVALID", "NO VALIDO");
    /** Un mensaje de estado real es corto; la prosa de ayuda del modulo no lo es. */
    private static final int MAX_STATUS_MESSAGE_LENGTH = 80;
    private static final String ERROR_KEYWORD_PREDICATE =
            "[contains(" + UPPER_XP + ",'ERROR') or contains(" + UPPER_XP + ",'RECHAZ') or contains("
                    + UPPER_XP + ",'FALLID') or contains(" + UPPER_XP + ",'TIMEOUT') or contains("
                    + UPPER_XP + ",'EXPIR') or contains(" + UPPER_XP + ",'VENCID') or contains("
                    + UPPER_XP + ",'INVALID') or contains(" + UPPER_XP + ",'NO VALIDO')]";

    // Tokens del design system, expandidos a constantes porque las anotaciones @FindBy solo
    // admiten expresiones constantes en tiempo de compilacion.
    private static final String TOK_QR =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-qr ')";
    private static final String TOK_ERROR =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-error ')";
    private static final String TOK_ALERT =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-alert ')";
    private static final String TOK_BADGE =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-badge ')";

    /** Contenedor del modal de QR. */
    @FindBy(xpath = "//*[contains(@class,'clmc-modal') or contains(@class,'clmc-popup')" + VISIBLE + "]")
    private WebElement qrModal;
    @FindBy(xpath = "//*[contains(@id,'QR') or " + TOK_QR + VISIBLE + "]")
    private WebElement qrModalAlt;

    // Imagen/canvas del QR. El portal lo monta en #depositResponseImgQR y el <img> interior no lleva
    // ni id, ni alt, ni clase con "qr": solo un data URI base64. Por eso el id del contenedor es la
    // unica ancla estable y debe evaluarse antes que cualquier heuristica de clase.
    @FindBy(css = "#depositResponseImgQR img")
    private WebElement qrImage;
    @FindBy(css = "#depositResponseImgQR")
    private WebElement qrImageAlt;
    @FindBy(xpath = "//img[starts-with(@src,'data:image') and ancestor::*[@id='depositResponseImgQR']]")
    private WebElement qrImageAlt2;
    @FindBy(xpath = "//canvas[" + TOK_QR + " or contains(@id,'qr') or contains(@id,'QR')]")
    private WebElement qrImageAlt3;
    @FindBy(xpath = "//img[contains(@src,'data:image') and (contains(@class,'qr') or contains(@id,'QR') or contains(@alt,'QR'))]")
    private WebElement qrImageAlt4;

    // Payload del QR: campo de solo lectura o prefijo EMVCo/PIX expuesto en el DOM.
    @FindBy(xpath = "//input[@readonly and (contains(@id,'qr') or contains(@id,'QR') or " + TOK_QR + ")]")
    private WebElement qrPayloadElement;
    @FindBy(xpath = "//textarea[@readonly]")
    private WebElement qrPayloadElementAlt;
    @FindBy(xpath = "//*[starts-with(normalize-space(.),'000201')]")
    private WebElement qrPayloadElementAlt2;

    // Referencia / codigo de operacion.
    @FindBy(xpath = "//*[contains(" + UPPER_XP + ",'REFERENCIA') or contains(" + UPPER_XP + ",'CODIGO') or contains(" + UPPER_XP + ",'OPERACION')]")
    private WebElement qrReferenceLabel;
    @FindBy(xpath = "//*[contains(@id,'reference') or contains(@id,'REFERENCE') or contains(@id,'operacion')]")
    private WebElement qrReferenceLabelAlt;
    @FindBy(xpath = "//*[contains(@class,'reference') or contains(@class,'Reference')]")
    private WebElement qrReferenceLabelAlt2;

    // Vigencia / cuenta regresiva del QR.
    @FindBy(xpath = "//*[contains(" + UPPER_XP + ",'VIGENCIA') or contains(" + UPPER_XP + ",'EXPIRA') or contains(" + UPPER_XP + ",'CADUCA') or contains(" + UPPER_XP + ",'VALIDO')]")
    private WebElement qrExpiryLabel;
    @FindBy(xpath = "//*[contains(@id,'expiry') or contains(@id,'countdown') or contains(@class,'countdown')]")
    private WebElement qrExpiryLabelAlt;
    @FindBy(xpath = "//*[contains(@class,'timer') or contains(@class,'Timer')]")
    private WebElement qrExpiryLabelAlt2;

    // Estado de la transaccion.
    @FindBy(xpath = "//*[" + TOK_BADGE + "]")
    private WebElement qrStatusLabel;
    @FindBy(xpath = "//*[contains(" + UPPER_XP + ",'ESTADO') or contains(" + UPPER_XP + ",'PENDIENTE') or contains(" + UPPER_XP + ",'EXITOSO') or contains(" + UPPER_XP + ",'FALLIDO')]")
    private WebElement qrStatusLabelAlt;
    @FindBy(xpath = "//*[contains(@id,'status') or contains(@class,'status') or contains(@class,'Status')]")
    private WebElement qrStatusLabelAlt2;
    @FindBy(xpath = "//*[@role='status'" + VISIBLE + "]")
    private WebElement qrStatusLabelAlt3;

    // QR consumido: insignia o texto explicito.
    @FindBy(xpath = "//*[" + TOK_BADGE + " and (contains(" + UPPER_XP + ",'UTILIZADO') or contains(" + UPPER_XP + ",'USADO') or contains(" + UPPER_XP + ",'VENCIDO'))]")
    private WebElement qrUsedBadge;
    @FindBy(xpath = "//*[contains(" + UPPER_XP + ",'UTILIZADO') or contains(" + UPPER_XP + ",'YA FUE USADO') or contains(" + UPPER_XP + ",'EXPIRADO') or contains(" + UPPER_XP + ",'CADUCADO')]")
    private WebElement qrUsedBadgeAlt;

    // Error de la pasarela.
    @FindBy(xpath = "//*[" + TOK_ERROR + " or " + TOK_ALERT + "]")
    private WebElement qrErrorMessage;
    @FindBy(xpath = "//*[@role='alert'" + VISIBLE + "]")
    private WebElement qrErrorMessageAlt;
    @FindBy(xpath = "//*[contains(" + UPPER_XP + ",'ERROR') or contains(" + UPPER_XP + ",'FALLO') or contains(" + UPPER_XP + ",'RECHAZADO')]")
    private WebElement qrErrorMessageAlt2;
    @FindBy(xpath = "//*[contains(@class,'error') or contains(@class,'Error')]")
    private WebElement qrErrorMessageAlt3;

    // Confirmacion del pago por parte del usuario.
    @FindBy(xpath = "//button[contains(" + UPPER_XP + ",'YA PAGUE')]")
    private WebElement confirmPaymentButton;
    @FindBy(xpath = "//button[contains(" + UPPER_XP + ",'CONFIRMAR PAGO') or contains(" + UPPER_XP + ",'REALIZAR PAGO')]")
    private WebElement confirmPaymentButtonAlt;
    @FindBy(xpath = "//button[@type='submit' and not(contains(@class,'close'))]")
    private WebElement confirmPaymentButtonAlt2;

    // Reapertura del flujo QR (intento de reutilizar el mismo codigo).
    @FindBy(xpath = "//*[contains(" + UPPER_XP + ",'PAGO CON QR') or contains(" + UPPER_XP + ",'PAGO QR')]")
    private WebElement qrMethodEntry;
    @FindBy(xpath = "//*[contains(@id,'QR') or contains(@value,'QR') or contains(@data-method,'qr')]")
    private WebElement qrMethodEntryAlt;

    // Cierre del modal.
    @FindBy(xpath = "//*[contains(@data-testid,'CloseIcon')]/ancestor::*[self::button or self::div or @role='button'][1]")
    private WebElement closeModalButton;
    @FindBy(xpath = "//button[contains(@aria-label,'Cerrar') or contains(@aria-label,'Close') or contains(@class,'clmc-close')]")
    private WebElement closeModalButtonAlt;
    @FindBy(xpath = "//*[contains(@id,'error400Popup') or contains(@id,'closePopup')]//*[contains(@class,'clmc-pointer')]")
    private WebElement closeModalButtonAlt2;

    private static final String PROBE_INSTALL_JS = """
        (function(){
          window.__qrGenProbe = window.__qrGenProbe || {active: false};
          if (window.__qrGenProbe.active) return;
          window.__qrGenProbe.active = true;
          var clickMs = null;
          var renderMs = null;
          var serverMs = null;
          var resourceName = null;

          try {
            window.__qrGenProbe.resourceObserver = new PerformanceObserver(function(list){
              try {
                var entries = list.getEntries();
                for (var i = 0; i < entries.length; i++) {
                  var e = entries[i];
                  if (!e.name) continue;
                  var it = e.initiatorType;
                  if (it === 'xmlhttprequest' || it === 'fetch') {
                    if (serverMs == null) {
                      serverMs = e.duration;
                      if (!isFinite(serverMs) || serverMs < 0) {
                        serverMs = e.responseEnd - e.requestStart;
                      }
                      if (isFinite(serverMs) && serverMs >= 0) {
                        resourceName = e.name;
                      } else {
                        serverMs = null;
                      }
                      break;
                    }
                  }
                }
              } catch (er) {}
            });
            window.__qrGenProbe.resourceObserver.observe({entryTypes: ['resource']});
          } catch (er) {}

          document.addEventListener('click', function(){
            if (clickMs === null) {
              clickMs = performance.now();
              window.__qrGenProbe.clickMs = clickMs;
            }
          }, true);

          var mo = new MutationObserver(function(){
            if (renderMs !== null) return;
            try {
              var img = document.querySelector('#depositResponseImgQR img, [id*=\\\"depositResponseImgQR\\\"] img');
              if (img) {
                renderMs = performance.now();
                window.__qrGenProbe.renderMs = renderMs;
                window.__qrGenProbe.clickMs = window.__qrGenProbe.clickMs || clickMs;
                window.__qrGenProbe.serverMs = serverMs;
                window.__qrGenProbe.resourceName = resourceName;
                try { mo.disconnect(); } catch (ee) {}
              }
            } catch (er) {}
          });
          mo.observe(document, {childList: true, subtree: true, attributes: true, attributeFilter: ['src', 'style']});
          window.__qrGenProbe.mutationObserver = mo;
          window.__qrGenProbe.clickMs = null;
          window.__qrGenProbe.renderMs = null;
          window.__qrGenProbe.serverMs = null;
          window.__qrGenProbe.resourceName = null;
        })();
    """;

  private static final String PROBE_READ_JS = """
        (function(){
          var p = window.__qrGenProbe || {};
          var click = p.clickMs;
          var render = p.renderMs;
          var server = p.serverMs;
          var rn = p.resourceName;
          var renderLag = (click !== undefined && click !== null && render !== undefined && render !== null) ? (render - click) : null;
          return {clickMs: click, renderMs: render, serverMs: server, resourceName: rn, renderLagMs: renderLag};
        })();
    """;

    private final ScenarioState state;

    public QrDepositPage(DriverProvider driverProvider, ScenarioEvidenceRecorder evidence, ScenarioState state) {
        super(driverProvider, evidence);
        this.state = state;
    }

// ------------------------------------------------------------------ entrada

    /**
     * Instala una sonda IN-PAGE para medir la generacion del QR sin ruido del arnes de pruebas.
     *
     * <p>La medicion por WebDriver mezcla tres cosas: el viaje del {@code click()} de ida y vuelta,
     * el trabajo real del portal y hasta 500 ms de retardo de sondeo (el {@code FluentWait} sondea
     * cada 500 ms). Como el cronometro se para en Java, el resultado solo puede discretizarse en
     * saltos de 500 ms y sobreestimar el tiempo real.</p>
     *
     * <p>Esta sonda se resuelve dentro del navegador, con precision de sub-milisegundo:</p>
     * <ul>
     *   <li>un listener en fase de captura registra {@code performance.now()} en el instante exacto
     *       en que el clic llega a la pagina, sin depender del viaje de WebDriver;</li>
     *   <li>un {@code MutationObserver} marca el instante exacto en que la imagen del QR aparece
     *       en el DOM, sin esperas ni sondeos;</li>
     *   <li>{@code PerformanceResourceTiming} aporta la duracion real de la peticion XHR/fetch que
     *       genera el QR, para separar red y servidor del renderizado.</li>
     * </ul>
     *
     * <p>El listener de clic se instala en fase de captura sobre {@code document}, asi que registra
     * el evento real disparado por el usuario y no un click sintetico.</p>
     */
    public QrDepositPage armGenerationProbe() {
        executeScript(PROBE_INSTALL_JS);
        return this;
    }

    /**
     * Lee el desglose de la sonda y lo registra.
     *
     * <p>Devuelve el tiempo {@code clic -> QR visible} medido dentro del navegador (ms enteros),
     * la cifra mas pura y comparable con el criterio de 3 s.</p>
     */
    @SuppressWarnings("unchecked")
    public Optional<Duration> readGenerationProbe() {
        Object raw = executeScript(PROBE_READ_JS);
        if (!(raw instanceof Map<?, ?> map)) {
            LOG.warn("[PERF] la sonda no devolvio datos: {}", raw);
            return Optional.empty();
        }
        Number clickMs = (Number) map.get("clickMs");
        Number renderMs = (Number) map.get("renderMs");
        Number serverMs = (Number) map.get("serverMs");
        Number renderLagMs = (Number) map.get("renderLagMs");

        long endToEnd = (clickMs != null && renderMs != null)
                ? Math.round(renderMs.doubleValue() - clickMs.doubleValue())
                : -1;

        LOG.info("[PERF] Desglose de la generacion del QR (medido en el navegador):");
        LOG.info("[PERF]   clic -> QR visible (end-to-end puro) : {} ms", endToEnd >= 0 ? endToEnd : "n/d");
        LOG.info("[PERF]   peticion XHR que genera el QR       : {} ms  ({})",
                serverMs != null ? Math.round(serverMs.doubleValue()) : "n/d", map.get("resourceName"));
        LOG.info("[PERF]   respuesta -> QR painted en pantalla : {} ms",
                renderLagMs != null ? Math.round(renderLagMs.doubleValue()) : "n/d");

        if (clickMs == null || renderMs == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(endToEnd));
    }

    /** Marca de tiempo en que el usuario confirma el deposito: punto de partida de la medicion. */
    public QrDepositPage markSubmission() {
        state.markQrSubmission();
        return this;
    }

    /**
     * Espera a que la imagen del QR aparezca y devuelve el tiempo medido desde
     * {@link #markSubmission()}. Es la metrica clave del criterio "&lt; 3 s".
     *
     * <p>El resultado se cachea en {@link ScenarioState} para que todos los pasos que afirman
     * sobre la generacion del QR (``se genera un codigo QR de pago valido`` y
     * ``el tiempo de generacion del QR es menor a N segundos``) midan lo mismo y no acumulen
     * el tiempo de invocaciones sucesivas.</p>
     */
    public Duration awaitQrGeneration() {
        Duration cached = state.qrGenerationTime();
        if (cached != null) {
            return cached;
        }
        Instant start = state.qrSubmissionInstant() != null ? state.qrSubmissionInstant() : Instant.now();
        waitForVisibilityOfAny(driverProvider.config().qr().maxGenerationTime().plusSeconds(2),
                qrImage, qrImageAlt, qrImageAlt2, qrImageAlt3, qrImageAlt4);
        Duration measured = Duration.between(start, Instant.now());
        state.cacheQrGenerationTime(measured);
        return measured;
    }

    public boolean isQrModalVisible() {
        return firstDisplayed(qrModal, qrModalAlt).isPresent();
    }

    public boolean isQrImageVisible() {
        return firstDisplayed(qrImage, qrImageAlt, qrImageAlt2, qrImageAlt3, qrImageAlt4).isPresent();
    }

    // ------------------------------------------------------------------- lectura

    /**
     * Contenido del codigo QR.
     *
     * <p>Este portal no publica el payload en el DOM: entrega un PNG en base64 dentro de
     * {@code #depositResponseImgQR}. Se intenta primero el DOM (por si el backend cambiara) y si no
     * hay nada se decodifica la imagen con ZXing, que es la unica forma de comprobar que el QR
     * generado es real y no un marcador de posicion.</p>
     */
    public Optional<String> qrPayload() {
        Optional<String> fromDom = firstDisplayed(qrPayloadElement, qrPayloadElementAlt, qrPayloadElementAlt2)
                .map(WebElement::getText)
                .map(text -> text.replaceAll("\\s+", "").trim())
                .filter(text -> !text.isEmpty());
        return fromDom.isPresent() ? fromDom : decodeQrImage();
    }

    /** Texto contenido en el PNG del QR, decodificado con ZXing. */
    private Optional<String> decodeQrImage() {
        return firstDisplayed(qrImage, qrImageAlt, qrImageAlt2, qrImageAlt3, qrImageAlt4)
                .map(element -> element.getAttribute("src"))
                .filter(src -> src != null && !src.isBlank())
                .flatMap(this::decodeQrDataUri);
    }

    /** Decodifica un data URI de imagen PNG en su texto embebido. */
    private Optional<String> decodeQrDataUri(String dataUri) {
        int separator = dataUri.indexOf(',');
        if (!dataUri.startsWith("data:image") || separator < 0) {
            return Optional.empty();
        }
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(dataUri.substring(separator + 1));
        } catch (IllegalArgumentException e) {
            LOG.warn("[QR] el src no es un data URI base64 valido: {}", e.getMessage());
            return Optional.empty();
        }
        return decodeQrBytes(imageBytes);
    }

    private Optional<String> decodeQrBytes(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                LOG.warn("[QR] la imagen del QR no es un formato legible por ImageIO");
                return Optional.empty();
            }
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            BinaryBitmap bitmap = new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            Result result = new MultiFormatReader().decode(bitmap, hints);
            String text = result.getText();
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.trim());
        } catch (Exception e) {
            // Deliberadamente amplio: ZXing lanza varias excepciones concretas segun el motivo.
            LOG.warn("[QR] no se pudo decodificar la imagen del QR: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Referencia unica de la operacion de cobro.
     *
     * <p>El portal no imprime ningun codigo de operacion: el bloque de respuesta solo muestra el
     * monto, el boton "DESCARGAR QR" y la vigencia. La unicidad si existe, pero va dentro del QR, asi
     * que se toma de ahi. Se intenta el template 8801 de EMVCo (referencia del comercio); este
     * portal emite un payload propietario donde ese tag no siempre esta bien formado, asi que si no
     * se puede leer se recurre a la huella del payload, que igualmente es unica por transaccion.</p>
     */
    public Optional<String> qrReference() {
        Optional<String> fromDom = firstDisplayed(qrReferenceLabel, qrReferenceLabelAlt, qrReferenceLabelAlt2)
                .map(WebElement::getText)
                .map(this::abbreviate)
                .filter(text -> !text.isEmpty());
        if (fromDom.isPresent()) {
            return fromDom;
        }
        return qrPayload()
                .flatMap(payload -> extractEmvcoMerchantReference(payload)
                        .or(() -> qrFingerprint().map(hash -> "QR-" + hash)));
    }

    /**
     * Lee el template 8801 de EMVCo recorriendo los pares tag/longitud.
     *
     * <p>El recorrido es defensivo a proposito: si la longitud declarada no cuadra con lo que queda
     * de payload se abandona en lugar de leer basura, porque un payload propietario puede romper el
     * formato y un tag leido en la posicion equivocada daria una referencia inventada.</p>
     */
    private Optional<String> extractEmvcoMerchantReference(String payload) {
        int index = 4;
        while (index + 4 <= payload.length()) {
            String tag = payload.substring(index, index + 2);
            if (!tag.matches("\\d{2}")) {
                return Optional.empty();
            }
            int lengthStart = index + 2;
            int length;
            if (EMVCO_NESTED_TAGS.contains(tag)) {
                if (lengthStart + 4 > payload.length()) {
                    return Optional.empty();
                }
                length = parseLength(payload.substring(lengthStart, lengthStart + 4));
                lengthStart += 4;
            } else {
                if (lengthStart + 2 > payload.length()) {
                    return Optional.empty();
                }
                length = parseLength(payload.substring(lengthStart, lengthStart + 2));
                lengthStart += 2;
            }
            if (length <= 0 || lengthStart + length > payload.length()) {
                return Optional.empty();
            }
            String value = payload.substring(lengthStart, lengthStart + length);
            if (EMVCO_MERCHANT_REFERENCE_TAG.equals(tag)) {
                return value.isBlank() ? Optional.empty() : Optional.of(value);
            }
            index = lengthStart + length;
        }
        return Optional.empty();
    }

    private static int parseLength(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Texto de vigencia del QR, util para aserciones de vencimiento. */
    public Optional<String> qrExpiryLabel() {
        return firstDisplayed(qrExpiryLabel, qrExpiryLabelAlt, qrExpiryLabelAlt2)
                .map(WebElement::getText)
                .map(this::abbreviate)
                .filter(text -> !text.isEmpty());
    }

    /**
     * El payload corresponde a un QR de cobro real, no a un marcador de posicion.
     *
     * <p>Se distinguen dos casos. Si el QR es EMVCo (que es lo que emite este portal) se valida su
     * estructura: cabecera 000201, template del nombre del comercio y el CRC "6304" final. Si la
     * pasarela usara otro esquema, se cae al chequeo generico de charset y longitud.</p>
     */
    public boolean isQrPayloadWellFormed() {
        return qrPayload().map(payload -> {
            if (payload.length() < MIN_QR_PAYLOAD_LENGTH) {
                return false;
            }
            if (payload.startsWith(EMVCO_HEADER)) {
                return payload.contains(EMVCO_MERCHANT_NAME_TAG) && EMVCO_CRC_TRAILER.matcher(payload).find();
            }
            return QR_PAYLOAD_FORMAT.matcher(payload).matches();
        }).orElse(false);
    }

    /** Hash estable del payload: permite comparar QR sin depender del texto integro. */
    public Optional<String> qrFingerprint() {
        return qrPayload().map(payload -> Integer.toHexString(payload.hashCode()));
    }

    // -------------------------------------------------------------------- estado

    /** Estado actual mostrado (Pendiente / Exitoso / Fallido / Caducado). */
public Optional<String> currentStatus() {
        return firstDisplayed(qrStatusLabel, qrStatusLabelAlt, qrStatusLabelAlt2, qrStatusLabelAlt3)
                .map(WebElement::getText)
                .map(this::abbreviate)
                .filter(text -> !text.isEmpty())
                // Un role="status" puede ser cualquier region aria-live del portal; solo se acepta
                // si su texto pertenece al vocabulario de estados de un deposito.
                .filter(this::isRecognisedStatus)
                .or(this::deriveStatusFromEvidence);
    }

    private boolean isRecognisedStatus(String text) {
        String upper = text.toUpperCase();
        return upper.contains("PENDIENTE") || upper.contains("EXITOSO") || upper.contains("FALLIDO")
                || upper.contains("PROCESANDO") || upper.contains("COMPLETADO") || upper.contains("PAGADO")
                || upper.contains("RECHAZADO") || upper.contains("VENCIDO") || upper.contains("EXPIRADO");
    }

    /**
     * Deduce el estado del QR a partir de lo que el portal realmente renderiza.
     *
     * <p>El modulo de QR no muestra ninguna etiqueta de estado: no hay badges ni el texto "Pendiente"
     * en ningun punto del DOM. El estado solo es deducible por lo que hay en pantalla, y esa
     * deduccion es la unica verificable sin inventar un dato que la pagina no expone:</p>
     * <ul>
     *   <li>mensaje de error visible dentro del modulo -> "Fallido";</li>
     *   <li>confirmacion de deposito visible -> "Exitoso";</li>
     *   <li>QR generado y sin confirmacion -> "Pendiente": el QR existe y espera el pago.</li>
     * </ul>
     *
     * <p>Las busquedas van acotadas al contenedor del deposito porque estas palabras aparecen
     * tambien en textos estaticos de ayuda fuera del modulo.</p>
     */
    private Optional<String> deriveStatusFromEvidence() {
        if (isQrErrorVisible()) {
            return Optional.of("Fallido");
        }
        if (isDepositConfirmedVisible()) {
            return Optional.of("Exitoso");
        }
        if (isQrImageVisible()) {
            return Optional.of("Pendiente");
        }
        return Optional.empty();
    }

    /**
     * Confirmacion de deposito registrado.
     *
     * <p>Se excluye "Deposit OK" a proposito: el portal lo pinta siempre como texto estatico de la
     * infografia "Como depositar", asi que no sirve para distinguir un pago confirmado.</p>
     */
    private boolean isDepositConfirmedVisible() {
        return driver().findElements(By.xpath(
                "//*[@role='alert' or contains(@class,'success') or contains(@class,'Success')]"
                        + "[contains(" + UPPER_XP + ",'DEPOSITO REALIZADO') or contains(" + UPPER_XP
                        + ",'PAGO CONFIRMADO') or contains(" + UPPER_XP + ",'OPERACION EXITOSA') or contains("
                        + UPPER_XP + ",'FELICIDADES')]"))
                .stream().anyMatch(this::isDisplayed);
    }

    private boolean isQrErrorVisible() {
        return semanticErrorVisible() || messageLikeErrorVisible();
    }

    /** Errores que el portal marca con semantica propia (role=alert o clase de error). */
    private boolean semanticErrorVisible() {
        String semantic = "//*[@role='alert' or contains(@class,'error') or contains(@class,'Error')"
                + " or contains(@class,'alert') or contains(@class,'invalid')]";
        return driver().findElements(By.xpath(semantic + ERROR_KEYWORD_PREDICATE))
                .stream().anyMatch(this::isDisplayed);
    }

    /**
     * Errores mostrados como texto plano.
     *
     * <p>La prosa de ayuda del modulo tambien menciona "rechazado" ("Paga el monto indicado, de lo
     * contrario tu deposito sera rechazado"), asi que un simple "contiene" daria falsos positivos.
     * Solo cuenta como error un mensaje corto que EMPIEZA por el motivo.</p>
     */
    private boolean messageLikeErrorVisible() {
        return driver().findElements(By.xpath("//*[self::p or self::span or self::div]" + ERROR_KEYWORD_PREDICATE))
                .stream()
                .filter(this::isDisplayed)
                .map(WebElement::getText)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isEmpty() && text.length() <= MAX_STATUS_MESSAGE_LENGTH)
                .anyMatch(text -> QR_ERROR_KEYWORDS.stream()
                        .anyMatch(keyword -> text.toUpperCase().startsWith(keyword)));
    }

    /** Espera (con polling) hasta que el estado contenga el texto esperado. */
    public boolean waitForStatus(String expectedStatus, Duration timeout) {
        return waitUntilTrue(d -> currentStatus()
                .map(status -> status.toLowerCase().contains(expectedStatus.toLowerCase()))
                .orElse(false), timeout);
    }

    public boolean waitForStatusSuccess(Duration timeout) {
        return waitForStatus("exitoso", timeout);
    }

    public boolean waitForStatusFailure(Duration timeout) {
        return waitForStatus("fallido", timeout);
    }

    public boolean waitForStatusExpired(Duration timeout) {
        return waitForStatus("caduc", timeout);
    }

    /** El QR quedo marcado como utilizado / vencido por el portal. */
    public boolean isQrMarkedAsUsed() {
        return firstDisplayed(qrUsedBadge, qrUsedBadgeAlt).isPresent();
    }

    public Optional<String> gatewayErrorMessage() {
        return firstDisplayed(qrErrorMessage, qrErrorMessageAlt, qrErrorMessageAlt2, qrErrorMessageAlt3)
                .map(WebElement::getText)
                .map(this::abbreviate)
                .filter(text -> !text.isEmpty());
    }

    // ------------------------------------------------------------------ acciones

    /** Boton "Ya page" / "Confirmar pago": dispara la notificacion del pago a la pasarela. */
    public QrDepositPage confirmPayment() {
        clickAny(confirmPaymentButton, confirmPaymentButtonAlt);
        return this;
    }

    /** Cierra el modal de QR. Tras el cierre, un QR consumido debe seguir marcado como usado. */
    public QrDepositPage closeModal() {
        firstDisplayed(closeModalButton, closeModalButtonAlt, closeModalButtonAlt2)
                .ifPresent(WebElement::click);
        return this;
    }

    /** Vuelve a abrir el ultimo QR generado para intentar reutilizarlo (caso de seguridad). */
    public QrDepositPage reopenQr() {
        clickAny(qrMethodEntry, qrMethodEntryAlt);
        return this;
    }

    /** Captura la evidencia visual del QR, imprescindible para depurar fallos de la pasarela. */
    public void captureQrEvidence() {
        captureScreenshot("qr-generado");
    }
}