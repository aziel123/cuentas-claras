package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.LogosColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.AltaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular.DatosTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular.OperadorFirmante;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular.TitularCreado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Sprint 8, tanda 1 (sección 3.3): el alta de un colegio por el operador de la plataforma, sin tocar la base. Son CUATRO
 * transacciones, en este orden, porque el colegio de la sesión no se puede cambiar con una transacción abierta y la ruta
 * de identidad exige no tener una abierta:
 * <ol>
 *   <li>{@code cc_sistema}, sin colegio en contexto: el INSERT del colegio (en preparación, {@code creado_por} = el
 *       operador) y {@code COLEGIO_CREADO} en la bitácora de la plataforma. Si falla, no se creó nada.</li>
 *   <li>{@code cc_app}, colegio de plataforma: la firma de la sesión del operador ({@code primera_promotoria:{colegio}}).</li>
 *   <li>{@code cc_sistema}, colegio nuevo ({@link AltaTitular}): la cuenta del titular, su marca de primera Promotoría,
 *       el rol PROMOTOR, el mensaje de activación (el enlace se genera al enviarlo) y {@code PRIMERA_PROMOTORIA_CREADA}
 *       en la bitácora del colegio nuevo, con el nombre del operador.</li>
 *   <li>{@code cc_sistema}, colegio nuevo: el año escolar inicial, el logo y {@code COLEGIO_CREADO} en su bitácora.</li>
 * </ol>
 * Si fallan la 2, la 3 o la 4, el colegio queda creado y la ficha dice qué falta: el operador reintenta con «Completar
 * alta». Después de la 3, el operador ya no puede crear cuentas en ese colegio (trg_usuario_nace).
 */
@Service
@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
public class AltaColegio {

	private static final Logger LOG = LoggerFactory.getLogger(AltaColegio.class);

	private final ColegioRepository colegios;

	private final UsuarioRepository usuarios;

	private final AnioEscolarRepository anios;

	private final ConfiguracionColegioRepository configuracion;

	private final pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository primeras;

	private final pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository firmas;

	private final AltaTitular titulares;

	private final LogosColegio logos;

