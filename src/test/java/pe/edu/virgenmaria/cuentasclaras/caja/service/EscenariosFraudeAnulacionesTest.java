package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.MOTIVO_ANULACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.correccion;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.descuento;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA_2;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Los fraudes de anulaciones y descuentos que el sistema debe impedir (sprint 3, tanda 2). Cada prueba reproduce el
 * ataque; la defensa en MySQL (triggers y permisos por columna) se prueba además en {@code PermisosMySqlTest}.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EscenariosFraudeAnulacionesTest {

	/** Dentro de los 30 días de la ventana de {@code ControlParticipantes} (hoy es 02/10/2026). */
	private static final Timestamp HACE_UNA_SEMANA = Timestamp.valueOf("2026-09-25 10:00:00");

	private static final Timestamp HACE_UN_ANIO = Timestamp.valueOf("2025-10-01 10:00:00");

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private PagoRepository pagos;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long marzoMateo;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/**
	 * La cajera cobra en efectivo, pide anular para quedarse el dinero y trata de aprobarlo ella misma: no tiene acceso
	 * a la bandeja. Mientras nadie apruebe, el pago sigue vigente y el efectivo sigue esperándose en su caja.
	 */
	@Test
	void fraudeCajeraAnulaParaQuedarseElEfectivoNoPuedeAprobar() {
		como(CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "500.00"));
		Long caja = cajaDe(pago);
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);

		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> bandeja.rechazar(solicitud, "Me equivoqué al pedirlo, mejor lo dejo")).isInstanceOf(
				AccessDeniedException.class);
		assertThatThrownBy(bandeja::bandeja).isInstanceOf(AccessDeniedException.class);
		// Tampoco puede pedirlo dos veces ni anular el pago de otra cajera.
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION))
				.isInstanceOf(ReglaNegocioException.class);
		como(CAJA_2);
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION))
				.isInstanceOf(RecursoNoEncontradoException.class);

		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("VIGENTE");
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PAGADA");
		assertThat(efectivoEsperado(caja)).isEqualByComparingTo("450.00");
		assertThat(contar(jdbc, "anulacion_pago")).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE tipo = 'NOTA_CREDITO'", Long.class))
				.isZero();
	}

	/**
	 * Con la caja abierta, la devolución aprobada baja el efectivo esperado de esa caja: si la cajera se quedó el dinero,
	 * el cierre lo descubre como faltante.
	 */
	@Test
	void fraudeAnularEfectivoConCajaAbiertaBajaElEsperado() {
		como(CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "500.00"));
		Long otro = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), "450.00",
				"450.00"));
		Long caja = cajaDe(pago);
		assertThat(cajaDe(otro)).isEqualTo(caja);
		assertThat(efectivoEsperado(caja)).isEqualByComparingTo("900.00");

		como(CAJA);
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		assertThat(efectivoEsperado(caja)).as("pedir no cambia nada").isEqualByComparingTo("900.00");
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);

		assertThat(efectivoEsperado(caja)).isEqualByComparingTo("450.00");
		assertThat(jdbc.queryForObject("SELECT posterior_al_cierre FROM anulacion_pago WHERE pago_id = ?", Boolean.class,
				pago)).isFalse();
	}

	/**
	 * Quien aprueba prepara una segunda cuenta de caja (la creó hace una semana), cobra con ella y pide anular desde
	 * Administración: el sistema la trata como participante porque la cajera del pago usa una cuenta que él creó.
	 */
	@Test
	void fraudeSegundaCuentaCreadaPorQuienApruebaNoLePermiteAprobar() {
		Usuario cuentaNueva = cajeraGuardada("caja.nueva");
		jdbc.update("UPDATE usuario SET creado_por = 'director', creado_en = ? WHERE id = ?", HACE_UNA_SEMANA,
				cuentaNueva.getId());
		Long pago = cobrarComo(cuentaNueva);
		como(ADMINISTRACION);
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);

		como(DIRECCION);
		assertThat(bandeja.bandeja().pendientes()).singleElement().extracting(SolicitudVista::puedeResolver)
				.isEqualTo(false);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(AutoaprobacionSolicitudException.class)
				.hasMessageContaining("cuenta que creaste");
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("VIGENTE");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "AUTOAPROBACION_RECHAZADA")).containsEntry("nombre_usuario",
				"director");

		// Otra persona sí puede.
		EscenarioAprobaciones.aprueba(PROMOTORIA, bandeja, jdbc, "pago", pago);
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("ANULADO");
	}

	/** La misma maniobra con una cuenta antigua a la que quien aprueba le restableció la clave hace poco. */
	@Test
	void fraudeCuentaConClaveRestablecidaPorQuienApruebaNoLePermiteAprobar() {
		Usuario cuentaAntigua = cajeraGuardada("caja.antigua");
		jdbc.update("UPDATE usuario SET creado_por = 'director', creado_en = ?, clave_restablecida_por = 'promotor', "
				+ "clave_restablecida_en = ? WHERE id = ?", HACE_UN_ANIO, HACE_UNA_SEMANA, cuentaAntigua.getId());
		Long pago = cobrarComo(cuentaAntigua);
		// Esta vez la misma cajera pide la devolución.
		UsuariosDePrueba.iniciarSesion(usuarios.findById(cuentaAntigua.getId()).orElseThrow());
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);

		como(PROMOTORIA);
		assertThatThrownBy(() -> bandeja.aprobar(EscenarioAprobaciones.pendiente(jdbc, "pago", pago), null))
				.isInstanceOf(AutoaprobacionSolicitudException.class).hasMessageContaining("restableciste la clave");
		// Quien la creó hace un año ya no cuenta como participante.
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);
		assertThat(jdbc.queryForObject("SELECT aprobado_por FROM anulacion_pago WHERE pago_id = ?", String.class, pago))
				.isEqualTo("director");
	}

	/**
	 * La cajera «corrige» un pago para que el dinero de los Quispe pague la deuda de los Flores. Solo pasa con la
	 * aprobación de otra persona, y la bandeja lo resalta para que confirme con las dos familias.
	 */
	@Test
	void fraudeCorregirPagoHaciaOtraFamiliaSoloConAprobacionYResaltado() {
		como(CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		Long cuotaSebastian = cuota(jdbc, f.sebastian(), "PEN-2027-03");
		anulaciones.solicitarCorreccion(pago, correccion(f.flores(), List.of(cuotaSebastian)));

		// Pedirlo no mueve el dinero.
		assertThat(estado(jdbc, cuotaSebastian)).isEqualTo("PENDIENTE");
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PAGADA");
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "PAGO_ANULACION_SOLICITADA").get("detalle")).asString()
				.contains("OTRA familia");
		assertThatThrownBy(() -> bandeja.aprobar(EscenarioAprobaciones.pendiente(jdbc, "pago", pago), null))
				.isInstanceOf(AccessDeniedException.class);

		como(PROMOTORIA);
		SolicitudVista tarjeta = bandeja.bandeja().pendientes().getFirst();
		assertThat(tarjeta.advertencia()).isEqualTo("El dinero pasa a OTRA familia: confirma con ambas familias antes "
				+ "de aprobar.");
		assertThat(tarjeta.resumen()).contains("OTRA familia");
		assertThat(tarjeta.puedeResolver()).isTrue();

		EscenarioAprobaciones.aprueba(PROMOTORIA, bandeja, jdbc, "pago", pago);
		assertThat(estado(jdbc, cuotaSebastian)).isEqualTo("PAGADA");
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PENDIENTE");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "PAGO_REEMPLAZO_REGISTRADO")).containsEntry("nombre_usuario",
				"promotor");
	}

	/**
	 * Decisión 18: quien aprueba una devolución en efectivo ve el celular COMPLETO del apoderado para llamarlo antes de
	 * aprobar. La cajera no lo ve en sus pantallas y el número no queda en la bitácora.
	 */
	@Test
	void devolucionMuestraElCelularCompletoSoloAQuienAprueba() {
		como(CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "500.00"));
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		assertThat(cobro.pagosDelDia().toString()).doesNotContain("987");

		como(DIRECCION);
		List<String> detalle = bandeja.bandeja().pendientes().getFirst().detalle();
		assertThat(detalle).anyMatch(l -> l.startsWith("Antes de aprobar, llama al apoderado") && l.contains(
				"+51 987 654 321"));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);
		String bitacora = EscenarioEscolar.todaLaBitacora(jdbc);
		assertThat(bitacora).doesNotContain(EscenarioEscolar.CELULAR_ROSA).doesNotContain("987 654 321");
	}

	/** Dos personas aprueban la misma anulación en el mismo instante: una sola nota de crédito, una sola reversión. */
	@Test
	void dobleAprobacionConcurrenteDeUnaAnulacionSoloSeAplicaUnaVez() throws Exception {
		como(CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		anulaciones.solicitarCorreccion(pago, correccion(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03"))));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);

		List<Object> resultados = enParalelo(solicitud);

		assertThat(resultados).filteredOn("ok"::equals).hasSize(1);
		assertThat(resultados).filteredOn(ReglaNegocioException.class::isInstance).singleElement()
				.extracting(e -> ((Exception) e).getMessage()).asString().contains("ya fue aprobada");
		assertThat(contar(jdbc, "anulacion_pago")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante WHERE tipo = 'NOTA_CREDITO'", Long.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aplicacion_pago WHERE tipo = 'REVERSION'", Long.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE origen = 'REEMPLAZO'", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT CONCAT(serie, '-', numero) FROM comprobante ORDER BY id", String.class))
				.containsExactly("B001-1", "BC01-1", "B001-2");
	}

	/** Lo mismo con un descuento: los ajustes se registran una sola vez. */
	@Test
	void dobleAprobacionConcurrenteDeUnDescuentoSoloSeAplicaUnaVez() throws Exception {
		como(ADMINISTRACION);
		Long id = descuentos.solicitar(descuento(f.mateo(), TipoDescuento.HERMANOS, "10", List.of(marzoMateo)));

		List<Object> resultados = enParalelo(EscenarioAprobaciones.pendiente(jdbc, "descuento", id));

		assertThat(resultados).filteredOn("ok"::equals).hasSize(1);
		assertThat(resultados).filteredOn(ReglaNegocioException.class::isInstance).hasSize(1);
		assertThat(contar(jdbc, "ajuste_cuota")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT monto_descuento FROM cuota WHERE id = ?", BigDecimal.class, marzoMateo))
				.isEqualByComparingTo("45.00");
	}

	/** Administración se aprueba su propio descuento: ni ella ni quien le creó la cuenta hace poco pueden. */
	@Test
	void fraudeDescuentoAutoaprobadoNiConCuentaPreparada() {
		Usuario cuenta = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "administracion.nueva",
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION, Rol.PROMOTOR);
		jdbc.update("UPDATE usuario SET creado_por = 'promotor', creado_en = ? WHERE id = ?", HACE_UNA_SEMANA,
				cuenta.getId());
		UsuariosDePrueba.iniciarSesion(usuarios.findById(cuenta.getId()).orElseThrow());
		Long id = descuentos.solicitar(descuento(f.mateo(), TipoDescuento.BECA, "100", List.of(marzoMateo)));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "descuento", id);

		// Ella misma (también tiene el rol de Promotoría).
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(AutoaprobacionSolicitudException.class);
		// Quien le creó la cuenta hace una semana.
		como(PROMOTORIA);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(AutoaprobacionSolicitudException.class);
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("PENDIENTE");
		assertThat(contar(jdbc, "ajuste_cuota")).isZero();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'")).isEqualTo(2);

		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "descuento", id);
		assertThat(estado(jdbc, marzoMateo)).isEqualTo("EXONERADA");
	}

	/**
	 * Descuento sobre una cuota ya pagada: sería devolver dinero sin nota de crédito. No se puede pedir, y si la cuota
	 * se pagó mientras esperaba aprobación, no se aplica.
	 */
	@Test
	void fraudeDescuentoSobreCuotaYaPagada() {
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		como(ADMINISTRACION);
		Long id = descuentos.solicitar(descuento(f.mateo(), TipoDescuento.HERMANOS, "10", List.of(abril)));
		como(CAJA);
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		cobro.cobrar(efectivo(f.quispe(), List.of(abril), "450.00", "450.00"));

		como(ADMINISTRACION);
		assertThatThrownBy(() -> descuentos.solicitar(descuento(f.mateo(), TipoDescuento.HERMANOS, "10",
				List.of(marzoMateo)))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pendientes o parciales");
		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "descuento", id))
				.isInstanceOf(ReglaNegocioException.class).hasMessageStartingWith("El descuento cambió desde que se pidió");

		assertThat(contar(jdbc, "ajuste_cuota")).isZero();
		assertThat(jdbc.queryForObject("SELECT SUM(monto_descuento) FROM cuota", BigDecimal.class))
				.isEqualByComparingTo("0.00");
		assertThat(jdbc.queryForObject("SELECT estado FROM descuento WHERE id = ?", String.class, id))
				.isEqualTo("SOLICITADO");
	}

	private Usuario cajeraGuardada(String nombre) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, Rol.CAJA);
	}

	private Long cobrarComo(Usuario cajera) {
		UsuariosDePrueba.iniciarSesion(usuarios.findById(cajera.getId()).orElseThrow());
		return cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
	}

	private Long cajaDe(Long pago) {
		return jdbc.queryForObject("SELECT caja_diaria_id FROM pago WHERE id = ?", Long.class, pago);
	}

	/** Lo consulta Dirección (la cajera no ve el esperado: cierre ciego); deja la sesión en Dirección. */
	private BigDecimal efectivoEsperado(Long caja) {
		como(DIRECCION);
		BigDecimal efectivo = new TransactionTemplate(transacciones).execute(t -> pagos.efectivoVigente(caja));
		return efectivo == null ? BigDecimal.ZERO : efectivo;
	}

	/** Dirección y Promotoría aprueban la misma solicitud a la vez; devuelve «ok» o la excepción de cada una. */
	private List<Object> enParalelo(Long solicitud) throws InterruptedException {
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch largada = new CountDownLatch(1);
			List<Future<String>> futuros = new ArrayList<>();
			for (UsuarioAutenticado quien : List.of(DIRECCION, PROMOTORIA)) {
				futuros.add(hilos.submit(aprobarComo(quien, largada, solicitud)));
			}
			largada.countDown();
			List<Object> resultados = new ArrayList<>();
			for (Future<String> futuro : futuros) {
				try {
					resultados.add(futuro.get(30, TimeUnit.SECONDS));
				}
				catch (ExecutionException e) {
					resultados.add(e.getCause());
				}
				catch (java.util.concurrent.TimeoutException e) {
					resultados.add(e);
				}
			}
			return resultados;
		}
		finally {
			hilos.shutdownNow();
		}
	}

	private Callable<String> aprobarComo(UsuarioAutenticado quien, CountDownLatch largada, Long solicitud) {
		return () -> {
			largada.await();
			como(quien);
			try {
				bandeja.aprobar(solicitud, null);
				return "ok";
			}
			finally {
				SecurityContextHolder.clearContext();
			}
		};
	}
}
