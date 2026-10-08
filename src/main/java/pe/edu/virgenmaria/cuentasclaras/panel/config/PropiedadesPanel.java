package pe.edu.virgenmaria.cuentasclaras.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.EscritorXlsxSeguro;

import java.time.LocalTime;

/**
 * {@code cuentasclaras.panel} (sprint 6, sección 8). Las horas de las tareas van como cron en
 * {@code cuentasclaras.panel.resumen-diario}, {@code avisos-cada} y {@code recalculo} (las lee {@code @Scheduled}). Un
 * valor inválido impide arrancar.
 *
 * @param avisosTopeDiario      alertas por persona y día al celular (decisión 70); a partir de la siguiente sale una sola
 *                              «y N alertas más»
 * @param recalculoDias         cuántos días de fotos del resumen se recalculan cada mañana (decisión 81)
 * @param resumenAlertaHora     si a esta hora el resumen de hoy no salió, es CRÍTICA (P5)
 * @param exportacionMaxMeses   rango máximo de un Excel (decisión 73)
 * @param exportacionMaxDiarias descargas por persona y día (decisión 73)
 * @param exportacionMaxFilas   filas de un Excel (como máximo, las que admite el escritor)
 * @param exportacionesAtencion más de estas descargas de una persona en el día es ATENCIÓN en el panel
 * @param llamadasPorSemana     familias de la llamada de control de cada semana (decisión 77; tanda 3)
 */
@ConfigurationProperties("cuentasclaras.panel")
public record PropiedadesPanel(
		@DefaultValue("10") int avisosTopeDiario,
		@DefaultValue("35") int recalculoDias,
		@DefaultValue("21:00") LocalTime resumenAlertaHora,
		@DefaultValue("12") int exportacionMaxMeses,
		@DefaultValue("20") int exportacionMaxDiarias,
		@DefaultValue("20000") int exportacionMaxFilas,
		@DefaultValue("5") int exportacionesAtencion,
		@DefaultValue("3") int llamadasPorSemana) {

	public PropiedadesPanel {
		if (avisosTopeDiario < 1) {
			throw new IllegalArgumentException("cuentasclaras.panel.avisos-tope-diario: 1 o más");
		}
		if (recalculoDias < 1 || recalculoDias > 400) {
			throw new IllegalArgumentException("cuentasclaras.panel.recalculo-dias: de 1 a 400");
		}
		if (resumenAlertaHora == null) {
			throw new IllegalArgumentException("cuentasclaras.panel.resumen-alerta-hora: obligatoria");
		}
		if (exportacionMaxMeses < 1 || exportacionMaxMeses > 24) {
			throw new IllegalArgumentException("cuentasclaras.panel.exportacion-max-meses: de 1 a 24");
		}
		if (exportacionMaxDiarias < 1) {
			throw new IllegalArgumentException("cuentasclaras.panel.exportacion-max-diarias: 1 o más");
		}
		if (exportacionMaxFilas < 1 || exportacionMaxFilas > EscritorXlsxSeguro.MAX_FILAS) {
			throw new IllegalArgumentException("cuentasclaras.panel.exportacion-max-filas: de 1 a "
					+ EscritorXlsxSeguro.MAX_FILAS);
		}
		if (exportacionesAtencion < 1) {
			throw new IllegalArgumentException("cuentasclaras.panel.exportaciones-atencion: 1 o más");
		}
		if (llamadasPorSemana < 1 || llamadasPorSemana > 10) {
			throw new IllegalArgumentException("cuentasclaras.panel.llamadas-por-semana: de 1 a 10");
		}
	}
}
