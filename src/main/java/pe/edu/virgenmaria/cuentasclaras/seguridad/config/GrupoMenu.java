package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp.*;

/**
 * Rediseño de la interfaz (docs/ux/rediseno-interfaz.md, hallazgo 3): los grupos plegables del menú, en orden de tarea.
 * Solo ordena y agrupa lo que {@link ModuloApp} ya permite a cada rol: no da ni quita permisos. {@link ModuloApp#INICIO}
 * va fuera de los grupos, arriba.
 */
public enum GrupoMenu {

	DINERO_DE_HOY("Dinero de hoy", CAJA_COBRO, APROBACIONES, PANEL, PAGOS_EN_LINEA, CONCILIACION, RECAUDACION),
	FAMILIAS_Y_ALUMNOS("Familias y alumnos", FAMILIA, ACADEMICO, ALUMNOS, AVISOS_FAMILIAS, MENSAJES, COMUNICADOS,
			MATRICULA_2027, LLAMADAS_CONTROL),
	// Sprint 9 (traído con el merge): la asistencia, sus cambios y reportes, los consentimientos de la ficha y la nómina
	// para el SIAGIE (tanda 3).
	ASISTENCIA_Y_CONSENTIMIENTOS("Asistencia", ASISTENCIA, ASISTENCIA_DIRECCION, REPORTE_ASISTENCIA, CONSENTIMIENTOS_FICHA,
			SIAGIE),
	COBRANZA("Cobranza", PENSIONES, DESCUENTOS, COMPROBANTES, REPORTES),
	CONTROL_Y_AUDITORIA("Control y auditoría", AUDITORIA, ACCESOS_DATOS),
	CONFIGURACION("Configuración", PLATAFORMA, COLEGIO, ASIGNACIONES, CONFIGURACION_ACADEMICA, USUARIOS, MARCA_COLEGIO,
			CONECTORES, FERIADOS, PUESTA_EN_MARCHA),
	MI_CUENTA("Mi cuenta", MI_CONTACTO);

	private final String etiqueta;

	private final List<ModuloApp> modulos;

	GrupoMenu(String etiqueta, ModuloApp... modulos) {
		this.etiqueta = etiqueta;
		this.modulos = List.of(modulos);
	}

	public String etiqueta() {
		return etiqueta;
	}

	/** Los módulos del grupo en el orden en que se muestran. */
	public List<ModuloApp> modulos() {
		return modulos;
	}

	/** El grupo de un módulo; vacío solo para {@link ModuloApp#INICIO}. */
	public static Optional<GrupoMenu> de(ModuloApp modulo) {
		return Arrays.stream(values()).filter(g -> g.modulos.contains(modulo)).findFirst();
	}
}
