package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.LogoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista.Estado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 8, tanda 1 (sección 3.5): calcula la lista de verificación de un colegio DESDE LOS DATOS (nada se marca a mano).
 * Puntos de la tanda 1: datos del colegio, primera Promotoría activada, equipo con segregación (una Dirección y una Caja
 * activas, distintas de la Promotoría), año escolar con secciones por nivel ofrecido, alumnos, correo externo de control
 * y aviso de privacidad aprobado. La tanda 2 agrega pensiones, saldo inicial, conectores, WhatsApp y respaldo.
 * <p>
 * Se llama sin transacción: fija el colegio con {@code ContextoColegio.en} (el operador consulta un colegio que no es el
 * suyo) y lee con una transacción de solo lectura. Solo lee conteos y sí/no: nunca devuelve nombres ni documentos.
 */
@Service
public class EstadoPuestaEnMarcha {

	public static final String DATOS = "datos";

	public static final String TITULAR = "titular";

	public static final String EQUIPO = "equipo";

	public static final String ANIO = "anio";

	public static final String ALUMNOS = "alumnos";

	public static final String CORREO_CONTROL = "correo-control";

	public static final String PRIVACIDAD = "privacidad";

	private final ColegioRepository colegios;

	private final UsuarioRepository usuarios;

	private final PrimeraPromotoriaRepository primeras;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final AlumnoRepository alumnos;

	private final LogoColegioRepository logos;

	private final ConfiguracionColegioRepository configuracion;

	private final TransactionTemplate lectura;

	public EstadoPuestaEnMarcha(ColegioRepository colegios, UsuarioRepository usuarios, PrimeraPromotoriaRepository primeras,
			AnioEscolarRepository anios, SeccionRepository secciones, AlumnoRepository alumnos, LogoColegioRepository logos,
			ConfiguracionColegioRepository configuracion, PlatformTransactionManager transacciones) {
		this.colegios = colegios;
		this.usuarios = usuarios;
		this.primeras = primeras;
		this.anios = anios;
		this.secciones = secciones;
		this.alumnos = alumnos;
		this.logos = logos;
		this.configuracion = configuracion;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	/** La lista del colegio en contexto (la sesión de una persona del colegio). */
	public ListaVerificacion actual() {
		Long colegioId = ContextoColegio.actual();
		if (colegioId == null || colegioId <= 0) {
			throw new RecursoNoEncontradoException("Colegio no encontrado");
		}
		return lectura.execute(t -> calcular(colegioId));
	}

	/** La lista de otro colegio, para el operador: sin enlaces. */
	public ListaVerificacion paraOperador(long colegioId) {
		return ContextoColegio.en(colegioId, () -> lectura.execute(t -> calcular(colegioId))).paraOperador();
	}

	private ListaVerificacion calcular(long colegioId) {
		Colegio colegio = colegios.findById(colegioId).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
		List<PuntoLista> puntos = new ArrayList<>();

		boolean datos = lleno(colegio.getRazonSocial()) && lleno(colegio.getRuc()) && lleno(colegio.getDireccion())
				&& lleno(colegio.getCorreoInstitucional());
		boolean conLogo = logos.existsByVigenteTrue();
		puntos.add(new PuntoLista(DATOS, "Datos y logo del colegio", datos ? Estado.HECHO : Estado.PENDIENTE,
				(datos ? "Razón social, RUC, dirección y correo completos" : "Faltan datos de la ficha")
						+ (conLogo ? "; con logo" : "; sin logo (opcional)"), "/colegio/marca"));

		Optional<Usuario> titular = primeras.findFirstByIdNotNull().flatMap(p -> usuarios.findById(p.getUsuarioId()));
		boolean activado = titular.map(u -> !u.isDebeCambiarClave()).orElse(false);
		puntos.add(new PuntoLista(TITULAR, "Primera Promotoría activada", activado ? Estado.HECHO : Estado.PENDIENTE,
				titular.isEmpty() ? "Sin titular: el operador completa el alta"
						: activado ? "El titular activó su cuenta con su DNI" : "Enlace enviado; el titular aún no activa su cuenta",
				"/usuarios"));

		long promotores = usuarios.contarActivosConRol(Rol.PROMOTOR);
		long directores = usuarios.contarActivosConRol(Rol.DIRECTOR);
		long cajas = usuarios.contarActivosConRol(Rol.CAJA);
		boolean equipo = promotores >= 1 && directores >= 1 && cajas >= 1;
		puntos.add(new PuntoLista(EQUIPO, "Equipo con segregación", equipo ? Estado.HECHO : Estado.PENDIENTE,
				"Promotoría: " + promotores + "; Dirección: " + directores + "; Caja: " + cajas
						+ (equipo ? "" : " (hace falta al menos una Dirección y una Caja activas, distintas de la Promotoría)"),
				"/usuarios"));

		Optional<AnioEscolar> vigente = anios.findByEstado(EstadoAnioEscolar.EN_CURSO)
				.or(() -> anios.findAllByOrderByAnioDesc().stream().findFirst());
		long cantidadSecciones = vigente.map(a -> secciones.countByAnioEscolarId(a.getId())).orElse(0L);
		boolean anio = vigente.isPresent() && cantidadSecciones >= colegio.getNiveles().size();
		puntos.add(new PuntoLista(ANIO, "Año escolar y secciones", anio ? Estado.HECHO : Estado.PENDIENTE,
				vigente.map(a -> "Año " + a.getAnio() + ": " + cantidadSecciones + " sección(es) para " + colegio.getNiveles().size()
						+ " nivel(es)").orElse("Sin año escolar"), "/colegio"));

		long cantidadAlumnos = alumnos.count();
		puntos.add(new PuntoLista(ALUMNOS, "Alumnos", cantidadAlumnos > 0 ? Estado.HECHO : Estado.PENDIENTE,
				"Alumnos: " + cantidadAlumnos, "/alumnos"));

		boolean correo = configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
				.isPresent();
		puntos.add(new PuntoLista(CORREO_CONTROL, "Correo externo de control", correo ? Estado.HECHO : Estado.PENDIENTE,
				correo ? "Registrado por el operador" : "Lo registra el operador (el contador o un tercero del contrato)", null));

		boolean privacidad = lleno(colegio.getAvisoPrivacidadVersion());
		puntos.add(new PuntoLista(PRIVACIDAD, "Aviso de privacidad del colegio", privacidad ? Estado.HECHO : Estado.PENDIENTE,
				privacidad ? "Versión " + colegio.getAvisoPrivacidadVersion() + " aprobada por Dirección"
						: "Dirección aprueba la versión vigente", "/colegio/privacidad"));

		return new ListaVerificacion(colegio.getId(), colegio.getNombre(), colegio.getEstado(), List.copyOf(puntos));
	}

	private static boolean lleno(String texto) {
		return texto != null && !texto.isBlank();
	}
}
