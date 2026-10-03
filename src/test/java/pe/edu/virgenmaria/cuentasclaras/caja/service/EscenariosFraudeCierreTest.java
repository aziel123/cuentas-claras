package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCierreVista;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ContenidoVisible;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.MOTIVO_ANULACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.digital;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.estado;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Los fraudes del cierre de caja, el depósito y la verificación bancaria (sprint 3, tanda 3). Cada prueba reproduce el
 * ataque; la defensa en MySQL (triggers y permisos por columna) se prueba además en {@code PermisosMySqlTest}.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EscenariosFraudeCierreTest {

	private static final String EXPLICACION = "Volví a contar dos veces y faltan cincuenta soles";

	private static final VerificacionRequest YAPE_EN_BANCO = VerificacionRequest.delBanco("YP000002",
			LocalDate.of(2026, 10, 2), new BigDecimal("450.00"));

	private static final VerificacionRequest DEPOSITO_EN_BANCO = VerificacionRequest.delBanco("OP6666",
			LocalDate.of(2026, 10, 2), new BigDecimal("450.00"));

	@Autowired
	private ServicioCierreCaja cierre;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioVerificacionBancaria verificacion;

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
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long pago;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(CAJA);
		pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00", "500.00"));
	}

	@AfterEach
	void limpiar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** El robo original: se queda S/ 50. El cierre lo deja registrado y Promotoría lo ve EL MISMO DÍA, en rojo. */
	@Test
	void fraudeCerrarConFaltanteGeneraAlertaCriticaInmediata() {
		cerrarCon("400.00", "400.00");

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_caja");
		assertThat((BigDecimal) fila.get("diferencia")).isEqualByComparingTo("-50.00");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CAJA_CERRADA_CON_DIFERENCIA").get("valor_nuevo")).asString()
				.contains("faltante de S/ 50.00");
		como(PROMOTORIA);
		List<AlertaRevision> lista = alertas.alertas();
		assertThat(lista).first().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(Gravedad.CRITICA);
			assertThat(a.texto()).startsWith("Faltante de S/ 50.00 en la caja de").contains(EXPLICACION);
		});
		// En la bandeja va primero, resaltado, y aprobarlo exige un comentario.
		var tarjeta = bandeja.bandeja().pendientes().getFirst();
		assertThat(tarjeta.tipo()).isEqualTo("CIERRE_CAJA");
		assertThat(tarjeta.advertencia()).startsWith("Faltante de S/ 50.00");
		assertThat(tarjeta.pideComentario()).isTrue();
		Long solicitud = tarjeta.id();
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("escribe qué verificaste");
		bandeja.aprobar(solicitud, "La cajera repuso los S/ 50; quedó en acta con Dirección");
		assertThat(jdbc.queryForObject("SELECT comentario_revision FROM cierre_caja", String.class))
				.isEqualTo("La cajera repuso los S/ 50; quedó en acta con Dirección");
		// Aprobado, sale de las alertas; el cierre con su faltante queda para siempre.
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().startsWith("Faltante"));
		assertThat((BigDecimal) jdbc.queryForObject("SELECT diferencia FROM cierre_caja", BigDecimal.class))
				.isEqualByComparingTo("-50.00");
	}

	/** Un sobrante también alerta: puede ser un cobro que no se registró. */
	@Test
	void fraudeSobranteTambienAlertaComoPosibleCobroSinRegistrar() {
		cerrarCon("500.00", "500.00");

		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("Sobrante de S/ 50.00"));
		assertThat(bandeja.bandeja().pendientes().getFirst().advertencia()).contains("cobro sin registrar");
	}

	/** Contar 449, 450, 451... para dar con el esperado: solo hay un reconteo y el primer conteo queda guardado. */
	@Test
	void fraudeNoSePuedeTantearElEsperadoContandoVariasVeces() {
		cierre.contar(new ConteoRequest(new BigDecimal("440.00"), null));
		assertThatThrownBy(() -> cierre.contar(new ConteoRequest(new BigDecimal("445.00"), null)))
				.isInstanceOf(ReglaNegocioException.class);
		cierre.recontar(new ReconteoRequest(new BigDecimal("450.00"), null, EXPLICACION));
		assertThatThrownBy(() -> cierre.recontar(new ReconteoRequest(new BigDecimal("450.00"), null, EXPLICACION)))
				.isInstanceOf(ReglaNegocioException.class);
		// El primer conteo (440) queda a la vista de quien aprueba, aunque el segundo cuadre.
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cierre_caja");
		assertThat((BigDecimal) fila.get("primer_conteo")).isEqualByComparingTo("440.00");
		como(DIRECCION);
		assertThat(bandeja.bandeja().pendientes().getFirst().detalle())
				.anyMatch(l -> l.contains("primer conteo S/ 440.00: no coincidió"));
		// Ni por SQL se «acomoda» el conteo: la diferencia es siempre contado − esperado (CHECK; en MySQL además el
		// conteo no tiene GRANT de UPDATE y el trigger de la caja no deja reescribir el primer conteo).
		assertThatThrownBy(() -> jdbc.update("UPDATE cierre_caja SET contado = 460.00")).isInstanceOf(
				DataIntegrityViolationException.class);
	}

	/** La pantalla de cierre no muestra el esperado antes de contar (ni en el modelo de la vista). */
	@Test
	void fraudeVerElEsperadoAntesDeContarNoEsPosible() {
		EstadoCierreVista estado = cierre.estado();
		assertThat(EstadoCierreVista.CajaPorCerrar.class.getRecordComponents()).extracting(c -> c.getName())
				.doesNotContain("esperado", "efectivo");
		assertThat(estado.ultimoCierre()).isNull();
		assertThat(ContenidoVisible.muestraMonto(estado, "450.00")).isFalse();
		// Pagos de hoy tampoco tiene totales.
		assertThat(java.util.Arrays.stream(pe.edu.virgenmaria.cuentasclaras.caja.dto.PagosDelDia.class.getRecordComponents())
				.map(java.lang.reflect.RecordComponent::getName)).doesNotContain("esperado", "total");
		// B1 (correcciones): con la caja abierta, ni el monto de cada pago en efectivo (para no sumarlos a ojo).
		var hoy = cobro.pagosDelDia();
		assertThat(hoy.pagos()).singleElement().satisfies(p -> {
			assertThat(p.total()).isNull();
			assertThat(p.comprobante()).isEqualTo("B001-00000001");
		});
		assertThat(ContenidoVisible.muestraMonto(hoy, "450.00")).isFalse();
		// Un Yape sí muestra su monto (se verifica contra el banco); cerrada la caja, el efectivo también.
		cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE, "YP000777",
				"450.00"));
		assertThat(cobro.pagosDelDia().pagos()).filteredOn(p -> p.total() != null).singleElement()
				.satisfies(p -> assertThat(p.medio()).isEqualTo("Yape"));
		cerrarCon("450.00", null);
		assertThat(cobro.pagosDelDia().pagos()).allSatisfy(p -> assertThat(p.total()).isEqualByComparingTo("450.00"));
	}

	@Test
	void fraudeCobrarEnEfectivoDespuesDelCierreEsRechazadoYAuditado() {
		cerrarCon("450.00", null);

		Long marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzoValeria), "450.00", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya se cerró");
		assertThat(estado(jdbc, marzoValeria)).isEqualTo("PENDIENTE");
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CAJA_EFECTIVO_RECHAZADO_CERRADA"))
				.containsEntry("nombre_usuario", "caja");
		assertThat(cobro.cuentaDeFamilia(f.quispe()).aceptaEfectivo()).isFalse();
	}

	@Test
	void cajaCerradaAceptaPagosDigitales() {
		cerrarCon("450.00", null);

		Long yape = cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE,
				"YP998877", "450.00"));
		assertThat(jdbc.queryForObject("SELECT caja_diaria_id FROM pago WHERE id = ?", Long.class, yape))
				.isEqualTo(jdbc.queryForObject("SELECT caja_diaria_id FROM pago WHERE id = ?", Long.class, pago));
		// El cierre no cambia: lo digital nunca entra al esperado.
		assertThat((BigDecimal) jdbc.queryForObject("SELECT esperado FROM cierre_caja", BigDecimal.class))
				.isEqualByComparingTo("450.00");
	}

	/** La cajera no aprueba su cierre: ni por la bandeja (no tiene acceso), ni con una cuenta preparada, ni por SQL. */
	@Test
	void fraudeCajeraNoApruebaSuPropioCierre() {
		cerrarCon("400.00", "400.00");
		Long cierreId = jdbc.queryForObject("SELECT id FROM cierre_caja", Long.class);
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "cierre_caja", cierreId);

		como(CAJA);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, "Todo conforme en mi caja")).isInstanceOf(
				AccessDeniedException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE cierre_caja SET estado = 'APROBADO', revisado_por = creado_por, "
				+ "revisado_en = creado_en")).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT estado FROM cierre_caja", String.class)).isEqualTo("POR_REVISAR");
	}

	/** Quien aprueba creó hace una semana la cuenta con la que se cobró y se cerró: no aprueba ese cierre. */
	@Test
	void fraudeQuienPreparoLaCuentaDeLaCajeraNoApruebaSuCierre() {
		Usuario cuenta = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.nueva", UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);
		jdbc.update("UPDATE usuario SET creado_por = 'director', creado_en = ? WHERE id = ?",
				Timestamp.valueOf("2026-09-25 10:00:00"), cuenta.getId());
		UsuariosDePrueba.iniciarSesion(usuarios.findById(cuenta.getId()).orElseThrow());
		cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), "450.00", "450.00"));
		cierre.contar(new ConteoRequest(new BigDecimal("400.00"), null));
		cierre.recontar(new ReconteoRequest(new BigDecimal("400.00"), null, EXPLICACION));
		Long cierreId = jdbc.queryForObject("SELECT c.id FROM cierre_caja c JOIN caja_diaria d ON d.id = c.caja_diaria_id "
				+ "WHERE d.cajero = 'caja.nueva'", Long.class);

		como(DIRECCION);
		assertThatThrownBy(() -> bandeja.aprobar(EscenarioAprobaciones.pendiente(jdbc, "cierre_caja", cierreId),
				"Aprobado por Dirección sin revisar")).isInstanceOf(AutoaprobacionSolicitudException.class)
				.hasMessageContaining("cuenta que creaste");
		como(PROMOTORIA);
		bandeja.aprobar(EscenarioAprobaciones.pendiente(jdbc, "cierre_caja", cierreId),
				"Conversé con la cajera nueva: repuso el faltante");
		assertThat(jdbc.queryForObject("SELECT revisado_por FROM cierre_caja WHERE id = ?", String.class, cierreId))
				.isEqualTo("promotor");
	}

	/**
	 * Cerró con faltante y pide reabrir para «arreglarlo» contando de nuevo (ahora que vio el esperado). Aunque se
	 * apruebe la reapertura, el primer cierre con su faltante queda registrado, sigue por aprobar y sigue en las alertas.
	 */
	@Test
	void fraudeReabrirParaArreglarUnFaltanteNoBorraElFaltante() {
		cerrarCon("400.00", "400.00");
		cierre.solicitarReapertura("Me equivoqué al contar: quiero volver a contar");
		Long caja = jdbc.queryForObject("SELECT id FROM caja_diaria", Long.class);

		como(DIRECCION);
		var tarjeta = bandeja.bandeja().pendientes().stream().filter(s -> s.tipo().equals("REAPERTURA_CAJA")).findFirst()
				.orElseThrow();
		assertThat(tarjeta.advertencia()).contains("faltante de S/ 50.00").contains("reabrir no la corrige");
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "caja_diaria", caja);

		como(CAJA);
		cierre.contar(new ConteoRequest(new BigDecimal("450.00"), null));
		assertThat(jdbc.queryForList("SELECT CONCAT(numero, ' ', estado, ' ', diferencia) FROM cierre_caja ORDER BY numero",
				String.class)).containsExactly("1 POR_REVISAR -50.00", "2 POR_REVISAR 0.00");
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("Faltante de S/ 50.00"));
	}

	/** Depositar menos de lo contado exige explicación y alerta; el depósito queda contra lo contado, no lo depositado. */
	@Test
	void fraudeDepositarMenosDeLoCerrado() {
		cerrarCon("450.00", null);
		EstadoCierreVista estado = cierre.estado();
		Long caja = estado.porDepositar().getFirst().cajaId();

		assertThatThrownBy(() -> cierre.registrarDeposito(new DepositoRequest(caja, estado.cuentas().getFirst(), "OP-5555",
				estado.hoy(), new BigDecimal("400.00"), null))).isInstanceOf(ReglaNegocioException.class);
		assertThat(contar(jdbc, "deposito_caja")).isZero();
		cierre.registrarDeposito(new DepositoRequest(caja, estado.cuentas().getFirst(), "OP-5555", estado.hoy(),
				new BigDecimal("400.00"), "Se me perdió un billete de cincuenta en el camino al banco"));

		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("depositó S/ 400.00 de S/ 450.00"));
		// Administración lo verifica a ciegas: aparece en el banco por S/ 400; la diferencia ya es una alerta crítica.
		como(ADMINISTRACION);
		Long deposito = verificacion.vista().depositos().getFirst().id();
		verificacion.verificarDeposito(deposito, VerificacionRequest.delBanco("op 5555", estado.hoy(),
				new BigDecimal("400.00")));
		assertThat(jdbc.queryForObject("SELECT banco_monto FROM verificacion_bancaria", BigDecimal.class))
				.isEqualByComparingTo("400.00");
	}

	/** «Cobré en efectivo y lo registré como Yape con un número inventado»: el cierre no lo ve; el banco sí. */
	@Test
	void fraudeYapeInventadoSeDetectaEnLaVerificacionBancaria() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE,
				"YP000001", "450.00"));
		cerrarCon("450.00", null);
		assertThat((BigDecimal) jdbc.queryForObject("SELECT diferencia FROM cierre_caja", BigDecimal.class))
				.isEqualByComparingTo("0.00");

		como(ADMINISTRACION);
		assertThat(verificacion.vista().pagos()).extracting(p -> p.id()).containsExactly(yape);
		assertThatThrownBy(() -> verificacion.verificarPago(yape, VerificacionRequest.noAparece(null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("anota qué revisaste");
		verificacion.verificarPago(yape, VerificacionRequest.noAparece(
				"No figura en el Yape empresarial del 02/10 ni del 03/10"));

		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "PAGO_NO_ENCONTRADO_BANCO")).containsEntry("nombre_usuario",
				"administracion");
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().contains("YP000001") && a.texto().contains("NO aparece en el banco"));
		como(ADMINISTRACION);
		assertThat(verificacion.vista().pagos()).isEmpty();
		assertThatThrownBy(() -> verificacion.verificarPago(yape, VerificacionRequest.delBanco("YP000001",
				LocalDate.of(2026, 10, 2), new BigDecimal("450.00")))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se verificó");
	}

	/** Quien cobró o depositó no verifica: la cajera no tiene el rol y el modelo lo impide aunque lo tuviera. */
	@Test
	void fraudeCajeraNoVerificaSuPropioYapeNiDeposito() {
		Long yape = cobro.cobrar(digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")), MedioPago.YAPE,
				"YP000002", "450.00"));
		cerrarCon("450.00", null);
		EstadoCierreVista estado = cierre.estado();
		cierre.registrarDeposito(new DepositoRequest(estado.porDepositar().getFirst().cajaId(),
				estado.cuentas().getFirst(), "OP-6666", estado.hoy(), new BigDecimal("450.00"), null));
		Long deposito = jdbc.queryForObject("SELECT id FROM deposito_caja", Long.class);

		assertThatThrownBy(() -> verificacion.verificarPago(yape, YAPE_EN_BANCO))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> verificacion.verificarDeposito(deposito, DEPOSITO_EN_BANCO))
				.isInstanceOf(AccessDeniedException.class);
		// Una cuenta que fuera Caja y Administración a la vez (los roles son incompatibles) tampoco: el modelo lo rechaza.
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(1L, 16L, "caja", "Lucía", false,
				java.util.EnumSet.of(Rol.ADMINISTRACION)));
		assertThatThrownBy(() -> verificacion.verificarPago(yape, YAPE_EN_BANCO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Quien cobró");
		assertThatThrownBy(() -> verificacion.verificarDeposito(deposito, DEPOSITO_EN_BANCO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Quien depositó");
		assertThat(contar(jdbc, "verificacion_bancaria")).isZero();
		// Y el efectivo no se «verifica» como si fuera digital.
		como(ADMINISTRACION);
		assertThatThrownBy(() -> verificacion.verificarPago(pago, YAPE_EN_BANCO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pagos digitales");
	}

	/**
	 * Pendiente de la tanda 2: anular (devolver) un pago en efectivo de una caja YA CERRADA no cambia ese cierre; la
	 * anulación queda «posterior al cierre» y Promotoría ve la devolución pendiente (el reembolso es por banco).
	 */
	@Test
	void fraudeAnularEfectivoDeDiaCerradoNoCambiaElCierreYQuedaDevolucionPendiente() {
		cerrarCon("450.00", null);
		Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM cierre_caja");
		anulaciones.solicitarDevolucion(pago, MOTIVO_ANULACION);
		reloj.avanzar(Duration.ofDays(1));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);

		assertThat(jdbc.queryForObject("SELECT posterior_al_cierre FROM anulacion_pago WHERE pago_id = ?", Boolean.class,
				pago)).isTrue();
		assertThat(jdbc.queryForMap("SELECT * FROM cierre_caja")).isEqualTo(antes);
		assertThat(jdbc.queryForObject("SELECT estado FROM caja_diaria", String.class)).isEqualTo("CERRADA");
		como(PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == Gravedad.CRITICA
				&& a.texto().startsWith("Devolución sin reembolso registrado") && a.texto().contains("S/ 450.00"));
		// El depósito esperado sigue siendo lo contado: el reembolso no sale del efectivo depositado.
		como(CAJA);
		assertThat(cierre.estado().porDepositar().getFirst().esperado()).isEqualByComparingTo("450.00");
	}

	/** Una corrección en una caja cerrada: el reemplazo entra en esa caja (única excepción) y su efectivo no cambia. */
	@Test
	void correccionEnCajaCerradaEntraEnEsaCajaSinCambiarElEfectivo() {
		cerrarCon("450.00", null);
		anulaciones.solicitarCorreccion(pago, EscenarioAprobaciones.correccion(f.quispe(), List.of(cuota(jdbc, f.valeria(),
				"PEN-2027-03"))));
		EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "pago", pago);

		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT caja_diaria_id) FROM pago", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT SUM(total) FROM pago WHERE estado = 'VIGENTE' AND medio = 'EFECTIVO'",
				BigDecimal.class)).isEqualByComparingTo("450.00");
		assertThat(jdbc.queryForObject("SELECT posterior_al_cierre FROM anulacion_pago", Boolean.class)).isTrue();
	}

	private void cerrarCon(String primero, String reconteo) {
		como(CAJA);
		cierre.contar(new ConteoRequest(new BigDecimal(primero), null));
		if (reconteo != null) {
			cierre.recontar(new ReconteoRequest(new BigDecimal(reconteo), null, EXPLICACION));
		}
	}
}