	private final EjecucionPlataforma plataforma;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final PropiedadesPlataforma propiedades;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AltaColegio(ColegioRepository colegios, UsuarioRepository usuarios, AnioEscolarRepository anios,
			ConfiguracionColegioRepository configuracion, AltaTitular titulares, LogosColegio logos,
			EjecucionPlataforma plataforma, FirmaSesion firma, AuditoriaService auditoria, PropiedadesPlataforma propiedades,
			PlatformTransactionManager transacciones, Clock reloj,
			pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository primeras,
			pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository firmas) {
		this.primeras = primeras;
		this.firmas = firmas;
		this.colegios = colegios;
		this.usuarios = usuarios;
		this.anios = anios;
		this.configuracion = configuracion;
		this.titulares = titulares;
		this.logos = logos;
		this.plataforma = plataforma;
		this.firma = firma;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	/** Las cuatro transacciones del alta. {@code logo} puede ser {@code null}. */
	public ColegioCreado crear(AltaColegioRequest datos, byte[] logo, String nombreLogo) {
		OperadorFirmante operador = operador();
		String ruc = ValidadorRuc.validar(datos.ruc());
		DatosTitular titular = titulares.validar(new DatosTitular(datos.titularUsuario(), datos.titularNombre(),
				datos.titularTelefono(), datos.titularCorreo()), operador);
		AnioEscolar anioInicial = AnioEscolar.nuevo(datos.anio(), true, datos.inicioClases(), datos.finClases());
		String documento = datos.titularDocumento().strip().toUpperCase(Locale.ROOT);
		// Sección 3.1: una instalación DEDICADA tiene un solo colegio (el que ya opera, con cuentas).
		List<Colegio> existentes = transaccion.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO));
		if (!propiedades.compartida() && existentes.stream().anyMatch(c -> usuariosDe(c) > 0)) {
			throw new ReglaNegocioException("Esta instalación es DEDICADA (un solo colegio). Para dar de alta otro colegio, "
					+ "despliega una instalación compartida (CC_INSTALACION=COMPARTIDA).");
		}
		// 1. El colegio, en preparación.
		Long colegioId = plataforma.como(() -> {
			if (colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO).size() >= propiedades.maxColegios()) {
				throw new ReglaNegocioException("Esta instalación ya tiene " + propiedades.maxColegios() + " colegios (decisión "
						+ "138): el siguiente va en otra instalación.");
			}
			colegios.findFirstByRucOrderByIdAsc(ruc).ifPresent(otro -> LOG.info("El RUC del colegio nuevo ya existe en el "
					+ "colegio {}: es otra sede del mismo RUC (sus series de comprobante no pueden repetirse).", otro.getId()));
			Colegio colegio;
			try {
				colegio = colegios.saveAndFlush(Colegio.enPreparacion(datos.nombre(), datos.razonSocial(), ruc,
						datos.direccion(), datos.correoInstitucional(), datos.niveles(), datos.titularNombre(), documento,
						datos.contratoCodigo()));
			}
			catch (DataIntegrityViolationException e) {
				throw new ReglaNegocioException("No se pudo crear el colegio: revisa el RUC, los niveles y el documento del "
						+ "titular.");
			}
			auditoria.registrar(AccionAuditoria.COLEGIO_CREADO, "colegio", colegio.getId().toString(), null,
					resumen(colegio), "El operador " + operador.nombreUsuario() + " dio de alta el colegio " + colegio.getNombre()
							+ " (RUC " + ruc + ", contrato " + (colegio.getContratoCodigo() == null ? "sin código"
									: colegio.getContratoCodigo()) + "), en preparación. Titular: " + colegio.getTitularNombre()
							+ " (" + Enmascarar.documento("DNI", documento) + ").");
			return colegio.getId();
		});
		// 2 y 3. La firma y el titular. 4. El año escolar y el logo.
		String pendiente = null;
		TitularCreado creado = null;
		try {
			creado = firmarYCrearTitular(colegioId, titular, operador);
		}
		catch (RuntimeException e) {
			LOG.warn("El alta del colegio {} quedó sin titular: {}", colegioId, e.getClass().getSimpleName());
			pendiente = "No se pudo crear la cuenta del titular (" + mensaje(e) + "). Completa el alta desde la ficha.";
		}
		if (pendiente == null) {
			try {
				completarDatosIniciales(colegioId, anioInicial, logo, nombreLogo, operador);
			}
			catch (RuntimeException e) {
				LOG.warn("El alta del colegio {} quedó sin año escolar o logo: {}", colegioId, e.getClass().getSimpleName());
				pendiente = "El titular ya tiene su enlace, pero no se pudo crear el año escolar o guardar el logo ("
						+ mensaje(e) + "). La Promotoría los completa desde su colegio.";
			}
		}
		Colegio colegio = colegios.findById(colegioId).orElseThrow();
		return new ColegioCreado(colegioId, colegio.getNombre(), creado == null ? null : creado.nombreUsuario(),
				creado == null ? null : creado.canal(), creado == null ? null : creado.destino(), pendiente);
	}

	/** «Completar alta»: reintenta la firma y el titular (transacciones 2 y 3) de un colegio que quedó sin titular. */
	public ColegioCreado completarAlta(long colegioId, DatosTitular datosTitular) {
		OperadorFirmante operador = operador();
		Colegio colegio = colegioEnPreparacion(colegioId);
		DatosTitular titular = titulares.validar(datosTitular, operador);
		TitularCreado creado = firmarYCrearTitular(colegioId, titular, operador);
		return new ColegioCreado(colegioId, colegio.getNombre(), creado.nombreUsuario(), creado.canal(), creado.destino(),
				null);
	}

	/** Corrige el contacto del titular y reenvía su enlace (solo antes de la activación), con la firma del operador. */
	public ColegioCreado reenviarEnlaceTitular(long colegioId, ContactoTitularRequest contacto) {
		OperadorFirmante operador = operador();
		Colegio colegio = colegioEnPreparacion(colegioId);
		Usuario titular = ContextoColegio.en(colegioId, () -> transaccion.execute(t -> titulares.titularSinActivar()));
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.titularContacto(colegioId, titular.getId(),
				titular.getVersion())));
		TitularCreado creado = titulares.corregirContacto(colegioId, contacto.telefonoWhatsapp(), contacto.correo(),
				operador);
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.TITULAR_CONTACTO_CORREGIDO, "colegio",
				Long.toString(colegioId), null, creado.destino(), "El operador " + operador.nombreUsuario()
						+ " corrigió el contacto del titular del colegio " + colegio.getNombre() + " y le reenvió su enlace."));
		return new ColegioCreado(colegioId, colegio.getNombre(), creado.nombreUsuario(), creado.canal(), creado.destino(),
				null);
	}

	/** La ficha de un colegio en preparación (el operador no toca el estado; en producción solo la Promotoría). */
	public void corregirFicha(long colegioId, FichaColegioRequest ficha) {
		OperadorFirmante operador = operador();
		String ruc = ValidadorRuc.validar(ficha.ruc());
		String documento = ficha.titularDocumento().strip().toUpperCase(Locale.ROOT);
		plataforma.ejecutar(() -> {
			Colegio colegio = colegioEnPreparacion(colegioId);
			String anterior = resumen(colegio);
			colegio.corregirFicha(ficha.nombre(), ficha.razonSocial(), ficha.direccion(), ficha.correoInstitucional());
			colegio.cambiarRuc(ruc);
			colegio.cambiarNiveles(ficha.niveles());
			colegio.cambiarTitular(ficha.titularNombre(), documento, ficha.contratoCodigo());
			try {
				colegios.saveAndFlush(colegio);
			}
			catch (DataIntegrityViolationException e) {
				throw new ReglaNegocioException("No se pudo corregir la ficha: revisa el RUC, los niveles y el documento del "
						+ "titular.");
			}
			auditoria.registrar(AccionAuditoria.FICHA_COLEGIO_CORREGIDA, "colegio", Long.toString(colegioId), anterior,
					resumen(colegio), "El operador " + operador.nombreUsuario() + " corrigió la ficha del colegio.");
		});
	}

	/**
	 * H10: registra o cambia el correo externo de control (resumen diario y huella) de un colegio, con la firma del
	 * operador por cada fila ({@code configuracion_colegio:{colegio}:{clave}:{version}}). Queda en las dos bitácoras.
	 */
	public void registrarCorreoControl(long colegioId, String correo) {
		OperadorFirmante operador = operador();
		Colegio colegio = colegios.findById(colegioId).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
		String valor = correo == null ? "" : correo.strip();
		if (valor.isEmpty() || !valor.contains("@") || valor.length() > 100) {
			throw new ReglaNegocioException("Escribe el correo externo de control (hasta 100 caracteres).");
		}
		if (operador.correo() != null && valor.equalsIgnoreCase(operador.correo())) {
			throw new ReglaNegocioException("Ese correo es tuyo: el correo de control es del contador o de un tercero de "
					+ "confianza del colegio, nunca del operador.");
		}
		List<String> claves = List.of(ConfiguracionColegio.HUELLA_CORREO_EXTERNO, ConfiguracionColegio.RESUMEN_CORREO_EXTERNO);
		// La firma de cada fila, en el colegio de plataforma.
		transaccion.executeWithoutResult(t -> {
			for (String clave : claves) {
				// Una fila nueva firma la versión 0; un cambio, la versión que tendrá la fila (la actual más uno).
				long version = configuracion.findByColegioIdAndClave(colegioId, clave).map(f -> f.getVersion() + 1)
						.orElse(0L);
				firma.firmar(ClaveFirma.configuracionColegio(colegioId, clave, version));
			}
		});
		String[] anterior = { null };
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			LocalDateTime ahora = LocalDateTime.now(reloj);
			for (String clave : claves) {
				ConfiguracionColegio fila = configuracion.findByColegioIdAndClave(colegioId, clave).orElse(null);
				if (fila == null) {
					configuracion.saveAndFlush(ConfiguracionColegio.nueva(colegioId, clave, valor, operador.nombreUsuario(),
							ahora));
				}
				else {
					if (anterior[0] == null) {
						anterior[0] = Enmascarar.correo(fila.getValor());
					}
					fila.cambiar(valor, operador.nombreUsuario(), ahora);
					configuracion.saveAndFlush(fila);
				}
			}
			auditoria.registrar(AccionAuditoria.CORREO_CONTROL_REGISTRADO, "colegio", Long.toString(colegioId), anterior[0],
					Enmascarar.correo(valor), "El operador " + operador.nombreUsuario() + " registró el correo externo de "
							+ "control (recibe el resumen diario y la huella de la bitácora). Si no es el del contador o del "
							+ "tercero del contrato, avisa al proveedor.");
		}));
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.CORREO_CONTROL_REGISTRADO, "colegio",
				Long.toString(colegioId), anterior[0], Enmascarar.correo(valor), "Correo externo de control del colegio "
						+ colegio.getNombre() + "."));
	}

	private TitularCreado firmarYCrearTitular(long colegioId, DatosTitular titular, OperadorFirmante operador) {
		// Antes de firmar: un colegio con su primera Promotoría no tiene otra (la firma sería inútil).
		if (Boolean.TRUE.equals(ContextoColegio.en(colegioId, () -> transaccion.execute(t -> primeras.existsByIdNotNull())))) {
			throw new ReglaNegocioException("Este colegio ya tiene su primera Promotoría: no se crea otra. Las cuentas "
					+ "siguientes las crea la Promotoría del colegio.");
		}
		// La firma, en el colegio de plataforma, con el número de intento (única por clave; el trigger exige la última).
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.primeraPromotoria(colegioId,
				firmas.countByClaveStartingWith("primera_promotoria:" + colegioId + ":") + 1)));
		return titulares.crearPrimeraPromotoria(colegioId, titular, operador);
	}

	private void completarDatosIniciales(long colegioId, AnioEscolar anioInicial, byte[] logo, String nombreLogo,
			OperadorFirmante operador) {
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			if (!anios.existsByAnio(anioInicial.getAnio())) {
				anios.saveAndFlush(AnioEscolar.nuevo(anioInicial.getAnio(), true, anioInicial.getInicioClases(),
						anioInicial.getFinClases()));
			}
			if (logo != null && logo.length > 0) {
				logos.guardar(logo, nombreLogo);
			}
			auditoria.registrar(AccionAuditoria.COLEGIO_CREADO, "colegio", Long.toString(colegioId), null,
					"año escolar " + anioInicial.getAnio() + (logo != null && logo.length > 0 ? "; con logo" : "; sin logo"),
					"Tu colegio y tu cuenta los creó el operador " + operador.nombreUsuario() + " de la plataforma. El año "
							+ "escolar " + anioInicial.getAnio() + " quedó en curso.");
		}));
	}

	private Colegio colegioEnPreparacion(long colegioId) {
		Colegio colegio = colegios.findById(colegioId).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
		if (colegio.getEstado() != EstadoColegio.PREPARACION) {
			throw new ReglaNegocioException("El colegio ya está en producción: el operador ya no cambia su ficha ni su "
					+ "titular. Lo hace su Promotoría desde su colegio.");
		}
		return colegio;
	}

	private long usuariosDe(Colegio colegio) {
		return ContextoColegio.en(colegio.getId(), () -> transaccion.execute(t -> usuarios.count()));
	}

	/** El operador en sesión, como firmante: su cuenta en el colegio de plataforma. */
	private OperadorFirmante operador() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
			throw new AccessDeniedException("Se requiere un operador en sesión");
		}
		String correo = transaccion.execute(t -> usuarios.findById(usuario.usuarioId()).map(Usuario::getCorreo).orElse(null));
		return new OperadorFirmante(usuario.usuarioId(), usuario.colegioId(), usuario.getUsername(), correo);
	}

	private static String resumen(Colegio c) {
		return "nombre=" + c.getNombre() + "; razón social=" + c.getRazonSocial() + "; RUC=" + c.getRuc() + "; niveles="
				+ c.getNiveles() + "; contrato=" + Objects.requireNonNullElse(c.getContratoCodigo(), "-") + "; titular="
				+ c.getTitularNombre();
	}

	private static String mensaje(RuntimeException e) {
		return e instanceof ReglaNegocioException ? e.getMessage() : "error técnico " + e.getClass().getSimpleName();
	}
}
