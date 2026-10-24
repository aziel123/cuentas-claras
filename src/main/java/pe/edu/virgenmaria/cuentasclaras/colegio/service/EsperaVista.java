package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import java.time.LocalDateTime;

/**
 * Sprint 11, tanda 1c (decisiones 218 y 223): algo que una Promotoría aplicó directo y espera 24 horas (una cuenta
 * bancaria, una pasarela o una recaudación; dar o quitar Promotoría; desactivar a una Promotoría), para la pantalla del
 * operador que lo puede frenar. Sin datos de las familias.
 *
 * @param clase {@code CONECTOR} o {@code SOLICITUD}
 */
public record EsperaVista(String clase, Long id, String que, String pidio, LocalDateTime vence) {
}
