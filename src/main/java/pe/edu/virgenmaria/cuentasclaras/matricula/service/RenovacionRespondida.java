package pe.edu.virgenmaria.cuentasclaras.matricula.service;

/**
 * La familia respondió la renovación (en la misma transacción). Si la registró Administración en persona
 * ({@code presencial}), la mensajería avisa a la familia: «si no la pediste, avísanos» (G16).
 */
public record RenovacionRespondida(Long renovacionId, boolean presencial, boolean continua) {
}
