package pe.edu.virgenmaria.cuentasclaras.recaudacion.inicial;

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
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Recaudación de demostración (solo perfil {@code dev}, con H2 en memoria y los datos del colegio): «administracion»
 * sube un archivo del banco con pagos de AYER de cuatro alumnos (Diego con referencia, Fernanda sin referencia, Luciana
 * a cuenta y Ariana con un pago de más) y un código con el dígito verificador errado. El lote queda POR CONFIRMAR: para
 * verlo aplicado, entra como «promotor» o «director» y escribe a ciegas el total que te muestra el log al arrancar (en
 * desarrollo no hay portal del banco).
 * Para probar la carga de punta a punta está el archivo {@code docs/ux/ejemplo-recaudacion.csv} (total del banco:
 * S/ 1755.00): sus códigos son los de Mateo y Valeria con los datos de demostración recién creados, y trae un pago
 * exacto, uno a cuenta, uno de más, un código errado y una operación repetida.
 */
@Component
@Profile("dev")
@Order(6)
public class DatosDemoRecaudacionDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoRecaudacionDev.class);

	static final String USUARIO = "administracion";

	static final String DNI_DIEGO = "76902114";

	static final String DNI_FERNANDA = "79876543";

	static final String DNI_LUCIANA = "79215560";

	static final String DNI_ARIANA = "77640391";

	private final ServicioRecaudacion servicio;

	private final AlumnoRepository alumnos;

	private final CuotaRepository cuotas;

	private final UsuarioRepository usuarios;

	private final LoteRecaudacionRepository lotes;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public DatosDemoRecaudacionDev(ServicioRecaudacion servicio, AlumnoRepository alumnos, CuotaRepository cuotas,
			UsuarioRepository usuarios, LoteRecaudacionRepository lotes, PlatformTransactionManager transacciones,
			Clock reloj, @Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.servicio = servicio;
		this.alumnos = alumnos;
		this.cuotas = cuotas;
		this.usuarios = usuarios;
		this.lotes = lotes;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si cargó el archivo de demostración */
	boolean crearSiCorresponde() {
		if (!habilitado || urlBaseDatos == null || !urlBaseDatos.startsWith("jdbc:h2:mem:")) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL, this::cargar));
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo cargar la recaudación de demostración: {}", e.getMessage());
			return false;
		}
	}

	private Boolean cargar() {
		if (!transaccion.execute(e -> lotes.findTop50ByOrderByIdDesc().isEmpty())) {
			LOG.info("No se carga la recaudación de demostración: ya hay archivos del banco.");
			return false;
		}
		Optional<UsuarioAutenticado> administracion = transaccion.execute(e -> usuarios.findByNombreUsuario(USUARIO)
				.map(u -> UsuarioAutenticado.de(u, LocalDateTime.now(reloj))));
		if (administracion == null || administracion.isEmpty()) {
			LOG.info("No se carga la recaudación de demostración: falta el usuario «{}».", USUARIO);
			return false;
		}
		Archivo archivo = transaccion.execute(e -> archivo(LocalDate.now(reloj).minusDays(1)));
		if (archivo == null) {
			LOG.info("No se carga la recaudación de demostración: no están los alumnos o sus cuotas.");
			return false;
		}
		Long lote = como(administracion.get(), () -> {
			VistaPreviaRecaudacion previa = servicio.previsualizar(archivo.nombre(), archivo.contenido(),
					archivo.contenido().length);
			return servicio.registrar(previa, previa.token());
		});
		LOG.info("Recaudación de demostración cargada: lote {} por confirmar (lo subió «{}»). Para aplicarlo, entra como "
				+ "«promotor» o «director» y escribe a ciegas el total del banco: {} (dato de desarrollo; en el colegio se "
				+ "lee en el portal del banco).", lote, USUARIO, archivo.total().toPlainString());
		return true;
	}

	/** El archivo del banco de demostración (formato genérico), o {@code null} si faltan los datos. */
	private Archivo archivo(LocalDate fecha) {
		Optional<Alumno> diego = alumno(DNI_DIEGO);
		Optional<Alumno> fernanda = alumno(DNI_FERNANDA);
		Optional<Alumno> luciana = alumno(DNI_LUCIANA);
		Optional<Alumno> ariana = alumno(DNI_ARIANA);
		if (diego.isEmpty() || fernanda.isEmpty() || luciana.isEmpty() || ariana.isEmpty()) {
			return null;
		}
		List<Cuota> deDiego = porPagar(diego.get());
		List<Cuota> deFernanda = porPagar(fernanda.get());
		List<Cuota> deLuciana = porPagar(luciana.get());
		List<Cuota> deAriana = porPagar(ariana.get());
		if (deDiego.isEmpty() || deFernanda.isEmpty() || deLuciana.isEmpty() || deAriana.isEmpty()) {
			return null;
		}
		String dia = fecha.toString();
		String prefijo = "DEMO" + fecha.toString().replace("-", "");
		List<String> lineas = new ArrayList<>();
		BigDecimal total = Dinero.CERO;
		// 1. Diego paga su cuota más antigua con la referencia de la base de deudas: se aplica a esa cuota.
		total = total.add(linea(lineas, dia, diego.get(), deDiego.getFirst(), deDiego.getFirst().saldo(), prefijo + "01"));
		// 2. Fernanda paga su cuota más antigua sin referencia: se imputa de la más antigua a la más nueva.
		total = total.add(linea(lineas, dia, fernanda.get(), null, deFernanda.getFirst().saldo(), prefijo + "02"));
		// 3. Luciana paga la mitad de su cuota: pago a cuenta (parcial), resaltado.
		total = total.add(linea(lineas, dia, luciana.get(), deLuciana.getFirst(),
				deLuciana.getFirst().saldo().divide(BigDecimal.valueOf(2), 0, RoundingMode.DOWN).setScale(2), prefijo + "03"));
		// 4. Ariana paga 100 soles de más (con referencia): queda por revisar (EXCESO).
		total = total.add(linea(lineas, dia, ariana.get(), deAriana.getFirst(),
				deAriana.getFirst().saldo().add(new BigDecimal("100.00")), prefijo + "04"));
		// 5. Un código con el dígito verificador errado: queda por revisar (CODIGO_INVALIDO).
		String errado = codigoErrado(CodigoPago.deAlumno(diego.get().getId()));
		lineas.add(dia + ";" + errado + ";;150.00;PEN;" + prefijo + "05;Agente");
		total = total.add(new BigDecimal("150.00"));
		StringBuilder csv = new StringBuilder("fecha_pago;codigo_alumno;referencia_deuda;monto;moneda;numero_operacion;canal\n");
		lineas.forEach(l -> csv.append(l).append('\n'));
		csv.append("TOTAL;").append(total.toPlainString()).append(';').append(lineas.size()).append('\n');
		return new Archivo("recaudacion-demo-" + dia + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8), total);
	}

	private static BigDecimal linea(List<String> lineas, String dia, Alumno alumno, Cuota referida, BigDecimal monto,
			String operacion) {
		lineas.add(dia + ";" + CodigoPago.deAlumno(alumno.getId()) + ";"
				+ (referida == null ? "" : CodigoPago.deCuota(referida.getId())) + ";" + monto.toPlainString() + ";PEN;"
				+ operacion + ";Ventanilla");
		return monto;
	}

	/** El mismo código con el último dígito (el verificador) cambiado: no corresponde a nadie. */
	static String codigoErrado(String codigo) {
		char ultimo = codigo.charAt(codigo.length() - 1);
		return codigo.substring(0, codigo.length() - 1) + (char) ('0' + ((ultimo - '0' + 1) % 10));
	}

	private Optional<Alumno> alumno(String dni) {
		return alumnos.findByDocumentoTipoAndDocumentoNumero(TipoDocumento.DNI, dni);
	}

	private List<Cuota> porPagar(Alumno alumno) {
		return cuotas.findByAlumnoIdOrderByFechaVencimientoAscIdAsc(alumno.getId()).stream()
				.filter(c -> c.admiteCobro() && !c.anulacionPendiente() && c.saldo().signum() > 0).toList();
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

	private record Archivo(String nombre, byte[] contenido, BigDecimal total) {
	}
}
