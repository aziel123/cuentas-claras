package pe.edu.virgenmaria.cuentasclaras.conciliacion.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CuentaFamilia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ResultadoBusqueda;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.RevisionCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.config.RelojMovible;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.ConfirmacionExtractoVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CuentaRequest;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaPreviaExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.BancoCuenta;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CuentaBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LoteRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Conciliación de demostración (solo perfil {@code dev}, con H2 en memoria y los datos del colegio), hecha con los mismos
 * servicios y personas que en el colegio:
 * <ul>
 *   <li>«promotor» registra la cuenta del colegio {@value #CUENTA};</li>
 *   <li>AYER, después de cerrar su caja, «caja» registra un Yape INVENTADO (número {@value #OPERACION_YAPE_INVENTADO})
 *       por la cuota de Ariana Gutiérrez: no está en el banco y la conciliación lo muestra en rojo en cuanto se confirma
 *       el extracto de ese día;</li>
 *   <li>«administracion» sube el extracto de ANTEAYER (solo una comisión y el ITF) y «director» lo confirma a ciegas: es
 *       el primero de la cadena de saldos;</li>
 *   <li>queda listo el extracto de EJEMPLO de AYER para probar el flujo: se descarga en Conciliación › Extractos, lo sube
 *       «administracion» y lo confirma «promotor» escribiendo el saldo final que muestra el log al arrancar (en desarrollo
 *       no hay app del banco). Trae el Yape real y el depósito de la caja de ayer (se emparejan solos), el abono de la
 *       recaudación del banco (se empareja cuando se confirme el lote), unos intereses (sin pareja: se explican) y una
 *       comisión; NO trae el Yape inventado por la cajera, que aparece en rojo.</li>
 * </ul>
 */
@Component
@Profile("dev")
@Order(7)
public class DatosDemoConciliacionDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoConciliacionDev.class);

	static final String CUENTA = "191-2345678-0-12";

	static final BigDecimal SALDO_INICIAL = new BigDecimal("18450.00");

	/** El Yape que la cajera «inventa» en la demostración: no está en el extracto del banco. */
	public static final String OPERACION_YAPE_INVENTADO = "YP700112233";

	/** Ariana Gutiérrez: su pago por banco de la demostración es de más (queda por revisar), así que su cuota sigue por pagar. */
	static final String DNI_ARIANA = "77640391";

	private final ServicioCuentasBancarias cuentas;

	private final ServicioExtractos extractos;

	private final CuentaBancariaRepository repositorioCuentas;

	private final PagoRepository pagos;

	private final DepositoCajaRepository depositos;

	private final LoteRecaudacionRepository lotes;

	private final UsuarioRepository usuarios;

	private final EjemploExtractoDev ejemplo;

	private final ServicioCobro cobro;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final RelojMovible relojMovible;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public DatosDemoConciliacionDev(ServicioCuentasBancarias cuentas, ServicioExtractos extractos,
			CuentaBancariaRepository repositorioCuentas, PagoRepository pagos, DepositoCajaRepository depositos,
			LoteRecaudacionRepository lotes, UsuarioRepository usuarios, EjemploExtractoDev ejemplo, ServicioCobro cobro,
			PlatformTransactionManager transacciones, Clock reloj, RelojMovible relojMovible,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.cuentas = cuentas;
		this.extractos = extractos;
		this.repositorioCuentas = repositorioCuentas;
		this.pagos = pagos;
		this.depositos = depositos;
		this.lotes = lotes;
		this.usuarios = usuarios;
		this.ejemplo = ejemplo;
		this.cobro = cobro;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
		this.relojMovible = relojMovible;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si creó la cuenta, el primer extracto y el de ejemplo */
	boolean crearSiCorresponde() {
		if (!habilitado || urlBaseDatos == null || !urlBaseDatos.startsWith("jdbc:h2:mem:")) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL, this::cargar));
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo cargar la conciliación de demostración: {}", e.getMessage());
			return false;
		}
	}

	private Boolean cargar() {
		if (!Objects.requireNonNull(transaccion.execute(e -> repositorioCuentas.findByActivaTrueOrderByIdAsc().isEmpty()))) {
			LOG.info("No se carga la conciliación de demostración: ya hay cuentas bancarias.");
			return false;
		}
		UsuarioAutenticado promotor = persona("promotor");
		UsuarioAutenticado administracion = persona("administracion");
		UsuarioAutenticado director = persona("director");
		if (promotor == null || administracion == null || director == null) {
			LOG.info("No se carga la conciliación de demostración: faltan los usuarios de demostración.");
			return false;
		}
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate anteayer = hoy.minusDays(2);
		LocalDate ayer = hoy.minusDays(1);
		yapeInventadoDeAyer();
		Long cuentaId = como(promotor, () -> cuentas.registrar(new CuentaRequest(BancoCuenta.BCP, CUENTA,
				"BCP soles · cobranza")));
		// Extracto de anteayer: el primero de la cadena (solo cargos del banco), confirmado a ciegas por Dirección.
		BigDecimal saldo = SALDO_INICIAL;
		StringBuilder primero = cabecera();
		saldo = fila(primero, anteayer, "COMISION MANTENIMIENTO CUENTA", "", "", new BigDecimal("12.00"), null, saldo);
		saldo = fila(primero, anteayer, "IMPUESTO ITF", "", "", new BigDecimal("0.45"), null, saldo);
		BigDecimal saldoAnteayer = saldo;
		como(administracion, () -> {
			byte[] contenido = primero.toString().getBytes(StandardCharsets.UTF_8);
			VistaPreviaExtracto previa = extractos.previsualizar("extracto-" + anteayer + ".csv", contenido,
					contenido.length);
			return extractos.registrar(previa, previa.token());
		});
		como(director, () -> {
			ConfirmacionExtractoVista c = extractos.paraConfirmar(cuentaId);
			extractos.confirmar(cuentaId, c.extractoId(), c.version(), saldoAnteayer);
			return null;
		});
		// Extracto de EJEMPLO de ayer, con lo que de verdad entró al banco (sin el Yape inventado).
		StringBuilder csv = cabecera();
		BigDecimal saldoAyer = Objects.requireNonNull(transaccion.execute(e -> {
			BigDecimal s = saldoAnteayer;
			for (Pago p : pagos.digitalesDeVentanillaEntre(ayer, ayer)) {
				if (!OPERACION_YAPE_INVENTADO.equals(p.getNumeroOperacion())) {
					s = fila(csv, ayer, "ABONO " + p.getMedio().etiqueta().toUpperCase(java.util.Locale.ROOT) + " "
							+ p.getFamilia().getNombre().toUpperCase(java.util.Locale.ROOT), p.getNumeroOperacion(), "", null,
							p.getTotal(), s);
				}
			}
			for (DepositoCaja d : depositos.findByFechaDepositoBetweenOrderByFechaDepositoAscIdAsc(ayer, ayer)) {
				s = fila(csv, ayer, "DEPOSITO EN EFECTIVO VENTANILLA", d.getNumeroOperacion(), "", null, d.getMonto(), s);
			}
			for (LoteRecaudacion t : lotes.findTop50ByOrderByIdDesc()) {
				if (t.getFechaProceso().equals(ayer) && t.getShaVigente() != null) {
					s = fila(csv, ayer, "ABONO RECAUD CODIGO ALUMNO", "", "RECAUD-" + ayer.toString().replace("-", ""), null,
							t.getTotal(), s);
				}
			}
			s = fila(csv, ayer, "INTERESES CTA CTE", "", "", null, new BigDecimal("1.23"), s);
			return fila(csv, ayer, "COMISION TRANSFERENCIAS", "", "", new BigDecimal("15.00"), null, s);
		}));
		ejemplo.guardar("extracto-ejemplo-" + ayer + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8), saldoAyer);
		LOG.info("Conciliación de demostración lista: cuenta {} con el extracto del {} confirmado. Para probar el flujo, "
				+ "entra como «administracion», descarga el extracto de ejemplo de ayer en Conciliación › Extractos y súbelo; "
				+ "luego entra como «promotor» y escribe a ciegas el saldo final: {} (dato de desarrollo; en el colegio se lee "
				+ "en la app del banco). El Yape inventado ({}) aparecerá en rojo.", CUENTA, Calendario.formatear(anteayer),
				saldoAyer.toPlainString(), OPERACION_YAPE_INVENTADO);
		return true;
	}

	/** Ayer, con su caja ya cerrada, «caja» registra un Yape que nadie pagó (el reloj de desarrollo va un día atrás y vuelve). */
	private void yapeInventadoDeAyer() {
		relojMovible.mover(Duration.ofDays(-1));
		try {
			SecurityContext anterior = SecurityContextHolder.getContext();
			SecurityContext contexto = SecurityContextHolder.createEmptyContext();
			contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("caja", null,
					List.of(new SimpleGrantedAuthority("ROLE_CAJA"))));
			SecurityContextHolder.setContext(contexto);
			try {
				ResultadoBusqueda ariana = cobro.buscar(DNI_ARIANA).resultados().getFirst();
				CuentaFamilia cuenta = cobro.cuentaDeFamilia(ariana.familiaId());
				Long cuota = cuenta.alumnos().stream().filter(a -> a.nombre().startsWith("Ariana"))
						.flatMap(a -> a.cuotas().stream()).filter(CuentaFamilia.CuotaPorCobrar::cobrable).findFirst()
						.orElseThrow().id();
				RevisionCobro revision = cobro.revisar(ariana.familiaId(), new SeleccionCobroRequest(List.of(cuota),
						MedioPago.YAPE), null);
				cobro.cobrar(new CobroRequest(revision.clave(), ariana.familiaId(), revision.cuotaIds(), MedioPago.YAPE,
						OPERACION_YAPE_INVENTADO, null, null, revision.total(), TipoComprobante.BOLETA,
						revision.receptorPorDefecto(), null, null));
			}
			finally {
				SecurityContextHolder.setContext(anterior);
			}
		}
		finally {
			relojMovible.mover(Duration.ofDays(1));
		}
	}

	private static StringBuilder cabecera() {
		return new StringBuilder("cuenta;").append(CUENTA).append('\n')
				.append("fecha;descripcion;numero_operacion;referencia;cargo;abono;saldo\n");
	}

	/** Agrega una fila (cargo o abono) y devuelve el saldo después de ella. */
	private static BigDecimal fila(StringBuilder csv, LocalDate fecha, String descripcion, String operacion,
			String referencia, BigDecimal cargo, BigDecimal abono, BigDecimal saldo) {
		BigDecimal nuevo = Dinero.normalizar(cargo != null ? saldo.subtract(cargo) : saldo.add(abono));
		csv.append(fecha).append(';').append(descripcion).append(';').append(operacion == null ? "" : operacion).append(';')
				.append(referencia).append(';').append(cargo == null ? "" : cargo.toPlainString()).append(';')
				.append(abono == null ? "" : abono.toPlainString()).append(';').append(nuevo.toPlainString()).append('\n');
		return nuevo;
	}

	private UsuarioAutenticado persona(String usuario) {
		return transaccion.execute(e -> usuarios.findByNombreUsuario(usuario)
				.map(u -> UsuarioAutenticado.de(u, LocalDateTime.now(reloj))).orElse(null));
	}

	private static <T> T como(UsuarioAutenticado usuario, Supplier<T> operacion) {
		SecurityContext anterior = SecurityContextHolder.getContext();
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null,
				usuario.getAuthorities()));
		SecurityContextHolder.setContext(contexto);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.setContext(anterior);
		}
	}

}
