package pe.edu.virgenmaria.cuentasclaras.privacidad.dto;

import java.time.LocalDate;

/** Una familia del reporte «Datos con plazo vencido»: cuándo se fue y cuántos contactos (celular o correo) guardamos. */
public record DatoVencido(Long familiaId, String familia, LocalDate salida, long contactos) {
}
