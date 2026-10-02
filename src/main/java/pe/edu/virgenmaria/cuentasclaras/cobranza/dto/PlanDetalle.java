package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle de un plan con lo que el usuario en sesión puede hacer. {@code version}: la que vio; aprobar o devolver la
 * envía y se rechaza si el plan cambió. Si es su autor y tiene rol para aprobar,
 * {@code avisoAutor} explica por qué no ve «Aprobar».
 */
public record PlanDetalle(Long id, Long anioId, int anio, String nivel, String nivelEtiqueta, String nombre,
		int numeroVersion, String estado, String estadoEtiqueta, String estadoVariante, BigDecimal montoMatricula,
		LocalDate vencimientoMatricula, BigDecimal montoPension, List<VencimientoVista> vencimientos,
		LocalDate cobroDesde, String motivoCambio, String creadoPor, LocalDateTime creadoEn, String editadoPor,
		String aprobadoPor, LocalDateTime aprobadoEn, String cerradoPor, LocalDateTime cerradoEn,
		boolean puedeEditar, boolean puedeAprobar, boolean puedeNuevaVersion, boolean puedeDescartar,
		String avisoAutor, List<PlanResumen> historial, boolean puedeEnviar, boolean puedeDevolver, String enviadoPor,
		LocalDateTime enviadoEn, String devueltoPor, String motivoDevolucion, String editores, Long version) {
}
