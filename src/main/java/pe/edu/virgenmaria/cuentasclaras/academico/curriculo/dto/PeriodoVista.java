package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sprint 10, tanda 1: un periodo en la pantalla «Periodos». {@code cierreDesde}: el primer día en que Dirección puede
 * cerrarlo ({@code fin − dias_cierre_anticipado}). Las acciones posibles las calcula el servicio con las mismas reglas que la
 * base (la base vuelve a exigirlas).
 */
public record PeriodoVista(Long id, int numero, String nombre, LocalDate inicio, LocalDate fin, String estado,
		String estadoEtiqueta, String estadoVariante, String abiertoPor, LocalDateTime abiertoEn, String cerradoPor,
		LocalDateTime cerradoEn, LocalDate cierreDesde, boolean puedeAbrir, boolean puedeCerrar, boolean puedeMoverInicio,
		boolean puedeMoverFin) {
}
