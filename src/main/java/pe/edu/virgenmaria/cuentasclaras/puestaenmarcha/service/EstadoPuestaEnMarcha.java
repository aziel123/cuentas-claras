package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.LoteSaldoInicialRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.LogoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoTecnico;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista.Estado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 8, tanda 2 (sección 3.5): calcula la lista de verificación de la puesta en marcha de un colegio DESDE LOS DATOS
 * (nada se marca a mano, salvo los dos «aceptos» firmados por la Promotoría). Son los 14 pasos del diseño: 12
 * obligatorios para pasar a producción y 2 opcionales (pago en línea y recaudación bancaria). En PRODUCCION se sigue
 * mostrando como «Salud de la configuración» (un punto puede volver a pendiente, por ejemplo si se desactiva la única
 * Caja).
 * <p>
 * Se llama sin transacción: fija el colegio con {@code ContextoColegio.en} (el operador consulta un colegio que no es el
 * suyo) y lee con una transacción de solo lectura. Solo lee conteos y sí/no: nunca devuelve nombres, documentos, montos
 * ni números de cuenta (lo ve también el operador, E2).
 */
@Service
public class EstadoPuestaEnMarcha {

	public static final String DATOS = "datos";

	public static final String EQUIPO = "equipo";

	public static final String ANIO = "anio";

	public static final String PENSIONES = "pensiones";

	public static final String ALUMNOS = "alumnos";

	public static final String SALDO_INICIAL = "saldo-inicial";

	public static final String OSE = "ose";

	public static final String CUENTA_BANCARIA = "cuenta-bancaria";

	public static final String MENSAJERIA = "mensajeria";

	public static final String CORREO_CONTROL = "correo-control";

	public static final String PRIVACIDAD = "privacidad";

	public static final String RESPALDO = "respaldo";

	public static final String PASARELA = "pasarela";

	public static final String RECAUDACION = "recaudacion";

	/** Sprint 10, tanda 2: las asignaciones del sprint 9 sin áreas del catálogo (esos docentes no califican). */
	public static final String AREAS_DOCENTES = "areas-docentes";

	/** Los 14 pasos en el orden de la sección 3.5, y el 15 del sprint 10 (las áreas de cada docente; no bloquea). */
	public static final List<String> PASOS = List.of(DATOS, EQUIPO, ANIO, PENSIONES, ALUMNOS, SALDO_INICIAL, OSE,
			CUENTA_BANCARIA, MENSAJERIA, CORREO_CONTROL, PRIVACIDAD, RESPALDO, PASARELA, RECAUDACION, AREAS_DOCENTES);

	private static final Set<EstadoMensaje> SALIDOS = EnumSet.of(EstadoMensaje.ENVIADO, EstadoMensaje.ENTREGADO,
			EstadoMensaje.LEIDO);

	private final ColegioRepository colegios;

	private final UsuarioRepository usuarios;

	private final PrimeraPromotoriaRepository primeras;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final MatriculaRepository matriculas;

	private final PlanPensionRepository planes;

	private final LoteSaldoInicialRepository lotes;

	private final ConectorColegioRepository conectores;

	private final MensajeRepository mensajes;

	private final FirmaOperacionRepository firmas;

	private final LogoColegioRepository logos;

	private final ConfiguracionColegioRepository configuracion;

	private final EstadoTecnico estadoTecnico;

	private final Environment entorno;

	private final TransactionTemplate lectura;

