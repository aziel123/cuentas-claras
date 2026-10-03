package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCierreVista;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReembolsoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CausaDevolucion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.MOTIVO_ANULACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Los ataques de la auditoría antifraude del sprint 3 (correcciones C1, A1, A2, A4, B3 y M3). Cada prueba reproduce el
 * ataque y falla si se quita la corrección. La defensa en MySQL (UNIQUE, CHECK y triggers de V12 y 03-triggers.sql) se
 * prueba además en {@code PermisosMySqlTest}.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EscenariosFraudeCorreccionesTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	/** Viernes 2 de octubre de 2026, el día de la prueba. */
	private static final LocalDate HOY = LocalDate.of(2026, 10, 2);

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioVerificacionBancaria verificacion;

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private ServicioEstadoCuenta estadoCuenta;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private AlertasCaja alertas;

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

	private Familias f;

	private Long marzoMateo;

	private Long marzoValeria;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		como(CAJA);
	}

	@AfterEach
	void limpiar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	// ------------------------------------------------------------------ C1

	/** C1: el mismo número con otro formato (ceros, guiones, espacios, minúsculas) o en otro medio digital se rechaza. */
	@Test
	void c1MismoNumeroDeOperacionConOtroFormatoOEnOtroMedioEsRechazado() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "yp-0012345", "450.00"));
		assertThat(jdbc.queryForObject("SELECT numero_operacion FROM pago WHERE id = ?", String.class, yape))
				.isEqualTo("YP0012345");
		for (var intento : List.of("YP 0012345", "yp0012345", "YP-001-2345")) {
			assertThatThrownBy(() -> cobro.cobrar(digital(f.quispe(), List.of(marzoValeria), MedioPago.PLIN, intento,
					"450.00"))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está registrado");
		}
		Long otro = cobro.cobrar(digital(f.quispe(), List.of(marzoValeria), MedioPago.TRANSFERENCIA, "000777888",
				"450.00"));
		assertThat(jdbc.queryForObject("SELECT numero_operacion FROM pago WHERE id = ?", String.class, otro))
				.isEqualTo("777888");
		Long abril = cuota(jdbc, f.mateo(), "PEN-2027-04");
		assertThatThrownBy(() -> cobro.cobrar(digital(f.quispe(), List.of(abril), MedioPago.TARJETA, "777-888", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está registrado");
		assertThat(contar(jdbc, "pago")).isEqualTo(2);
	}

	/** C1: dos depósitos con el mismo voucher en otro formato: el segundo se rechaza. */
	@Test
	void c1MismoVoucherEnDosDepositosEsRechazado() {
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		cerrarConLoEsperado("450.00");
		EstadoCierreVista estado = cierre.estado();
		cierre.registrarDeposito(new DepositoRequest(estado.porDepositar().getFirst().cajaId(),
				estado.cuentas().getFirst(), "00-4455-66", HOY, new BigDecimal("450.00"), null));
		// Al día siguiente, la caja del lunes intenta reutilizar el voucher (otro formato).
		reloj.fijar(ZonedDateTime.of(2026, 10, 5, 8, 0, 0, 0, LIMA).toInstant());
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00"));
		cerrarConLoEsperado("450.00");
		EstadoCierreVista lunes = cierre.estado();
		assertThatThrownBy(() -> cierre.registrarDeposito(new DepositoRequest(lunes.porDepositar().getFirst().cajaId(),
				lunes.cuentas().getFirst(), "445566", LocalDate.of(2026, 10, 5), new BigDecimal("450.00"), null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está registrado en otro depósito");
		assertThat(contar(jdbc, "deposito_caja")).isEqualTo(1);
	}

	/**
	 * C1 en la base (V12): aunque alguien se saltara la aplicación, el UNIQUE de la forma canónica cubre todos los medios
	 * digitales juntos y los vouchers de depósito, y un CHECK exige la forma canónica.
	 */
	@Test
	void c1UnicidadYFormaCanonicaTambienEnLaBase() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "77665544", "450.00"));
		Long plin = cobro.cobrar(digital(f.quispe(), List.of(marzoValeria), MedioPago.PLIN, "77665545", "450.00"));
		assertThatThrownBy(() -> jdbc.update("UPDATE pago SET numero_operacion = '77665544', "
				+ "operacion_vigente = '77665544' WHERE id = ?", plin))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		for (String noCanonico : List.of("0077665546", "776-655-46", "abcd1")) {
			assertThatThrownBy(() -> jdbc.update("UPDATE pago SET numero_operacion = ?, operacion_vigente = NULL "
					+ "WHERE id = ?", noCanonico, yape)).as(noCanonico)
					.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		}
	}

	/**
	 * C1: la verificación es a ciegas: la vista no trae el número ni el monto registrados; lo escrito que no coincide no
	 * se guarda y queda en la bitácora (y como alerta para Promotoría). Un número a un carácter de otro se marca.
	 */
	@Test
	void c1VerificacionAciegasYNumerosParecidos() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "55443322", "450.00"));
		Long plin = cobro.cobrar(digital(f.quispe(), List.of(marzoValeria), MedioPago.PLIN, "55443323", "450.00"));

		como(ADMINISTRACION);
		var vista = verificacion.vista();
		assertThat(vista.pagos()).extracting(p -> p.id()).containsExactlyInAnyOrder(yape, plin);
		assertThat(vista.pagos()).allSatisfy(p -> assertThat(p.parecido()).isNotNull());
		assertThat(vista.pagos().stream().map(Object::toString)).noneMatch(t -> t.contains("55443322")
				|| t.contains("450"));
		// Lo que la cajera registró «para que cuadre» no basta: hay que escribir lo que se ve en el banco.
		for (var intento : List.of(VerificacionRequest.delBanco("55443323", HOY, new BigDecimal("450.00")),
				VerificacionRequest.delBanco("55443322", HOY, new BigDecimal("45.00")),
				VerificacionRequest.delBanco("55443322", HOY.minusDays(1), new BigDecimal("450.00")),
				VerificacionRequest.delBanco("55443322", HOY.plusDays(4), new BigDecimal("450.00")))) {
			assertThatThrownBy(() -> verificacion.verificarPago(yape, intento))
					.isInstanceOf(ServicioVerificacionBancaria.DatosBancoNoCoincidenException.class)
					.hasMessageNotContaining("monto no").hasMessageContaining("no coincide");
		}
		assertThat(contar(jdbc, "verificacion_bancaria")).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'VERIFICACION_NO_COINCIDE'",
				Long.class)).isEqualTo(4);
		verificacion.verificarPago(yape, VerificacionRequest.delBanco("55-443-322", HOY.plusDays(1),
				new BigDecimal("450")));
		assertThat(jdbc.queryForMap("SELECT resultado, banco_operacion, banco_fecha FROM verificacion_bancaria"))
				.containsEntry("resultado", "ENCONTRADO").containsEntry("banco_operacion", "55443322");

		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().startsWith("4 verificación(es) bancaria(s) de hoy no coincidieron"));
	}

	// ------------------------------------------------------------------ A1

	/** A1: un Yape inventado no sale de la conciliación pidiendo su devolución (ni antes ni al aprobar). */
	@Test
	void a1YapeSinVerificarNoSePuedeAnular() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "99887766", "450.00"));

		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(yape, MOTIVO_ANULACION))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("todavía no está verificado");
		assertThatThrownBy(() -> anulaciones.solicitarCorreccion(yape, EscenarioAprobaciones.correccion(f.quispe(),
				List.of(marzoValeria)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("todavía no está verificado");
		como(ADMINISTRACION);
		verificacion.verificarPago(yape, VerificacionRequest.noAparece("No figura en el Yape empresarial del 02/10"));
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(yape, MOTIVO_ANULACION))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("todavía no está verificado");
		assertThat(contar(jdbc, "solicitud_cambio")).isZero();
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("NO aparece en el banco"));
	}

	/** A1: la verificación se vuelve a exigir al aprobar (por ejemplo, si alguien borró la fila en la base). */
	@Test
	void a1AprobarRevalidaQueElPagoDigitalEsteVerificado() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "99887767", "450.00"));
		EscenarioCaja.verificadoEnBanco(verificacion, jdbc, yape);
		como(CAJA);
		anulaciones.solicitarDevolucion(yape, MOTIVO_ANULACION);
		como(DIRECCION);
		assertThat(bandeja.bandeja().pendientes().getFirst().detalle())
				.anyMatch(l -> l.startsWith("Verificación bancaria: Encontrado por administracion"));
		// En H2 no hay triggers que lo impidan: simula una base manipulada.
		jdbc.update("DELETE FROM verificacion_bancaria WHERE pago_id = ?", yape);
		assertThat(bandeja.bandeja().pendientes().getFirst().detalle())
				.anyMatch(l -> l.startsWith("Verificación bancaria: SIN VERIFICAR"));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", yape);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("todavía no está verificado");
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, yape)).isEqualTo("VIGENTE");
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("PENDIENTE");
	}

	/**
	 * A1: la devolución de un Yape verificado deja una alerta CRÍTICA hasta que Administración registra el reembolso a
	 * la cuenta de origen con su número de operación; la cajera no puede registrarlo.
	 */
	@Test
	void a1DevolucionDigitalExigeReembolsoALaCuentaDeOrigenRegistradoPorAdministracion() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "11223344", "450.00"));
		EscenarioCaja.verificadoEnBanco(verificacion, jdbc, yape);
		como(CAJA);
		anulaciones.solicitarDevolucion(yape, CausaDevolucion.COBRO_EQUIVOCADO, MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", yape);
		Long anulacion = jdbc.queryForObject("SELECT id FROM anulacion_pago WHERE pago_id = ?", Long.class, yape);

		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("Devolución sin reembolso registrado") && a.texto().contains("Yape"));
		como(CAJA);
		assertThatThrownBy(() -> verificacion.registrarReembolso(anulacion, new ReembolsoRequest("OP-1", true, null,
				null))).isInstanceOf(AccessDeniedException.class);
		// Una cuenta que fuera Caja y Administración a la vez tampoco: el modelo rechaza a la cajera del pago.
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(1L, 16L, "caja", "Lucía", false,
				EnumSet.of(Rol.ADMINISTRACION)));
		assertThatThrownBy(() -> verificacion.registrarReembolso(anulacion, new ReembolsoRequest("REM-2001", true, null,
				null))).isInstanceOf(ReglaNegocioException.class);
		como(ADMINISTRACION);
		assertThatThrownBy(() -> verificacion.registrarReembolso(anulacion, new ReembolsoRequest("REM-2001", false, null,
				null))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cuenta de origen");
		assertThat(verificacion.vista().devoluciones()).extracting(d -> d.anulacionId()).containsExactly(anulacion);
		verificacion.registrarReembolso(anulacion, new ReembolsoRequest("rem-2001", true, null, null));
		assertThat(jdbc.queryForMap("SELECT medio, numero_operacion, creado_por, cajero_pago FROM reembolso"))
				.containsEntry("medio", "YAPE").containsEntry("numero_operacion", "REM2001")
				.containsEntry("creado_por", "administracion").containsEntry("cajero_pago", "caja");
		assertThatThrownBy(() -> verificacion.registrarReembolso(anulacion, new ReembolsoRequest("REM-2002", true, null,
				null))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está registrado");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "REEMBOLSO_REGISTRADO").get("detalle")).asString()
				.contains("cuenta de origen").contains("REM2001");
		assertThat(verificacion.vista().devoluciones()).isEmpty();
		como(PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().startsWith("Devolución sin reembolso registrado"));
	}

	// ------------------------------------------------------------------ A2

	/**
	 * A2: aprobar una devolución en efectivo exige «Hablé con el apoderado» y un celular REGISTRADO de la familia; la
	 * bitácora guarda el número enmascarado. La alerta crítica sigue hasta que Administración registra la entrega.
	 */
	@Test
	void a2DevolucionEnEfectivoExigeHablarConElApoderado() {
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "500.00"));
		anulaciones.solicitarDevolucion(pago, CausaDevolucion.COBRO_EQUIVOCADO, MOTIVO_ANULACION);
		como(DIRECCION);
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);
		var tarjeta = bandeja.bandeja().pendientes().getFirst();
		assertThat(tarjeta.llamadas()).hasSize(1);
		assertThat(tarjeta.apruebaConVentana()).isTrue();
		assertThat(tarjeta.detalle()).anyMatch(l -> l.startsWith("Antes de aprobar, llama al apoderado: ")
				&& l.contains("+51 987 654 321"));

		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Hablé con el apoderado");
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null, false, List.of("987654321")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Hablé con el apoderado");
		// El número de un cómplice no sirve, ni el de la otra familia.
		for (var otro : List.of("999111222", "912345678", "abc")) {
			assertThatThrownBy(() -> bandeja.aprobar(solicitud, null, true, List.of(otro)))
					.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es el celular registrado");
		}
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("VIGENTE");

		bandeja.aprobar(solicitud, null, true, List.of("+51 987-654-321"));
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("ANULADO");
		String bitacora = EscenarioEscolar.ultimoEvento(jdbc, "SOLICITUD_APROBADA").get("detalle").toString();
		assertThat(bitacora).contains("Habló con el apoderado: ").contains("+51 *** *** 321");
		assertThat(EscenarioEscolar.todaLaBitacora(jdbc)).doesNotContain("987654321").doesNotContain("987 654 321");

		como(PROMOTORIA);
		List<AlertaRevision> lista = alertas.alertas();
		assertThat(lista).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("Devolución sin reembolso registrado") && a.texto().contains("Efectivo"));
		assertThat(lista).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().startsWith("Devoluciones en efectivo de hoy: 1 por S/ 450.00"));

		// La entrega la registra Administración, con nombre y documento de quien recibe (firma la nota de crédito).
		como(ADMINISTRACION);
		Long anulacion = jdbc.queryForObject("SELECT id FROM anulacion_pago", Long.class);
		assertThatThrownBy(() -> verificacion.registrarReembolso(anulacion, new ReembolsoRequest(null, false, "Rosa",
				null))).isInstanceOf(ReglaNegocioException.class);
		verificacion.registrarReembolso(anulacion, new ReembolsoRequest(null, false, "Rosa Huamán Torres",
				EscenarioEscolar.DNI_ROSA));
		assertThat(EscenarioEscolar.todaLaBitacora(jdbc)).doesNotContain(EscenarioEscolar.DNI_ROSA);
		Long nota = jdbc.queryForObject("SELECT nota_credito_id FROM anulacion_pago", Long.class);
		assertThat(estadoCuenta.comprobante(nota).firmaRecepcion()).isTrue();
	}

	/** A2: mover dinero a OTRA familia exige hablar con ambas: un número de cada una, en orden. */
	@Test
	void a2CorreccionAOtraFamiliaExigeHablarConAmbas() {
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		Long sebastian = cuota(jdbc, f.sebastian(), "PEN-2027-03");
		anulaciones.solicitarCorreccion(pago, EscenarioAprobaciones.correccion(f.flores(), List.of(sebastian)));
		como(PROMOTORIA);
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);
		var tarjeta = bandeja.bandeja().pendientes().getFirst();
		assertThat(tarjeta.llamadas()).hasSize(2);
		assertThat(tarjeta.detalle()).anyMatch(l -> l.startsWith("Antes de aprobar, llama también a la otra familia: ")
				&& l.contains("+51 912 345 678"));

		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null, true, List.of("987654321")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ambas familias");
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null, true, List.of("987654321", "987654321")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es el celular registrado");
		bandeja.aprobar(solicitud, null, true, List.of("987654321", "912345678"));
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "SOLICITUD_APROBADA").get("detalle")).asString()
				.contains("Habló con ambas familias").contains("+51 *** *** 321").contains("+51 *** *** 678");
		// Una corrección no entrega dinero: su nota de crédito no lleva espacio para la firma.
		como(ADMINISTRACION);
		Long nota = jdbc.queryForObject("SELECT nota_credito_id FROM anulacion_pago", Long.class);
		assertThat(estadoCuenta.comprobante(nota).firmaRecepcion()).isFalse();
	}

	/** A2: «pago duplicado» sin otro pago vigente de esas cuotas se rechaza (también si solo lo dice el motivo). */
	@Test
	void a2PagoDuplicadoSinOtroPagoEsRechazado() {
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(pago, CausaDevolucion.PAGO_DUPLICADO,
				"La familia pagó dos veces la misma pensión")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no es un pago duplicado");
		assertThatThrownBy(() -> anulaciones.solicitarDevolucion(pago, CausaDevolucion.OTRA,
				"Pago DUPLICADO por la mamá y el papá")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no es un pago duplicado");
		assertThat(contar(jdbc, "solicitud_cambio")).isZero();
	}

	// ------------------------------------------------------------------ A4

	/**
	 * A4: un Yape del viernes sin verificar es CRÍTICO desde las 19:00 del lunes (día hábil siguiente), no antes. Y
	 * la alerta de ATENCIÓN se cuenta en horas (hallazgo 7 de QA).
	 */
	@Test
	void a4YapeSinVerificarEsCriticoTrasElDiaHabilSiguiente() {
		cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "66554433", "450.00"));
		como(PROMOTORIA);
		reloj.fijar(ZonedDateTime.of(2026, 10, 5, 18, 59, 59, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).noneMatch(a -> a.gravedad() == Gravedad.CRITICA && a.texto().contains("sin verificarse"))
				.anyMatch(a -> a.gravedad() == Gravedad.ATENCION && a.texto().contains("sin verificarse"));
		reloj.fijar(ZonedDateTime.of(2026, 10, 5, 19, 0, 0, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("1 pago(s) digital(es) por S/ 450.00 siguen sin verificarse"));
	}

	/**
	 * Hallazgo 7 de QA: un Yape de las 23:59:59 no tiene «más de un día» a las 00:00:01; sí 24 horas después. (Es viernes:
	 * todavía no es crítico, eso llega el lunes a las 19:00).
	 */
	@Test
	void yapeCobradoA235959NoEsMasDeUnDiaALas000001() {
		reloj.fijar(ZonedDateTime.of(2026, 10, 2, 23, 59, 59, 0, LIMA).toInstant());
		cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "66554434", "450.00"));
		como(PROMOTORIA);
		reloj.fijar(ZonedDateTime.of(2026, 10, 3, 0, 0, 1, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("sin verificarse"));
		reloj.fijar(ZonedDateTime.of(2026, 10, 3, 23, 59, 58, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("sin verificarse"));
		reloj.fijar(ZonedDateTime.of(2026, 10, 3, 23, 59, 59, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().startsWith("1 pago(s) digital(es) por S/ 450.00 llevan más de 1 día(s)"));
	}

	/** A4: Promotoría ve al día hábil siguiente una muestra de verificaciones con quién verificó y qué escribió. */
	@Test
	void a4MuestreoDeVerificacionesDelDiaHabilAnterior() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "44332211", "450.00"));
		EscenarioCaja.verificadoEnBanco(verificacion, jdbc, yape);
		como(PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().startsWith("Revisa al azar"));
		reloj.fijar(ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.INFORMATIVA
				&& a.texto().startsWith("Revisa al azar: Yape de ") && a.texto().contains("verificado por")
				&& a.texto().contains("operación 44332211 del 02/10/2026 por S/ 450.00"));
	}

	// ------------------------------------------------------------------ B3

	/** B3: un pago anulado no se verifica (se simula un dato antiguo, anulado antes de exigir la verificación). */
	@Test
	void b3PagoAnuladoNoSeVerifica() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(marzoMateo), MedioPago.YAPE, "33221100", "450.00"));
		jdbc.update("UPDATE pago SET estado = 'ANULADO', operacion_vigente = NULL WHERE id = ?", yape);
		como(ADMINISTRACION);
		assertThatThrownBy(() -> verificacion.verificarPago(yape, VerificacionRequest.delBanco("33221100", HOY,
				new BigDecimal("450.00")))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("anulado");
		assertThat(contar(jdbc, "verificacion_bancaria")).isZero();
	}

	// ------------------------------------------------------------------ M3

	/**
	 * M3 (lapping): la caja del viernes sin depositar es ATENCIÓN el lunes y CRÍTICA el martes; un depósito con fecha de
	 * más de un día hábil después de la caja queda en rojo.
	 */
	@Test
	void m3EfectivoSinDepositarYDepositoTardio() {
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		cerrarConLoEsperado("450.00");
		como(PROMOTORIA);
		reloj.fijar(ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.ATENCION
				&& a.texto().contains("aún no se deposita."));
		reloj.fijar(ZonedDateTime.of(2026, 10, 6, 9, 0, 0, 0, LIMA).toInstant());
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("aún no se deposita, y ya pasó un día hábil."));

		como(CAJA);
		EstadoCierreVista estado = cierre.estado();
		cierre.registrarDeposito(new DepositoRequest(estado.porDepositar().getFirst().cajaId(),
				estado.cuentas().getFirst(), "DEP-9001", LocalDate.of(2026, 10, 6), new BigDecimal("450.00"), null));
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("Depósito tardío") && a.texto().contains("06/10/2026"));
		como(ADMINISTRACION);
		assertThat(verificacion.vista().depositos()).singleElement().satisfies(d -> assertThat(d.tardio()).isTrue());
	}

	/** Hallazgo 9 de QA: la fecha del depósito no puede ser anterior a la caja ni futura. */
	@Test
	void depositoConFechaAnteriorALaCajaOFuturaEsRechazado() {
		cobro.cobrar(efectivo(f.quispe(), List.of(marzoMateo), "450.00", "450.00"));
		cerrarConLoEsperado("450.00");
		EstadoCierreVista estado = cierre.estado();
		Long caja = estado.porDepositar().getFirst().cajaId();
		for (var fecha : List.of(HOY.minusDays(1), HOY.plusDays(1))) {
			assertThatThrownBy(() -> cierre.registrarDeposito(new DepositoRequest(caja, estado.cuentas().getFirst(),
					"DEP-9002", fecha, new BigDecimal("450.00"), null))).isInstanceOf(ReglaNegocioException.class)
					.hasMessageContaining("entre el día de la caja y hoy");
		}
		assertThat(contar(jdbc, "deposito_caja")).isZero();
	}

	private void cerrarConLoEsperado(String contado) {
		como(CAJA);
		cierre.contar(new ConteoRequest(new BigDecimal(contado), null));
	}
}
