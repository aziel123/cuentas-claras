package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 10, tanda 1: la pantalla «Evaluación del año». {@code guardada}: si Dirección ya la guardó (si no, muestra los
 * valores por defecto); {@code fijos}: si ya abrió un periodo del año (la versión, el tipo de periodo y los días de cierre
 * anticipado ya no cambian, E11 y M7); {@code puedeGuardar}: solo Dirección (Administración y Promotoría la leen).
 */
public record ConfiguracionEvaluacionVista(List<OpcionAnio> anios, Long anioId, Integer anio, boolean anioCerrado,
		boolean guardada, boolean fijos, boolean puedeGuardar, List<VersionDelCatalogo> versiones, Long curriculoVersionId,
		String versionCodigo, TipoPeriodo tipoPeriodo, int conclusionMax, boolean conclusionSiempre, int diasCierreAnticipado,
		String lenguaMaterna, String dre, String ugel, String actualizadoPor, LocalDateTime actualizadoEn) {
}
