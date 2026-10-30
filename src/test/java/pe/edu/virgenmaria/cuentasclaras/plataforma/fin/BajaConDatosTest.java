package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 2 (AP-6 y AP2-2): un colegio en preparación CON datos (alumnos, apoderados o matrículas) se da de baja
 * solo con su exportación ENTREGADA o VENCIDA de los últimos 30 días; antes, la baja se rechaza.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class BajaConDatosTest extends BaseFinColegio {

	private static final String MOTIVO = "El colegio decidió no seguir y ya recibió sus datos por escrito.";

	@Test
	void conDatosLaBajaEsperaLaExportacionEntregada() {
		long colegio = colegioEnProduccion("Colegio Baja Datos", EscenarioPlataforma.RUC_B, "titular.bajadatos",
				"45678997", "987650097");
		jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, produccion_por = NULL WHERE id = ?",
				colegio);
		long titularId = usuario(colegio, "titular.bajadatos").getId();
		jdbc.update("DELETE FROM configuracion_colegio WHERE colegio_id = ? AND clave = 'huella_correo_externo'", colegio);
		EscenarioPuestaEnMarcha.datos(jdbc, colegio, EnumSet.of(Nivel.PRIMARIA), titularId);

		como(ana);
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.NO_ARRANCO, MOTIVO,
				null)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("exportación");

		// La exportación a pedido (con la firma del operador), entregada al titular.
		fin.pedirExportacion(colegio, "Carta del colegio 2026-77");
		SecurityContextHolder.clearContext();
		recibir(colegio, "45678997");
		assertThat(exportacion(colegio)).containsEntry("estado", "ENTREGADA");

		como(ana);
		Long id = cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.NO_ARRANCO, MOTIVO, null);
		assertThat(id).isPositive();
		SecurityContextHolder.clearContext();
	}

	@Test
	void conUnaExportacionDeHaceMasDe30DiasNo() {
		long colegio = colegioEnProduccion("Colegio Baja Vieja", EscenarioPlataforma.RUC_C, "titular.bajavieja",
				"45678998", "987650098");
		jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, produccion_por = NULL WHERE id = ?",
				colegio);
		jdbc.update("DELETE FROM configuracion_colegio WHERE colegio_id = ? AND clave = 'huella_correo_externo'", colegio);
		EscenarioPuestaEnMarcha.datos(jdbc, colegio, EnumSet.of(Nivel.PRIMARIA), usuario(colegio, "titular.bajavieja").getId());
		como(ana);
		fin.pedirExportacion(colegio, "Carta del colegio 2026-78");
		SecurityContextHolder.clearContext();
		recibir(colegio, "45678998");
		reloj.mover(Duration.ofDays(31));
		como(ana);
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.BAJA, MotivoCambioEstado.NO_ARRANCO, MOTIVO,
				null)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("30 días");
		SecurityContextHolder.clearContext();
	}
}
