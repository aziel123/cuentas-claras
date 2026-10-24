package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import java.time.LocalDateTime;

/**
 * Sprint 11, tanda 1 (AP-10): un cambio de estado tal como lo ve la Promotoría o Dirección de su colegio: qué se propuso,
 * desde cuándo rige, la objeción del titular y la respuesta del operador. Sin nombres de operadores.
 *
 * @param puedeObjetar {@code true} si quien mira es el titular y el cambio (una baja o una suspensión pendiente) aún no
 *                     tiene objeción
 */
public record CambioDelColegioVista(Long id, String tipo, String estado, String estadoEtiqueta, String motivoTipo,
		String motivo, String referenciaAviso, LocalDateTime propuestoEn, LocalDateTime efectivaDesde, String objecion,
		String objecionRespuesta, String objecionResueltaPor, boolean puedeObjetar) {

	public boolean pendiente() {
		return "PROPUESTO".equals(estado) || "APROBADO".equals(estado);
	}
}
