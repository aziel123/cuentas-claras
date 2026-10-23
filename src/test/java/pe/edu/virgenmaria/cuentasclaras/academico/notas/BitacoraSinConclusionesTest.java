package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 2 (M8 y riesgo 5): las conclusiones pueden llevar datos sensibles (apoyos y ajustes por discapacidad).
 * En todo el circuito (registrar con conclusión, pedir la corrección con su borrador y aprobarla) el texto no aparece en la
 * bitácora, en la solicitud ni en los logs: solo su SHA-256.
 */
class BitacoraSinConclusionesTest extends PruebaNotas {

	private static final String SENSIBLE = "Presenta TDAH diagnosticado; requiere ajustes razonables en la evaluación escrita.";

	private static final String CORREGIDA = "Con los ajustes razonables avanza; recomendar tutoría personalizada semanal.";

	@Test
	void niLaBitacoraNiLaSolicitudNiLosLogsLlevanElTexto() {
		ListAppender<ILoggingEvent> capturados = new ListAppender<>();
		Logger raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		capturados.start();
		raiz.addAppender(capturados);
		try {
			guardar(ana, "MAT", hoja("MAT", List.of(mateo), "C", SENSIBLE));
			cerrarPeriodo();
			Long nota = vigente(mateo, competencias("MAT").get(0));
			como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(nota, "B", CORREGIDA, MOTIVO_CORRECCION));
			EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "calificacion", nota);
			SecurityContextHolder.clearContext();
		}
		finally {
			raiz.detachAppender(capturados);
		}
		String bitacora = String.join("\n", jdbc.queryForList("SELECT CONCAT(COALESCE(detalle, ''), '|', "
				+ "COALESCE(valor_anterior, ''), '|', COALESCE(valor_nuevo, '')) FROM evento_auditoria", String.class));
		String solicitudes = String.join("\n", jdbc.queryForList("SELECT CONCAT(resumen, '|', COALESCE(datos, ''), '|', motivo) "
				+ "FROM solicitud_cambio", String.class));
		String logs = capturados.list.stream().map(e -> e.getFormattedMessage()
				+ (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage())).collect(Collectors.joining("\n"));
		for (String fuente : List.of(bitacora, solicitudes, logs)) {
			assertThat(fuente).doesNotContain("TDAH", "ajustes razonables", "tutoría personalizada");
		}
		assertThat(bitacora).as("la bitácora guarda la huella de las conclusiones").contains("conclusión");
		assertThat(jdbc.queryForObject("SELECT conclusion FROM calificacion WHERE id = ?", String.class,
				vigente(mateo, competencias("MAT").get(0)))).isEqualTo(CORREGIDA);
	}
}
