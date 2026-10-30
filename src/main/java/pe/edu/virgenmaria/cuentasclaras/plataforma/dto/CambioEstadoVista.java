package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import java.time.LocalDateTime;

/**
 * Sprint 11, tanda 1: un cambio de estado en la bandeja del operador. Sin datos de familias: el colegio, el cambio, quién
 * lo propuso y lo aprobó, la objeción del titular y lo que espera (cierres, comprobantes, órdenes o libretas).
 *
 * @param puedeAprobar   {@code true} si quien mira no lo propuso (la pantalla oculta «Aprobar» y «Rechazar» si no)
 * @param bloqueadaPor   lo que espera la aplicación («2 cierres por revisar, 1 comprobante rechazado»), o {@code null}
 */
public record CambioEstadoVista(Long id, Long colegioId, String colegio, String tipo, String tipoEtiqueta, String estado,
		String estadoEtiqueta, String motivoTipo, String motivo, String referenciaAviso, String propuestoPor,
		LocalDateTime propuestoEn, String aprobadoPor, LocalDateTime efectivaDesde, String objecion, String objetadoPor,
		String objecionRespuesta, String objecionResueltaPor, boolean puedeAprobar, boolean puedeResolverObjecion,
		String bloqueadaPor) {

	public boolean propuesto() {
		return "PROPUESTO".equals(estado);
	}

	public boolean objecionAbierta() {
		return objecion != null && objecionResueltaPor == null;
	}
}
