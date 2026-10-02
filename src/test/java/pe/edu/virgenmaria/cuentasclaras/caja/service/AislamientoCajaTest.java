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
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
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
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioEstadoCuenta estadoCuenta;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioCierreCaja cierres;

	@Autowired
	private ConsultaCajas consulta;

	@Autowired
	private ServicioVerificacionBancaria verificacion;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias a;

	private Long pagoA;

	private long colegioB;

	private UsuarioAutenticado cajaB;

	private UsuarioAutenticado administracionB;

	private UsuarioAutenticado directorB;

	private Long familiaB;

	private Long pagoB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		a = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(EscenarioCaja.CAJA);
		pagoA = cobro.cobrar(efectivo(a.quispe(), List.of(cuota(jdbc, a.mateo(), "PEN-2027-03")), "450.00", "500.00"));

		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		administracionB = UsuariosDePrueba.autenticado(colegioB, 91L, "administracion.b",
				"Administración B", false, EnumSet.of(Rol.ADMINISTRACION));
		directorB = UsuariosDePrueba.autenticado(colegioB, 90L, "director.b", "Director B", false,
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

	/** Tanda 2: anulaciones, descuentos, estado de cuenta y comprobantes del A no existen para el B (404). */
	@Test
	void colegioBNoVeAnulacionesDescuentosNiEstadoDeCuentaDelA() {
		Long marzoValeria = cuota(jdbc, a.valeria(), "PEN-2027-03");
		como(EscenarioCaja.CAJA);
		anulaciones.solicitarDevolucion(pagoA, EscenarioAprobaciones.MOTIVO_ANULACION);
		como(EscenarioCobranza.ADMINISTRACION);
		Long descuentoA = descuentos.solicitar(EscenarioAprobaciones.descuento(a.valeria(), TipoDescuento.HERMANOS, "10",
				List.of(marzoValeria)));
		Long comprobanteA = jdbc.queryForObject("SELECT comprobante_id FROM pago WHERE id = ?", Long.class, pagoA);

		como(cajaB);
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(pagoA, EscenarioAprobaciones.MOTIVO_ANULACION))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> anulaciones.prepararCorreccion(pagoA, null, null))
				.isInstanceOf(RecursoNoEncontradoException.class);
		como(administracionB);
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(pagoA, EscenarioAprobaciones.MOTIVO_ANULACION))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> estadoCuenta.deAlumno(a.mateo())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> estadoCuenta.comprobante(comprobanteA)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(descuentos.lista()).isEmpty();
		assertThatThrownBy(() -> descuentos.solicitar(EscenarioAprobaciones.descuento(a.valeria(), TipoDescuento.BECA,
				"100", List.of(marzoValeria)))).isInstanceOf(RecursoNoEncontradoException.class);
		// Con el DNI de Valeria del A no encuentra a nadie: en el B solo está su Mateo.
		assertThatThrownBy(() -> descuentos.prepararSolicitud(jdbc.queryForObject("SELECT numero_documento FROM alumno "
				+ "WHERE id = ?", String.class, a.valeria()))).isInstanceOf(ReglaNegocioException.class)
				.hasMessage("No hay un alumno con ese documento.");
		como(directorB);
		assertThat(bandeja.bandeja().pendientes()).isEmpty();
		assertThatThrownBy(() -> bandeja.aprobar(EscenarioAprobaciones.pendiente(jdbc, "pago", pagoA), null))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> bandeja.aprobar(EscenarioAprobaciones.pendiente(jdbc, "descuento", descuentoA), null))
				.isInstanceOf(RecursoNoEncontradoException.class);

		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pagoA)).isEqualTo("VIGENTE");
		assertThat(jdbc.queryForObject("SELECT estado FROM descuento WHERE id = ?", String.class, descuentoA))
				.isEqualTo("SOLICITADO");
	}

	/** Tanda 3: cierres, depósitos, verificaciones y cajas del día del A no existen para el B. */
	@Test
	void colegioBNoVeCierresDepositosNiVerificacionesDelA() {
		como(EscenarioCaja.CAJA);
		Long yapeA = cobro.cobrar(EscenarioCaja.digital(a.quispe(), List.of(cuota(jdbc, a.valeria(), "PEN-2027-03")),
				pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.YAPE, "YP-A-0001", "450.00"));
		cierres.contar(new pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest(new java.math.BigDecimal("450.00"),
				null));
		var estadoA = cierres.estado();
		Long cajaA = estadoA.porDepositar().getFirst().cajaId();
		cierres.registrarDeposito(new pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest(cajaA,
				estadoA.cuentas().getFirst(), "OP-A-0001", estadoA.hoy(), new java.math.BigDecimal("450.00"), null));
		Long depositoA = jdbc.queryForObject("SELECT id FROM deposito_caja", Long.class);

		// La cajera del B (mismo nombre de usuario «caja») no ve ni toca la caja del A.
		como(cajaB);
		var estadoB = cierres.estado();
		assertThat(estadoB.porDepositar()).isEmpty();
		assertThat(estadoB.porCerrar().id()).isNotEqualTo(cajaA);
		assertThatThrownBy(() -> cierres.registrarDeposito(new pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest(
				cajaA, estadoB.cuentas().getFirst(), "OP-B-0001", estadoB.hoy(), new java.math.BigDecimal("450.00"), null)))
				.isInstanceOf(RecursoNoEncontradoException.class);
		como(directorB);
		assertThat(consulta.delDia(null).cajas()).extracting(c -> c.id()).doesNotContain(cajaA);
		assertThatThrownBy(() -> consulta.detalle(cajaA)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(bandeja.bandeja().pendientes()).noneMatch(s -> s.tipo().equals("CIERRE_CAJA"));
		como(administracionB);
		var vistaB = verificacion.vista();
		assertThat(vistaB.pagos()).extracting(p -> p.id()).doesNotContain(yapeA);
		assertThat(vistaB.depositos()).isEmpty();
		assertThatThrownBy(() -> verificacion.verificarPago(yapeA, new pe.edu.virgenmaria.cuentasclaras.caja.dto
				.VerificacionRequest(pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion.ENCONTRADO, null)))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> verificacion.verificarDeposito(depositoA, new pe.edu.virgenmaria.cuentasclaras.caja.dto
				.VerificacionRequest(pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion.ENCONTRADO, null)))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verificacion_bancaria", Long.class)).isZero();
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