	public EstadoPuestaEnMarcha(ColegioRepository colegios, UsuarioRepository usuarios, PrimeraPromotoriaRepository primeras,
			AnioEscolarRepository anios, SeccionRepository secciones, MatriculaRepository matriculas,
			PlanPensionRepository planes, LoteSaldoInicialRepository lotes, ConectorColegioRepository conectores,
			MensajeRepository mensajes, FirmaOperacionRepository firmas, LogoColegioRepository logos,
			ConfiguracionColegioRepository configuracion, EstadoTecnico estadoTecnico, Environment entorno,
			PlatformTransactionManager transacciones,
			pe.edu.virgenmaria.cuentasclaras.academico.api.AsignacionesPorCompletar porCompletar) {
		this.porCompletar = porCompletar;
		this.colegios = colegios;
		this.usuarios = usuarios;
		this.primeras = primeras;
		this.anios = anios;
		this.secciones = secciones;
		this.matriculas = matriculas;
		this.planes = planes;
		this.lotes = lotes;
		this.conectores = conectores;
		this.mensajes = mensajes;
		this.firmas = firmas;
		this.logos = logos;
		this.configuracion = configuracion;
		this.estadoTecnico = estadoTecnico;
		this.entorno = entorno;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	private final pe.edu.virgenmaria.cuentasclaras.academico.api.AsignacionesPorCompletar porCompletar;

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
		Set<Nivel> niveles = colegio.getNiveles();
		List<PuntoLista> puntos = new ArrayList<>();

		// 1. Datos y logo del colegio.
		boolean datos = lleno(colegio.getRazonSocial()) && lleno(colegio.getRuc()) && lleno(colegio.getDireccion())
				&& lleno(colegio.getCorreoInstitucional());
		boolean conLogo = logos.existsByVigenteTrue();
		puntos.add(new PuntoLista(DATOS, "Datos y logo del colegio", estado(datos),
				(datos ? "Razón social, RUC, dirección y correo completos" : "Faltan datos de la ficha")
						+ (conLogo ? "; con logo" : "; sin logo (opcional)"), "/colegio/marca"));

		// 2. Equipo con segregación: la Promotoría activada, y una Dirección y una Caja activas que no son de Promotoría.
		Optional<Usuario> titular = primeras.findFirstByIdNotNull().flatMap(p -> usuarios.findById(p.getUsuarioId()));
		boolean activado = titular.map(u -> u.isActivo() && !u.isDebeCambiarClave()).orElse(true);
		long promotores = usuarios.contarActivosConRol(Rol.PROMOTOR);
		long directores = sinPromotoria(Rol.DIRECTOR);
		long cajas = sinPromotoria(Rol.CAJA);
		// Sprint 9, tanda 1 (decisión 141): una cuenta de antes de V32 con DOCENTE y CAJA o ADMINISTRACION queda pendiente
		// hasta que Promotoría la corrija (el docente no ve deudas).
		long docentesConCobranza = usuarios.activosConRol(Rol.DOCENTE).stream()
				.filter(u -> u.getRoles().contains(Rol.CAJA) || u.getRoles().contains(Rol.ADMINISTRACION)).count();
		// Sprint 9, tanda 2 (A8): con menos de 2 personas activas de Promotoría o Dirección, nadie distinto aprueba lo que
		// otra pide (la segregación no se cumple).
		long gobierno = usuarios.activosConRol(Rol.PROMOTOR).stream().filter(u -> u.getApoderadoId() == null)
				.map(Usuario::getId).collect(Collectors.toSet()).size() + usuarios.activosConRol(Rol.DIRECTOR).stream()
				.filter(u -> u.getApoderadoId() == null && !u.getRoles().contains(Rol.PROMOTOR)).count();
		boolean equipo = activado && promotores >= 1 && directores >= 1 && cajas >= 1 && docentesConCobranza == 0
				&& gobierno >= 2;
		puntos.add(new PuntoLista(EQUIPO, "Equipo con segregación", estado(equipo), "Promotoría: " + promotores
				+ (activado ? "" : " (el titular aún no activa su cuenta)") + "; Dirección: " + directores + "; Caja: " + cajas
				+ (directores >= 1 && cajas >= 1 ? "" : " (hace falta una Dirección y una Caja activas, distintas de la "
						+ "Promotoría: quien cobra no aprueba)")
				+ (docentesConCobranza == 0 ? "" : "; " + docentesConCobranza + " cuenta(s) combinan Docente con Caja o "
						+ "Administración: corrígelas (el docente no ve deudas)")
				+ (gobierno >= 2 ? "" : "; hay menos de 2 personas de Promotoría o Dirección: nadie distinto aprueba lo "
						+ "que la otra pide"), "/usuarios"));

		// 3. Año escolar y secciones: el año vigente con al menos una sección por nivel ofrecido.
		Optional<AnioEscolar> vigente = anios.findByEstado(EstadoAnioEscolar.EN_CURSO)
				.or(() -> anios.findAllByOrderByAnioDesc().stream().findFirst());
		Set<Nivel> conSeccion = vigente.map(a -> secciones.findByAnioEscolarIdOrderByGradoAscNombreAsc(a.getId()).stream()
				.filter(Seccion::isActiva).map(Seccion::nivel).collect(Collectors.toSet())).orElse(Set.of());
		boolean anio = vigente.isPresent() && conSeccion.containsAll(niveles);
		puntos.add(new PuntoLista(ANIO, "Año escolar y secciones", estado(anio), vigente.map(a -> "Año " + a.getAnio()
				+ ": secciones en " + conSeccion.size() + " de " + niveles.size() + " nivel(es)").orElse("Sin año escolar"),
				"/colegio"));

		// 4. Pensiones: un plan APROBADO (vigente) para cada nivel ofrecido.
		Set<Nivel> conPlan = vigente.map(a -> planes.findByAnioEscolarIdAndVigenteTrue(a.getId()).stream()
				.map(PlanPension::getNivel).collect(Collectors.toSet())).orElse(Set.of());
		boolean pensiones = vigente.isPresent() && conPlan.containsAll(niveles);
		puntos.add(new PuntoLista(PENSIONES, "Pensiones", estado(pensiones), "Planes aprobados: " + conPlan.size() + " de "
				+ niveles.size() + " nivel(es)", "/pensiones"));

		// 5. Alumnos matriculados en el año vigente.
		long matriculados = vigente.map(a -> matriculas.countByAnioEscolarIdAndEstado(a.getId(), EstadoMatricula.ACTIVA))
				.orElse(0L);
		puntos.add(new PuntoLista(ALUMNOS, "Alumnos", estado(matriculados > 0), "Alumnos matriculados: " + matriculados,
				"/alumnos"));

		// 6. Saldo inicial: un lote CONFIRMADO o «el colegio empieza sin deudas anteriores» firmado por Promotoría.
		boolean loteConfirmado = lotes.findAllByOrderByCreadoEnDescIdDesc().stream()
				.anyMatch(l -> l.getEstado() == EstadoLote.CONFIRMADO);
		boolean sinSaldo = firmas.existsByClave(ClaveFirma.colegioSinSaldoInicial(colegioId));
		puntos.add(new PuntoLista(SALDO_INICIAL, "Saldo inicial", estado(loteConfirmado || sinSaldo), loteConfirmado
				? "Lote de saldo inicial confirmado" : sinSaldo ? "Promotoría firmó que el colegio empieza sin deudas anteriores"
						: "Sin lote confirmado (o la Promotoría firma que no hay deudas anteriores)", "/pensiones/saldo-inicial"));

		// 7. Comprobantes: un OSE real vigente y probado, o el simulado aceptado con la firma de Promotoría.
		Optional<ConectorColegio> ose = vigenteDe(TipoConector.OSE);
		boolean oseReal = ose.filter(c -> c.getProveedor() == ProveedorConector.NUBEFACT && c.getModo() == ModoConector.REAL
				&& Boolean.TRUE.equals(c.getPruebaOk())).isPresent();
		boolean oseSimulado = firmas.existsByClave(ClaveFirma.colegioOseSimulado(colegioId));
		puntos.add(new PuntoLista(OSE, "Comprobantes (OSE)", estado(oseReal || oseSimulado), oseReal
				? "OSE real vigente y probado" : oseSimulado ? "Promotoría firmó que usa el comprobante simulado y emite el "
						+ "legal aparte" : ose.map(c -> "Conector " + c.getProveedor().etiqueta() + " ("
								+ c.getModo().etiqueta().toLowerCase(Locale.ROOT) + "): falta el OSE real o el acepto firmado")
								.orElse("Sin conector OSE vigente"), "/conectores"));

		// 8. Cuenta bancaria vigente.
		boolean cuenta = vigenteDe(TipoConector.CUENTA_BANCARIA).isPresent();
		puntos.add(new PuntoLista(CUENTA_BANCARIA, "Cuenta bancaria", estado(cuenta), cuenta
				? "Cuenta bancaria vigente (aprobada por otra persona)" : "Sin cuenta bancaria vigente", "/conectores"));

		// 9. WhatsApp y correo: un canal de la plataforma activo y un mensaje ya salió a la Promotoría.
		boolean canal = canalActivo();
		List<Long> promotoria = usuarios.activosConRol(Rol.PROMOTOR).stream().map(Usuario::getId).toList();
		boolean salio = !promotoria.isEmpty() && mensajes.existsByUsuarioIdInAndEstadoIn(promotoria, SALIDOS);
		puntos.add(new PuntoLista(MENSAJERIA, "WhatsApp y correo", estado(canal && salio), !canal
				? "La plataforma no tiene un canal de mensajes activo" : salio ? "Canal activo; un mensaje ya llegó a la "
						+ "Promotoría" : "Canal activo; todavía no sale ningún mensaje a la Promotoría", null));

		// 10. Correo externo de control (lo registra el operador).
		boolean correo = configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
				.isPresent();
		puntos.add(new PuntoLista(CORREO_CONTROL, "Correo externo de control", estado(correo),
				correo ? "Registrado por el operador" : "Lo registra el operador (el contador o un tercero del contrato)",
				null));

		// 11. Aviso de privacidad del colegio aprobado por Dirección.
		boolean privacidad = lleno(colegio.getAvisoPrivacidadVersion());
		puntos.add(new PuntoLista(PRIVACIDAD, "Aviso de privacidad del colegio", estado(privacidad),
				privacidad ? "Versión " + colegio.getAvisoPrivacidadVersion() + " aprobada por Dirección"
						: "Dirección aprueba la versión vigente", "/colegio/privacidad"));

		// 12. Respaldo de la instalación verificado y al día.
		EstadoRespaldo respaldo = estadoTecnico.respaldo(colegio.getId());
		boolean alDia = respaldo.existe() && respaldo.alDia() && !respaldo.faltanFilas();
		puntos.add(new PuntoLista(RESPALDO, "Respaldo", estado(alDia), !respaldo.existe() ? "Todavía no hay un respaldo "
				+ "de la instalación" : respaldo.faltanFilas() ? "El último respaldo encontró filas faltantes"
						: respaldo.alDia() ? "Último respaldo verificado y al día" : "El último respaldo está atrasado", null));

		// 13 y 14 (opcionales): pago en línea y recaudación bancaria.
		boolean pasarela = vigenteDe(TipoConector.PASARELA).isPresent();
		puntos.add(new PuntoLista(PASARELA, "Pago en línea", pasarela ? Estado.HECHO : Estado.OPCIONAL,
				pasarela ? "Pasarela vigente" : "Opcional: sin pago en línea", "/conectores"));
		boolean recaudacion = vigenteDe(TipoConector.RECAUDACION).isPresent();
		puntos.add(new PuntoLista(RECAUDACION, "Recaudación bancaria", recaudacion ? Estado.HECHO : Estado.OPCIONAL,
				recaudacion ? "Recaudación vigente" : "Opcional: sin recaudación por código de alumno", "/conectores"));

		// 15 (sprint 10, tanda 2; no bloquea): las asignaciones del sprint 9 sin áreas del catálogo no pueden poner notas.
		int sinAreas = porCompletar.sinAreasDelAnioEnCurso();
		puntos.add(new PuntoLista(AREAS_DOCENTES, "Áreas de cada docente", sinAreas == 0 ? Estado.HECHO : Estado.OPCIONAL,
				sinAreas == 0 ? "Todas las asignaciones tienen sus áreas del catálogo" : sinAreas
						+ " asignaciones sin áreas del catálogo: esos docentes no pueden poner notas", "/academico/asignaciones"));

		return new ListaVerificacion(colegio.getId(), colegio.getNombre(), colegio.getEstado(), List.copyOf(puntos));
	}

	private Optional<ConectorColegio> vigenteDe(TipoConector tipo) {
		return conectores.findFirstByTipoAndEstado(tipo, EstadoConector.VIGENTE);
	}

	/** Cuentas activas con ese rol que NO son de Promotoría (quien cobra no aprueba; Promotoría no se autocontrola). */
	private long sinPromotoria(Rol rol) {
		return usuarios.activosConRol(rol).stream().filter(u -> !u.getRoles().contains(Rol.PROMOTOR)).count();
	}

	/** Un canal de mensajes de la plataforma que no sea NINGUNO (WhatsApp o correo). */
	private boolean canalActivo() {
		return activo("cuentasclaras.mensajeria.whatsapp.proveedor") || activo("cuentasclaras.mensajeria.correo.proveedor");
	}

	private boolean activo(String propiedad) {
		String valor = entorno.getProperty(propiedad, "NINGUNO");
		return !valor.isBlank() && !"NINGUNO".equalsIgnoreCase(valor.strip());
	}

	private static Estado estado(boolean hecho) {
		return hecho ? Estado.HECHO : Estado.PENDIENTE;
	}

	private static boolean lleno(String texto) {
		return texto != null && !texto.isBlank();
	}
}
