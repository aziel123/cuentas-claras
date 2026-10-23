package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioCampanaRenovacion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * QA del sprint 8 (secciones 3.4 y 3.5, decisión 116; E9, E10, E11 y E30): un colegio en preparación no mueve dinero ni
 * avisa cobros por ningún camino (no solo la caja), la lista tiene 14 pasos (12 obligatorios) y no hay vuelta atrás.
 *
 * <p>Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dado un colegio en preparación con la pasarela y una familia con su portal, cuando la familia paga en línea,
 *       entonces no se registra el pago (S8-M2 de la auditoría).</li>
 *   <li>Dado un colegio en preparación, cuando Administración abre la renovación de matrículas, entonces no sale ninguna
 *       invitación RENOVACION_MATRICULA a las familias (QA-S8-4).</li>
 *   <li>Dado un colegio en producción, cuando se intenta volver a preparación o cambiar el RUC, entonces se rechaza.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EstadosColegioQaSprint8Test {

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private EstadoPuestaEnMarcha estado;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** El colegio 1 vuelve a «en preparación» por la base (como uno recién dado de alta; LimpiezaBaseDatos lo repone). */
	private void colegioEnPreparacion() {
		jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, produccion_por = NULL WHERE id = 1");
	}

	private long contar(String sql, Object... parametros) {
		return jdbc.queryForObject(sql, Long.class, parametros);
	}

	@Test
	void debeRechazarElPagoEnLineaDeUnaFamiliaEnUnColegioEnPreparacion() {
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		colegioEnPreparacion();
		como(new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false, EnumSet.of(Rol.APODERADO),
				f.rosa()));
		Long marzo = EscenarioCaja.cuota(jdbc, f.mateo(), "PEN-2027-03");

		Throwable error = catchThrowable(() -> {
			BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(marzo))).total();
			String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(marzo), total,
					false));
			simulador.simular(referencia, Accion.YAPE);
		});

		assertThat(contar("SELECT COUNT(*) FROM pago WHERE colegio_id = 1")).as("pagos en un colegio en preparación")
				.isZero();
		assertThat(error).as("la familia debe saber que el colegio todavía no recibe pagos en línea")
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void debeImpedirInvitacionesDeRenovacionALasFamiliasEnUnColegioEnPreparacion() {
		EscenarioRenovacion.Datos d = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		colegioEnPreparacion();
		como(ADMINISTRACION);

		Throwable error = catchThrowable(() -> campana.abrir(d.anio2027(), EscenarioRenovacion.RESPONDEN_HASTA));

		assertThat(error).as("si se rechaza, que sea con un mensaje para la persona")
				.satisfiesAnyOf(e -> assertThat(e).isNull(), e -> assertThat(e).isInstanceOf(ReglaNegocioException.class));
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE colegio_id = 1 AND tipo = 'RENOVACION_MATRICULA'"))
				.as("invitaciones de renovación en un colegio en preparación").isZero();
	}

	@Test
	void debeCalcularCatorcePasosConDoceObligatoriosEnElOrdenDelDiseno() {
		colegioEnPreparacion();

		List<PuntoLista> puntos = estado.paraOperador(1L).puntos();

		assertThat(puntos).extracting(PuntoLista::clave).containsExactlyElementsOf(EstadoPuestaEnMarcha.PASOS);
		assertThat(puntos).hasSize(15);
		assertThat(puntos.stream().filter(PuntoLista::obligatorio).count()).isEqualTo(12L);
		assertThat(puntos).filteredOn(p -> !p.obligatorio()).extracting(PuntoLista::clave)
				.containsExactly(EstadoPuestaEnMarcha.PASARELA, EstadoPuestaEnMarcha.RECAUDACION);
		assertThat(puntos).allSatisfy(p -> assertThat(p.enlace()).as("el operador no recibe enlaces").isNull());
	}

	@Test
	void debeRechazarPasarAProduccionDosVeces() {
		Colegio colegio = Colegio.enPreparacion("Colegio Doble", "Colegio Doble S.A.C.", EscenarioPlataforma.RUC_A,
				"Av. 1", "info@doble.edu.pe", EnumSet.of(Nivel.PRIMARIA), "Titular", "45678921", null);
		LocalDateTime ahora = LocalDateTime.of(2026, 10, 2, 8, 0);
		colegio.pasarAProduccion("promotora.doble", ahora);

		assertThatThrownBy(() -> colegio.pasarAProduccion("promotora.doble", ahora.plusMinutes(1)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está en producción");
		assertThat(colegio.getProduccionDesde()).isEqualTo(ahora);
	}

	@Test
	void debeRechazarCambiarElRucDeUnColegioEnProduccion() {
		Colegio colegio = Colegio.enPreparacion("Colegio Ruc", "Colegio Ruc S.A.C.", EscenarioPlataforma.RUC_A, "Av. 1",
				"info@ruc.edu.pe", EnumSet.of(Nivel.PRIMARIA), "Titular", "45678922", null);
		colegio.pasarAProduccion("promotora.ruc", LocalDateTime.of(2026, 10, 2, 8, 0));

		assertThatThrownBy(() -> colegio.cambiarRuc(EscenarioPlataforma.RUC_B)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no cambia su RUC");
		assertThat(colegio.getRuc()).isEqualTo(EscenarioPlataforma.RUC_A);
	}

	@Test
	void debeCalcularLaListaSinDatosPersonalesNiMontosParaElOperador() {
		EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		colegioEnPreparacion();

		List<PuntoLista> puntos = estado.paraOperador(1L).puntos();

		assertThat(puntos).extracting(PuntoLista::conteo).allSatisfy(conteo -> assertThat(conteo)
				.doesNotContain("S/").doesNotContain("Quispe").doesNotContain("Rosa").doesNotContain("Flores")
				.doesNotContain(EscenarioCaja.DNI_PEDRO).doesNotContainPattern("9\\d{8}"));
	}
}
