package com.casino.qa.steps;

import com.casino.qa.pages.DepositPage;
import com.casino.qa.pages.QrDepositPage;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Steps del modulo de Deposito: apertura del modal, seleccion de pasarela, captura de monto y
 * moneda, envio de la solicitud y validacion del rango del monto.
 *
 * <p><b>Regla de arquetipo:</b> aqui vive la orquestacion. El atajo que atraviesa dos pantallas
 * ("seleccionar Pago con QR + monto + moneda + enviar") vivia antes dentro de
 * {@code DepositPage.depositWithQr()}, lo que mezclaba logica de negocio en la capa de paginas.
 * Ahora se compone en {@link #abrirPasarelaQrYEnviar(String, String)}.</p>
 */
@Component
public class DepositSteps {

private static final String QR_METHOD = "Pago con QR";
    private final ObjectProvider<DepositPage> depositPage;
    private final ObjectProvider<QrDepositPage> qrDepositPage;
    private final LoginSteps loginSteps;

    public DepositSteps(ObjectProvider<DepositPage> depositPage,
                        ObjectProvider<QrDepositPage> qrDepositPage,
                        LoginSteps loginSteps) {
        this.depositPage = depositPage;
        this.qrDepositPage = qrDepositPage;
        this.loginSteps = loginSteps;
    }

    // ------------------------------------------------------------- precondiciones

    /** Autentica y abre el modal de deposito, validando que el modal quedo disponible. */
    public void estarAutenticadoEnModuloDeposito() {
        loginSteps.autenticarConUsuarioDePruebas();
        abrirModuloDepositoDesdeHeader();
        Assertions.assertThat(depositPage.getObject().isModalVisible())
                .as("El modal de deposito debe abrirse")
                .isTrue();
    }

    // -------------------------------------------------------------------- acciones

    public void abrirModuloDepositoDesdeHeader() {
        depositPage.getObject().openFromHeader();
    }

    /** Selecciona la pasarela indicada. El reloj de la metrica arranca al enviar, no aqui. */
    public void seleccionarPasarela(String pasarela) {
        DepositPage deposit = depositPage.getObject();
        if (QR_METHOD.equalsIgnoreCase(pasarela)) {
            deposit.selectQrPaymentMethod();
        } else {
            deposit.selectPaymentMethod(pasarela);
        }
    }

    public void completarMontoYMoneda(String monto, String moneda) {
        DepositPage deposit = depositPage.getObject();
        deposit.enterAmount(monto);
        deposit.selectCurrency(moneda);
    }

    public void cambiarMontoA(String monto) {
        depositPage.getObject().enterAmount(monto);
    }

    /**
     * Envia la solicitud. El instante de marcado se toma inmediatamente ANTES del clic para que
     * la metrica "&lt; 3 s" mida la generacion del QR y no el relleno del formulario.
     */
    public void enviarSolicitudDeposito() {
        qrDepositPage.getObject().markSubmission();
        depositPage.getObject().submit();
    }

    /**
     * Atajo de orquestacion que atraviesa dos pantallas (modal de deposito -&gt; pasarela QR).
     * Sustituye al antiguo {@code DepositPage.depositWithQr()}.
     */
    public void abrirPasarelaQrYEnviar(String monto, String moneda) {
        estarAutenticadoEnModuloDeposito();
        seleccionarPasarela(QR_METHOD);
        completarMontoYMoneda(monto, moneda);
        enviarSolicitudDeposito();
    }

    // --------------------------------------------------------------- validaciones

/**
 * El monto introducido esta dentro del rango que acepta el portal.
 *
 * <p>La verificacion se hace sobre el boton de envio: el portal solo lo habilita cuando el monto
 * es valido. Comprobar que "no aparece un texto de error" no serviria, porque los limites minimo y
 * maximo se muestran siempre, tanto si el monto es correcto como si no.</p>
 */
public void sistemaAceptaElMontoEnRango() {
    DepositPage deposit = depositPage.getObject();
    Assertions.assertThat(deposit.amountValidationMessage())
            .as("No debe mostrarse mensaje de error para un monto valido")
            .isNotPresent();
    Assertions.assertThat(deposit.isAmountAccepted())
            .as("El portal debe habilitar el envio para un monto dentro del rango")
            .isTrue();
}

/**
 * El portal rechaza el monto.
 *
 * <p>Se verifican las dos señales que el portal emite: el boton de envio queda deshabilitado y
 * aparece el mensaje de limite correspondiente al minimo o al maximo.</p>
 */
public void sistemaMuestraErrorDeMontoInvalido() {
    DepositPage deposit = depositPage.getObject();
    Assertions.assertThat(deposit.amountValidationMessage())
            .as("Debe mostrarse un mensaje de validacion de monto")
            .isPresent();
    Assertions.assertThat(deposit.isAmountAccepted())
            .as("El portal debe impedir el envio de un monto fuera de rango")
            .isFalse();
    Assertions.assertThat(deposit.isModalStillOpen())
            .as("El modal de deposito debe permanecer abierto tras un monto invalido")
            .isTrue();
}
}