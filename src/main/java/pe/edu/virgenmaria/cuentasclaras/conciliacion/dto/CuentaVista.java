package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import java.time.LocalDate;

/** Una cuenta del colegio con hasta dónde está cargado y confirmado su extracto. */
public record CuentaVista(Long id, String banco, String numero, String alias, boolean activa, LocalDate cargadoHasta,
		LocalDate confirmadoHasta, int porConfirmar) {
}
