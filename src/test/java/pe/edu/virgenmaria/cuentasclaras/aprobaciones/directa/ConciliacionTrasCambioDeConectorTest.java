package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.extracto;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.CORREO_CONTROL;

/**
 * Sprint 11, tanda 1c (A1c-4): quien cambió la cuenta bancaria del colegio (la propuso o la aprobó) no confirma el extracto
 * en los 30 días siguientes: así, una cuenta cambiada en silencio no se «concilia» sola. El intento queda en la bitácora y
 * avisa a todas las Promotorías y al correo de control; la confirma otra persona.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ConciliacionTrasCambioDeConectorTest extends BaseDirecta {

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	private Long cuentaId;

	@BeforeEach
	void preparar() {
		EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		cuentaId = EscenarioConciliacion.cuenta(cuentas);
		SecurityContextHolder.clearContext();
		// La cuenta bancaria del conector la propuso la Promotoría hace unos días y la aprobó al instante su socia.
		jdbc.update("INSERT INTO conector_colegio (colegio_id, tipo, proveedor, modo, configuracion, clave_publica, estado, "
				+ "vigente, propuesto_por, resuelto_por, resuelto_en, prueba_ok, creado_en, creado_por, actualizado_en) VALUES "
				+ "(1, 'CUENTA_BANCARIA', 'BCP', 'REAL', '{}', ?, 'VIGENTE', TRUE, 'promotora.dir', 'socia.dir', ?, TRUE, "
				+ "NOW(), 'promotora.dir', NOW())", java.util.UUID.randomUUID().toString(),
				java.time.LocalDateTime.now(reloj).minusDays(3));
	}

	private void confirmar(Usuario quien, EscenarioConciliacion.Extracto banco) {
		try {
			EscenarioConciliacion.confirmar(extractos, UsuariosDePrueba.autenticado(quien), cuentaId,
					banco.saldoFinal().toPlainString());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void quienCambioLaCuentaNoConfirmaElExtractoPeroOtraPromotoriaSi() {
		Usuario operador = pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma.operador(usuarios, codificador,
				colegios, "operador.conciliacion");
		var banco = extracto("10000.00").abono("2026-10-01", "ABONO A", "", "100.00");
		Long id = EscenarioConciliacion.registrar(extractos, UsuariosDePrueba.autenticado(e.administracion()), banco);
		SecurityContextHolder.clearContext();

		assertThatThrownBy(() -> confirmar(e.promotora(), banco)).isInstanceOf(AutoaprobacionException.class)
				.hasMessageContaining("30 días");
		// La socia la aprobó: también cambió la cuenta.
		assertThatThrownBy(() -> confirmar(e.socia(), banco)).isInstanceOf(AutoaprobacionException.class);
		// Dos en el colegio y dos en la bitácora de la plataforma (el aviso a los operadores).
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = "
				+ "'CONCILIACION_TRAS_CAMBIO_RECHAZADA' AND colegio_id = 1", Long.class)).isEqualTo(2L);
		assertThat(EscenarioDirecta.eventos(jdbc, "CONCILIACION_TRAS_CAMBIO_RECHAZADA")).isEqualTo(4L);
		assertThat(EscenarioDirecta.destinos(jdbc, "CONCILIACION_BLOQUEADA")).contains(CORREO_CONTROL);
		// Sprint 11, tanda 2 (pendiente de la 1c): también los operadores de la plataforma.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE plantilla = 'CONCILIACION_BLOQUEADA' AND "
				+ "usuario_id = ?", Long.class, operador.getId())).isEqualTo(2L);
		assertThat(EscenarioConciliacion.estadoExtracto(jdbc, id)).isNotEqualTo("CONFIRMADO");

		// Otra persona (Dirección) sí lo confirma.
		confirmar(e.directora(), banco);
		assertThat(EscenarioConciliacion.estadoExtracto(jdbc, id)).isEqualTo("CONFIRMADO");
	}
}
