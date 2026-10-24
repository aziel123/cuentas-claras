package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correcciones del sprint 10 (P-5 del QA y hallazgo 4): UN aviso por familia por oleada de emisión. Con la ventana de 30
 * minutos, las libretas de una familia emitidas en pasadas distintas (la versión 1 a las 08:00 y la corregida a las 08:20)
 * van en un solo aviso, que sale cuando la oleada lleva 30 minutos sin libretas nuevas.
 */
@TestPropertySource(properties = "cuentasclaras.libretas.ventana-avisos-minutos=30")
class AvisosLibretasVentanaTest extends PruebaLibretas {

	@Test
	void lasLibretasDeUnaOleadaVanEnUnSoloAvisoAlTerminarLaVentana() {
		cerrarYEmitir();
		assertThat(avisarA(8, 10)).as("dentro de la ventana, todavía no").isZero();
		// Una corrección de Mateo aprobada a las 08:15 y emitida a las 08:20: la misma oleada.
		hora(8, 15);
		Long nota = vigente(mateo, competencias("MAT").get(0));
		como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(nota, "AD", null, MOTIVO_CORRECCION));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "calificacion", nota);
		SecurityContextHolder.clearContext();
		hora(8, 20);
		assertThat(emitir().corregidas()).isEqualTo(1);
		// A las 08:40 la oleada de la familia de Pedro (08:00) ya terminó; la de Rosa sigue abierta (última, 08:20).
		assertThat(avisarA(8, 40)).isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE apoderado_id = ? AND tipo LIKE 'LIBRETA%'", pedro)).isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE apoderado_id = ? AND tipo LIKE 'LIBRETA%'", rosa)).isZero();
		assertThat(avisarA(8, 55)).isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE apoderado_id = ? AND tipo LIKE 'LIBRETA%'", rosa))
				.as("Rosa: un solo aviso por Mateo y Valeria, la versión 1 y la corregida").isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE apoderado_id = ? AND tipo = 'LIBRETA_CORREGIDA'", rosa))
				.isEqualTo(1);
		assertThat(avisarA(9, 30)).as("una pasada repetida no duplica").isZero();
	}

	private void hora(int h, int m) {
		reloj.fijar(LocalDateTime.of(2026, 5, 4, h, m).atZone(ZoneId.of("America/Lima")).toInstant());
	}

	private int avisarA(int h, int m) {
		hora(h, m);
		SecurityContextHolder.clearContext();
		return avisos.enColegio(1L, LocalDateTime.of(2026, 5, 4, h, m));
	}
}
