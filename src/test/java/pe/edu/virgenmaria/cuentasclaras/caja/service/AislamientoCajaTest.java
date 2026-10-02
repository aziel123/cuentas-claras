package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Pagos, cajas y comprobantes de un colegio no existen para otro: ni por los servicios (404) ni en la base (FK
 * compuestas con colegio_id). La cajera del colegio B se llama igual que la del A («caja») a propósito.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoCajaTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias a;

	private Long pagoA;

	private long colegioB;

	private UsuarioAutenticado cajaB;

	private Long familiaB;

	private Long pagoB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		a = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(EscenarioCaja.CAJA);
		pagoA = cobro.cobrar(efectivo(a.quispe(), List.of(cuota(jdbc, a.mateo(), "PEN-2027-03")), "450.00", "500.00"));

		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		UsuarioAutenticado administracionB = UsuariosDePrueba.autenticado(colegioB, 91L, "administracion.b",
				"Administración B", false, EnumSet.of(Rol.ADMINISTRACION));
		UsuarioAutenticado directorB = UsuariosDePrueba.autenticado(colegioB, 90L, "director.b", "Director B", false,
				EnumSet.of(Rol.DIRECTOR));
		cajaB = UsuariosDePrueba.autenticado(colegioB, 92L, "caja", "Caja B", false, EnumSet.of(Rol.CAJA));
		como(administracionB);
		var escuelaB = EscenarioEscolar.crearEstructura(estructura);
		var alumnoB = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuelaB.primaria6A2027()));
		familiaB = alumnoB.familiaId();
		Long planB = planes.crearBorrador(escuelaB.anio2027(), Nivel.PRIMARIA, EscenarioCobranza.plan(2027, "380", "300",
				null));
		planes.enviar(planB);
		como(directorB);
		planes.aprobar(planB, planes.obtener(planB).version());
		como(cajaB);
		pagoB = cobro.cobrar(efectivo(familiaB, List.of(cuota(jdbc, alumnoB.alumnoId(), "PEN-2027-03")), "380.00",
				"400.00"));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void colegioBNoVePagosCajasNiComprobantesDelA() {
		como(cajaB);
		// Encuentra a «su» Mateo (mismo DNI, otro colegio), nunca a los del A.
		assertThat(cobro.buscar("quispe").resultados()).extracting(r -> r.familiaId()).containsOnly(familiaB);
		assertThat(cobro.buscar(EscenarioCaja.DNI_SEBASTIAN).resultados()).isEmpty();
		assertThatThrownBy(() -> cobro.cuentaDeFamilia(a.quispe())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> cobro.revisar(a.quispe(), new SeleccionCobroRequest(List.of(cuota(jdbc, a.mateo(),
				"PEN-2027-04")), MedioPago.YAPE), null)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> cobro.confirmacion(pagoA)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> cobro.imprimible(pagoA)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(cobro.pagosDelDia().pagos()).extracting(p -> p.id()).containsExactly(pagoB);
		// Cobrar cuotas del A desde el B: no existen para él.
		assertThatThrownBy(() -> cobro.cobrar(efectivo(familiaB, List.of(cuota(jdbc, a.mateo(), "PEN-2027-04")),
				"450.00", "450.00"))).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(EscenarioCaja.estado(jdbc, cuota(jdbc, a.mateo(), "PEN-2027-04"))).isEqualTo("PENDIENTE");
	}

	@Test
	void cajaDelColegioBRecibe404AlAbrirPagoDelA() {
		// Y al revés: la cajera del A no ve el pago del B aunque tenga el mismo nombre de usuario.
		como(EscenarioCaja.CAJA);
		assertThatThrownBy(() -> cobro.confirmacion(pagoB)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> cobro.imprimible(pagoB)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(cobro.pagosDelDia().pagos()).extracting(p -> p.id()).containsExactly(pagoA);
	}

	@Test
	void laBaseRechazaPagoConFamiliaDeOtroColegio() {
		String copia = "INSERT INTO pago (colegio_id, familia_id, caja_diaria_id, cajero, fecha, comprobante_id, medio, total, "
				+ "recibido, vuelto, origen, clave_idempotencia, estado, creado_en, creado_por, actualizado_en) "
				+ "SELECT colegio_id, %s, caja_diaria_id, cajero, fecha, comprobante_id, medio, total, recibido, vuelto, "
				+ "origen, 'copia', estado, creado_en, creado_por, actualizado_en FROM pago WHERE id = ?";
		// El pago del B con la familia del A: la FK compuesta (familia_id, colegio_id) lo rechaza.
		assertThatThrownBy(() -> jdbc.update(String.format(copia, a.quispe()), pagoB))
				.isInstanceOf(DataIntegrityViolationException.class);
		// Una aplicación del pago del B a una cuota del A, tampoco.
		assertThatThrownBy(() -> jdbc.update("INSERT INTO aplicacion_pago (colegio_id, pago_id, cuota_id, tipo, monto, "
				+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, 'APLICACION', 1, CURRENT_TIMESTAMP, 'x', "
				+ "CURRENT_TIMESTAMP)", colegioB, pagoB, cuota(jdbc, a.mateo(), "PEN-2027-04")))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Long.class)).isEqualTo(2);
	}

	@Test
	void mismaSerieB001EnDosColegiosNoChoca() {
		assertThat(jdbc.queryForList("SELECT CONCAT(colegio_id, ':', serie, '-', numero) FROM comprobante ORDER BY colegio_id",
				String.class)).containsExactly("1:B001-1", colegioB + ":B001-1");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM serie_comprobante WHERE serie = 'B001'", Long.class))
				.isEqualTo(2);
		como(cajaB);
		assertThat(cobro.confirmacion(pagoB).comprobante()).isEqualTo("B001-00000001");
	}
}
