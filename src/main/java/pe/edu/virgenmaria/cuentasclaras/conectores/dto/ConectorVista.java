package pe.edu.virgenmaria.cuentasclaras.conectores.dto;

import java.time.LocalDateTime;

/**
 * Sprint 8, tanda 2: un conector para la pantalla {@code /conectores}, SIN secretos: solo la pista (los 4 últimos
 * caracteres) y el resumen de su configuración (la cuenta y el CCI, enmascarados).
 */
public record ConectorVista(Long id, String tipo, String tipoEtiqueta, String proveedor, String modo, String resumen,
		String pista, String estado, String estadoEtiqueta, String estadoVariante, String propuestoPor,
		LocalDateTime propuestoEn, String resueltoPor, LocalDateTime resueltoEn, Boolean pruebaOk, String pruebaDetalle,
		Long solicitudId, boolean puedeProbar, boolean enEspera, LocalDateTime venceEspera, boolean puedeAprobarAhora,
		boolean puedeFrenar) {
}
