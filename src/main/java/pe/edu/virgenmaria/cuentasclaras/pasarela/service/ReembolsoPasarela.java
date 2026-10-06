package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import java.math.BigDecimal;

/** El reembolso que hizo la pasarela (al mismo medio de origen del cargo). */
public record ReembolsoPasarela(String reembolsoId, String cargoId, BigDecimal monto) {
}
