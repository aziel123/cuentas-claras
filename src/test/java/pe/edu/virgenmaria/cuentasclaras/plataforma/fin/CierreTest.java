package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.ConservacionDatos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 2 (3.10, AP-11 y AP2-2): el CIERRE (SUSPENDIDO → CERRADO) lo deciden dos operadores, con la exportación
 * del colegio generada después de la suspensión y ENTREGADA, o LISTA o VENCIDA con 30 días. La base pone
 * {@code cerrado_en} y {@code conservar_hasta}: la suspensión aplicada + 12 meses con la autorización del titular; si no,
 * el cierre + 30 días.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CierreTest extends BaseFinColegio {

	@org.springframework.beans.factory.annotation.Autowired
	private ConservacionDatos conservacion;

	@Test
	void sinLaExportacionEntregadaNiSus30DiasNoSeCierra() {
		long colegio = colegioEnProduccion("Colegio Sin Entrega", EscenarioPlataforma.RUC_A, "titular.sinentrega",
				"45678961", "987650061");
		// Un colegio en producción no se cierra: primero se suspende.
		como(ana);
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.CIERRE, MotivoCambioEstado.FALTA_PAGO,
				"El colegio no pagó y recibió su exportación: se cierra.", REFERENCIA))
				.isInstanceOf(ReglaNegocioException.class);
		suspender(colegio);
		como(ana);
		assertThatThrownBy(() -> cambios.proponer(colegio, TipoCambioEstado.CIERRE, MotivoCambioEstado.FALTA_PAGO,
				"El colegio no pagó y recibió su exportación: se cierra.", REFERENCIA))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("exportación");
		SecurityContextHolder.clearContext();
		// LISTA con 30 días: el colegio tuvo la oportunidad de recuperarla (L6).
		reloj.mover(Duration.ofDays(30).plusHours(1));
		cerrar(colegio);
		assertThat(estado(colegio)).isEqualTo("CERRADO");
		assertThat(jdbc.queryForObject("SELECT exportacion_id FROM cambio_estado_colegio WHERE tipo = 'CIERRE' AND "
				+ "colegio_id = ?", Long.class, colegio)).isEqualTo(((Number) exportacion(colegio).get("id")).longValue());
	}

	@Test
	void conLaEntregadaSeCierraYSinAutorizacionSeConservan30Dias() {
		long colegio = colegioEnProduccion("Colegio Cierre", EscenarioPlataforma.RUC_B, "titular.cierre", "45678962",
				"987650062");
		suspender(colegio);
		recibir(colegio, "45678962");
		reloj.mover(Duration.ofDays(3));
		cerrar(colegio);

		Map<String, Object> c = jdbc.queryForMap("SELECT estado, estado_desde, cerrado_en, conservar_hasta FROM colegio "
				+ "WHERE id = ?", colegio);
		assertThat(c.get("estado")).isEqualTo("CERRADO");
		assertThat(c.get("cerrado_en")).isEqualTo(c.get("estado_desde"));
		assertThat(((Date) c.get("conservar_hasta")).toLocalDate())
				.isEqualTo(((Timestamp) c.get("cerrado_en")).toLocalDateTime().toLocalDate().plusDays(30));
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("COLEGIO_CERRADO");
	}

	@Test
	void conLaAutorizacionDelTitularSeConservan12MesesDesdeLaSuspension() {
		long colegio = colegioEnProduccion("Colegio Doce Meses", EscenarioPlataforma.RUC_C, "titular.doce", "45678963",
				"987650063");
		Usuario titular = usuario(colegio, "titular.doce");
		ingresar(titular);
		ContextoColegio.en(colegio, () -> conservacion.autorizar());
		SecurityContextHolder.clearContext();
		suspender(colegio);
		recibir(colegio, "45678963");
		cerrar(colegio);

		Map<String, Object> c = jdbc.queryForMap("SELECT suspendido_en, conservar_hasta FROM colegio WHERE id = ?", colegio);
		assertThat(((Date) c.get("conservar_hasta")).toLocalDate())
				.isEqualTo(((Timestamp) c.get("suspendido_en")).toLocalDateTime().toLocalDate().plusMonths(12));
	}
}
