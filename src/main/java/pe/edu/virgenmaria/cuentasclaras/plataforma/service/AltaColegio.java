package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.LogosColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CambioCorreoControl;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.CambioCorreoControlRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.AltaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.TamanoInstalacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
 *   <li>{@code cc_sistema}, colegio nuevo: el logo y {@code COLEGIO_CREADO} en su bitácora (sprint 11, tanda 1: sin año
 *       escolar; lo configura el colegio, sección 3.7).</li>
 * </ol>
 * Si fallan la 2, la 3 o la 4, el colegio queda creado y la ficha dice qué falta: el operador reintenta con «Completar
 * alta». Después de la 3, el operador ya no puede crear cuentas en ese colegio (trg_usuario_nace).
 * <p>
 * Correcciones del sprint 8:
 * <ul>
 *   <li>QA-S8-1: el logo se valida ANTES de crear nada (un logo inválido ya no revierte el año escolar).</li>
 *   <li>QA-S8-2: «Completar alta» exige antes el DNI del titular en la ficha (la activación lo pide).</li>
 *   <li>QA-S8-8: el alta respeta el tope de alumnos de la instalación ({@code max-alumnos}, decisión 138).</li>
 *   <li>S8-A1: el reenvío del enlace avisa al titular, al correo institucional y al correo de control; cambiar el contacto
 *       del titular lo propone un operador y lo aplica OTRO; el DNI del titular no cambia una vez creada su cuenta.</li>
 *   <li>S8-A2: en un colegio en producción el operador solo PROPONE el correo externo de control y lo aprueba la
 *       Promotoría; en preparación lo registra él, con aviso al correo anterior, al institucional y a la Promotoría.</li>
 *   <li>S8-M5: la ficha la corrige el operador con su firma, solo en preparación y mientras el titular no active su cuenta,
 *       con aviso al titular, al correo institucional y al correo de control.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
public class AltaColegio {

	private static final Logger LOG = LoggerFactory.getLogger(AltaColegio.class);

	private final ColegioRepository colegios;

	private final UsuarioRepository usuarios;

	private final AnioEscolarRepository anios;

	private final ConfiguracionColegioRepository configuracion;

	private final CambioCorreoControlRepository cambiosCorreo;

	private final PrimeraPromotoriaRepository primeras;

	private final FirmaOperacionRepository firmas;

	private final AltaTitular titulares;

	private final LogosColegio logos;

	private final TamanoInstalacion tamano;

	private final EjecucionPlataforma plataforma;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final PropiedadesPlataforma propiedades;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AltaColegio(ColegioRepository colegios, UsuarioRepository usuarios, AnioEscolarRepository anios,
			ConfiguracionColegioRepository configuracion, CambioCorreoControlRepository cambiosCorreo, AltaTitular titulares,
			LogosColegio logos, TamanoInstalacion tamano, EjecucionPlataforma plataforma, FirmaSesion firma,
			AuditoriaService auditoria, ApplicationEventPublisher eventos, PropiedadesPlataforma propiedades,
			PlatformTransactionManager transacciones, Clock reloj, PrimeraPromotoriaRepository primeras,
			FirmaOperacionRepository firmas) {
		this.primeras = primeras;
		this.firmas = firmas;
		this.colegios = colegios;
		this.usuarios = usuarios;
		this.anios = anios;
		this.configuracion = configuracion;
		this.cambiosCorreo = cambiosCorreo;
		this.titulares = titulares;
		this.logos = logos;
		this.tamano = tamano;
		this.plataforma = plataforma;
		this.firma = firma;
		this.auditoria = auditoria;
		this.eventos = eventos;
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
		String documento = datos.titularDocumento().strip().toUpperCase(Locale.ROOT);
		boolean conLogo = logo != null && logo.length > 0;
		// QA-S8-1: el logo se valida antes de crear nada (si no sirve, no hay colegio a medias ni año escolar perdido).
		if (conLogo) {
			logos.validar(logo, nombreLogo);
		}
		// Sección 3.1: una instalación DEDICADA tiene un solo colegio (el que ya opera, con cuentas).
		List<Colegio> existentes = transaccion.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO));
		if (!propiedades.compartida() && existentes.stream().anyMatch(c -> usuariosDe(c) > 0)) {
			throw new ReglaNegocioException("Esta instalación es DEDICADA (un solo colegio). Para dar de alta otro colegio, "
					+ "despliega una instalación compartida (CC_INSTALACION=COMPARTIDA).");
		}
		// QA-S8-8 (decisión 138): el tope de alumnos de la instalación (el de colegios se revisa al insertar).
		long alumnos = tamano.alumnos();
		if (alumnos >= propiedades.maxAlumnos()) {
			throw new ReglaNegocioException("Esta instalación ya tiene " + alumnos + " alumnos (el tope es "
					+ propiedades.maxAlumnos() + ", decisión 138): el colegio siguiente va en otra instalación.");
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
		// 2 y 3. La firma y el titular. 4. El logo (sprint 11, tanda 1: el año escolar lo crea el colegio).
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
				completarDatosIniciales(colegioId, conLogo ? logo : null, nombreLogo, operador);
			}
			catch (RuntimeException e) {
				LOG.warn("El alta del colegio {} quedó sin logo: {}", colegioId, e.getClass().getSimpleName());
				pendiente = "El titular ya tiene su enlace, pero no se pudo guardar el logo (" + mensaje(e) + "). La "
						+ "Promotoría lo sube desde su colegio.";
			}
		}
		Colegio colegio = colegios.findById(colegioId).orElseThrow();
		return new ColegioCreado(colegioId, colegio.getNombre(), creado == null ? null : creado.nombreUsuario(),
				creado == null ? null : creado.canal(), creado == null ? null : creado.destino(), pendiente);
	}

	/**
	 * «Completar alta»: reintenta la firma y el titular (transacciones 2 y 3) de un colegio que quedó sin titular.
	 * QA-S8-2: exige antes el DNI del titular en la ficha (la activación lo pide; sin él no hay cómo confirmar al titular).
	 */
	public ColegioCreado completarAlta(long colegioId, DatosTitular datosTitular) {
		OperadorFirmante operador = operador();
		Colegio colegio = colegioEnPreparacion(colegioId);
		if (colegio.getTitularDocumento() == null || colegio.getTitularDocumento().isBlank()) {
			throw new ReglaNegocioException("Antes de completar el alta, escribe en la ficha el DNI del titular (el del "
					+ "contrato): al activar su cuenta, el titular lo confirma.");
		}
		DatosTitular titular = titulares.validar(datosTitular, operador);
		TitularCreado creado = firmarYCrearTitular(colegioId, titular, operador);
		return new ColegioCreado(colegioId, colegio.getNombre(), creado.nombreUsuario(), creado.canal(), creado.destino(),
				null);
	}

	/**
	 * S8-A1: reenvía el enlace al MISMO contacto del titular (solo si nunca activó su cuenta), con aviso al titular, al
	 * correo institucional y al correo externo de control en la misma transacción.
	 */
	public ColegioCreado reenviarEnlaceTitular(long colegioId) {
		OperadorFirmante operador = operador();
		Colegio colegio = colegioEnPreparacion(colegioId);
		TitularCreado creado = titulares.reenviarEnlace(colegioId, operador);
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.TITULAR_ENLACE_REENVIADO, "colegio",
				Long.toString(colegioId), null, creado.destino(), "El operador " + operador.nombreUsuario()
						+ " reenvió el enlace del titular del colegio " + colegio.getNombre() + " (mismo contacto)."));
		return new ColegioCreado(colegioId, colegio.getNombre(), creado.nombreUsuario(), creado.canal(), creado.destino(),
				null);
	}

	/**
	 * S8-A1: un operador PROPONE cambiar el contacto del titular (con la firma de su sesión). No cambia nada hasta que
	 * OTRO operador lo aplique ({@link #aplicarContactoTitular}).
	 *
	 * @return el id del cambio propuesto
	 */
	public Long proponerContactoTitular(long colegioId, ContactoTitularRequest contacto) {
		OperadorFirmante operador = operador();
		Colegio colegio = colegioEnPreparacion(colegioId);
		AltaTitular.IntentoCambio intento = titulares.siguienteIntentoDeCambio(colegioId, contacto.telefonoWhatsapp(),
				contacto.correo(), contacto.motivo(), operador);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioContactoTitularPropuesto(colegioId,
				intento.titularId(), intento.numero())));
		Long id = titulares.proponerContacto(colegioId, contacto.telefonoWhatsapp(), contacto.correo(), contacto.motivo(),
				operador);
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.TITULAR_CONTACTO_PROPUESTO, "colegio",
				Long.toString(colegioId), null, "cambio " + id, "El operador " + operador.nombreUsuario() + " propuso cambiar "
						+ "el contacto del titular del colegio " + colegio.getNombre() + ". Lo aplica otro operador."));
		return id;
	}

	/**
	 * Sprint 11, tanda 1c (A1c-1): un operador PROPONE cambiar el contacto de una cuenta de Promotoría o Dirección que lo
	 * perdió (con la firma de su sesión), en un colegio que opera. Lo aplica OTRO operador ({@link #aplicarContactoTitular}).
	 */
	public Long proponerContactoDirectivo(long colegioId, String cuenta, ContactoTitularRequest contacto) {
		OperadorFirmante operador = operador();
		Colegio colegio = colegioQueOpera(colegioId);
		AltaTitular.IntentoCambio intento = titulares.siguienteIntentoDeCambio(colegioId, cuenta, contacto.telefonoWhatsapp(),
				contacto.correo(), contacto.motivo(), operador);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioContactoTitularPropuesto(colegioId,
				intento.titularId(), intento.numero())));
		Long id = titulares.proponerContacto(colegioId, cuenta, contacto.telefonoWhatsapp(), contacto.correo(),
				contacto.motivo(), operador);
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.TITULAR_CONTACTO_PROPUESTO, "colegio",
				Long.toString(colegioId), null, "cambio " + id, "El operador " + operador.nombreUsuario() + " propuso cambiar "
						+ "el contacto de la cuenta directiva " + cuenta + " del colegio " + colegio.getNombre()
						+ ". Lo aplica otro operador."));
		return id;
	}

	/**
	 * S8-A1: OTRO operador aplica el cambio del contacto del titular (con la firma de su sesión), con aviso al contacto
	 * anterior, al correo institucional y al correo externo de control. Exige el correo institucional del colegio.
	 */
	public ColegioCreado aplicarContactoTitular(long colegioId, long cambioId) {
		OperadorFirmante operador = operador();
		// Sprint 11, tanda 1c (A1c-1): también una cuenta directiva de un colegio en producción.
		Colegio colegio = colegioQueOpera(colegioId);
		if (colegio.getCorreoInstitucional() == null || !colegio.getCorreoInstitucional().contains("@")) {
			throw new ReglaNegocioException("Primero completa el correo institucional del colegio en la ficha: el cambio del "
					+ "contacto del titular se le avisa.");
		}
		// Antes de firmar (la firma es única por clave): quien aplica no es quien propuso y el cambio sigue por aplicar.
		titulares.validarResolucion(colegioId, cambioId, operador, true);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioContactoTitular(cambioId,
				pe.edu.virgenmaria.cuentasclaras.seguridad.model.CambioContactoTitular.Estado.APLICADO)));
		TitularCreado creado = titulares.aplicarContacto(colegioId, cambioId, operador);
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.TITULAR_CONTACTO_CORREGIDO, "colegio",
				Long.toString(colegioId), null, creado.destino(), "El operador " + operador.nombreUsuario() + " aplicó el "
						+ "cambio " + cambioId + " del contacto del titular del colegio " + colegio.getNombre()
						+ " y le reenvió su enlace. Se avisó al contacto anterior y al colegio."));
		return new ColegioCreado(colegioId, colegio.getNombre(), creado.nombreUsuario(), creado.canal(), creado.destino(),
				null);
	}

	/** S8-A1: un operador rechaza (o quien lo propuso retira) el cambio del contacto del titular, con su firma. */
	public void rechazarContactoTitular(long colegioId, long cambioId) {
		OperadorFirmante operador = operador();
		colegioDeLaPlataforma(colegioId);
		titulares.validarResolucion(colegioId, cambioId, operador, false);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioContactoTitular(cambioId,
				pe.edu.virgenmaria.cuentasclaras.seguridad.model.CambioContactoTitular.Estado.RECHAZADO)));
		titulares.rechazarContacto(colegioId, cambioId, operador);
	}

	/**
	 * S8-M5: la ficha de un colegio en preparación, con la firma del operador ({@code colegio:{id}:FICHA:{version}}) y solo
	 * mientras el titular no active su cuenta (después la corrige la Promotoría, con su firma). El DNI del titular no cambia
	 * una vez creada su cuenta (S8-A1): es lo que el titular confirma al activarla. Avisa en la misma transacción al
	 * titular, al correo institucional (el anterior y, si cambió, el nuevo) y al correo externo de control.
	 */
	public void corregirFicha(long colegioId, FichaColegioRequest ficha) {
		OperadorFirmante operador = operador();
		String ruc = ValidadorRuc.validar(ficha.ruc());
		String documento = ficha.titularDocumento().strip().toUpperCase(Locale.ROOT);
		Colegio actual = colegioEnPreparacion(colegioId);
		// Antes de firmar (la firma es única por clave y versión): lo que rechazaría el cambio.
		ContextoColegio.en(colegioId, () -> transaccion.executeWithoutResult(t -> validarFicha(actual, ficha, ruc,
				documento)));
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.fichaColegio(colegioId, actual.getVersion())));
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			Colegio colegio = colegioEnPreparacion(colegioId);
			if (!Objects.equals(colegio.getVersion(), actual.getVersion())) {
				throw new ReglaNegocioException("Alguien cambió la ficha mientras la editabas: vuelve a abrirla.");
			}
			validarFicha(colegio, ficha, ruc, documento);
			String anterior = resumen(colegio);
			String institucionalAntes = colegio.getCorreoInstitucional();
			List<String> cambios = camposCambiados(colegio, ficha, ruc, documento);
			Long titularId = primeras.findFirstByIdNotNull().map(p -> p.getUsuarioId()).orElse(null);
			// El aviso antes del cambio: al correo institucional ANTERIOR (el registrado ahora), al titular y al de control.
			// Un colegio sin ninguno de ellos (la fila de V1 o una del DBA, sin ficha) recibe solo el aviso al correo nuevo.
			boolean hayAntes = titularId != null || (institucionalAntes != null && institucionalAntes.contains("@"))
					|| configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO).isPresent();
			if (hayAntes) {
				eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.FICHA_CORREGIDA,
						List.of(operador.nombreUsuario(), String.join(", ", cambios)), "colegio", colegioId,
						"F" + colegio.getVersion(), titularId, true, true, false));
			}
			colegio.corregirFicha(ficha.nombre(), ficha.razonSocial(), ficha.direccion(), ficha.correoInstitucional());
			colegio.cambiarRuc(ruc);
			colegio.cambiarNiveles(ficha.niveles());
			colegio.cambiarTitular(ficha.titularNombre(), documento, ficha.contratoCodigo());
			colegio.firmarCambioDeFicha(operador.nombreUsuario());
			try {
				colegios.saveAndFlush(colegio);
			}
			catch (DataIntegrityViolationException e) {
				throw new ReglaNegocioException("No se pudo corregir la ficha: revisa el RUC, los niveles y el documento del "
						+ "titular.");
			}
			if (!hayAntes || !Objects.equals(institucionalAntes, colegio.getCorreoInstitucional())) {
				// Y al correo institucional NUEVO (ya registrado).
				eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.FICHA_CORREGIDA,
						List.of(operador.nombreUsuario(), String.join(", ", cambios)), "colegio", colegioId,
						"F" + actual.getVersion() + "N", null, true, false, false));
			}
			auditoria.registrar(AccionAuditoria.FICHA_COLEGIO_CORREGIDA, "colegio", Long.toString(colegioId), anterior,
					resumen(colegio), "El operador " + operador.nombreUsuario() + " corrigió la ficha del colegio ("
							+ String.join(", ", cambios) + "), con su firma. Se avisó al titular, al correo institucional y "
							+ "al correo externo de control.");
		}));
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.FICHA_COLEGIO_CORREGIDA, "colegio",
				Long.toString(colegioId), null, null, "El operador " + operador.nombreUsuario() + " corrigió la ficha del "
						+ "colegio " + actual.getNombre() + "."));
	}

	/** Compatibilidad: el correo de control sin motivo (solo sirve en preparación; en producción se pide el motivo). */
	public void registrarCorreoControl(long colegioId, String correo) {
		registrarCorreoControl(colegioId, correo, null);
	}

	/**
	 * H10 y S8-A2: el correo externo de control (resumen diario y huella) de un colegio.
	 * <ul>
	 *   <li><b>En preparación</b>, lo registra o cambia el operador con su firma por cada fila
	 *       ({@code configuracion_colegio:{colegio}:{clave}:{version}}), y se avisa en la misma transacción al correo
	 *       anterior, al correo institucional, a la Promotoría (si ya hay) y al correo nuevo.</li>
	 *   <li><b>En producción</b>, el operador solo lo PROPONE (con su firma) y se avisa al correo anterior, al institucional y
	 *       a toda la Promotoría ANTES de que surta efecto. Lo aprueba una Promotoría del colegio con su firma
	 *       ({@code puestaenmarcha.service.CorreoDeControl}); hasta entonces sigue el correo anterior.</li>
	 * </ul>
	 *
	 * @return {@code true} si quedó registrado; {@code false} si quedó propuesto (en producción)
	 */
	public boolean registrarCorreoControl(long colegioId, String correo, String motivo) {
		OperadorFirmante operador = operador();
		Colegio colegio = colegioDeLaPlataforma(colegioId);
		String valor = correo == null ? "" : correo.strip();
		if (valor.isEmpty() || !valor.contains("@") || valor.length() > 100) {
			throw new ReglaNegocioException("Escribe el correo externo de control (hasta 100 caracteres).");
		}
		if (operador.correo() != null && valor.equalsIgnoreCase(operador.correo())) {
			throw new ReglaNegocioException("Ese correo es tuyo: el correo de control es del contador o de un tercero de "
					+ "confianza del colegio, nunca del operador.");
		}
		// Sprint 11, tanda 1c (A1c-8): el correo de control nunca es el de una Promotoría activa del colegio (el testigo
		// independiente sería la misma persona). La base también lo rechaza.
		if (Boolean.TRUE.equals(ContextoColegio.en(colegioId, () -> transaccion.execute(t -> usuarios
				.activosConRol(pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR).stream()
				.anyMatch(u -> u.getCorreo() != null && pe.edu.virgenmaria.cuentasclaras.comun.texto.ContactoNormal
						.iguales(u.getCorreo(), valor)))))) {
			throw new ReglaNegocioException("Ese es el correo de una persona de Promotoría del colegio: el correo de control es "
					+ "del contador o de un tercero de confianza, nunca de la Promotoría.");
		}
		if (colegio.getEstado() == EstadoColegio.PRODUCCION) {
			proponerCorreoControl(colegio, valor, motivo, operador);
			return false;
		}
		List<String> claves = List.of(ConfiguracionColegio.HUELLA_CORREO_EXTERNO, ConfiguracionColegio.RESUMEN_CORREO_EXTERNO);
		if (transaccion.execute(t -> configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
				.map(f -> f.getValor().equalsIgnoreCase(valor)).orElse(false))) {
			throw new ReglaNegocioException("Ese ya es el correo externo de control del colegio.");
		}
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
			String actual = configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
					.map(ConfiguracionColegio::getValor).orElse(null);
			if (valor.equalsIgnoreCase(actual)) {
				throw new ReglaNegocioException("Ese ya es el correo externo de control del colegio.");
			}
			List<String> parametros = List.of(Enmascarar.correo(valor), "el operador " + operador.nombreUsuario());
			// El aviso antes del cambio: al correo anterior (el registrado ahora), al institucional y a la Promotoría.
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CORREO_CONTROL_CAMBIADO, parametros,
					"colegio", colegioId, "P" + LocalDateTime.now(reloj).toLocalDate(), null, true, true, true));
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
			// Y al correo nuevo (ya registrado).
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CORREO_CONTROL_CAMBIADO, parametros,
					"colegio", colegioId, "N" + LocalDateTime.now(reloj).toLocalDate(), null, false, true, false));
			auditoria.registrar(AccionAuditoria.CORREO_CONTROL_REGISTRADO, "colegio", Long.toString(colegioId), anterior[0],
					Enmascarar.correo(valor), "El operador " + operador.nombreUsuario() + " registró el correo externo de "
							+ "control (recibe el resumen diario y la huella de la bitácora), con su firma. Se avisó al correo "
							+ "anterior, al correo institucional y a la Promotoría. Si no es el del contador o del tercero del "
							+ "contrato, avisa al proveedor.");
		}));
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.CORREO_CONTROL_REGISTRADO, "colegio",
				Long.toString(colegioId), anterior[0], Enmascarar.correo(valor), "Correo externo de control del colegio "
						+ colegio.getNombre() + "."));
		return true;
	}

	/** S8-A2: en producción el operador solo propone; se avisa antes de que surta efecto y lo aprueba la Promotoría. */
	private void proponerCorreoControl(Colegio colegio, String valor, String motivo, OperadorFirmante operador) {
		long colegioId = colegio.getId();
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 10 || texto.length() > 500) {
			throw new ReglaNegocioException("En producción el correo de control lo aprueba la Promotoría del colegio: escribe "
					+ "el motivo del cambio (de 10 a 500 caracteres), por ejemplo la adenda del contrato.");
		}
		long intento = ContextoColegio.en(colegioId, () -> transaccion.execute(t -> {
			String actual = configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
					.map(ConfiguracionColegio::getValor).orElse(null);
			if (valor.equalsIgnoreCase(actual)) {
				throw new ReglaNegocioException("Ese ya es el correo externo de control del colegio.");
			}
			if (cambiosCorreo.findFirstByEstado(CambioCorreoControl.Estado.PROPUESTO).isPresent()) {
				throw new ReglaNegocioException("Ya hay un cambio del correo de control esperando a la Promotoría del colegio.");
			}
			return cambiosCorreo.count() + 1;
		}));
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioCorreoControlPropuesto(colegioId, intento)));
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			String actual = configuracion.findByColegioIdAndClave(colegioId, ConfiguracionColegio.HUELLA_CORREO_EXTERNO)
					.map(ConfiguracionColegio::getValor).orElse(null);
			CambioCorreoControl cambio = cambiosCorreo.saveAndFlush(CambioCorreoControl.propuesto(actual, valor, motivo,
					operador.usuarioId(), operador.plataformaId(), operador.nombreUsuario()));
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CORREO_CONTROL_PROPUESTO,
					List.of(operador.nombreUsuario(), Enmascarar.correo(valor)), "cambio_correo_control", cambio.getId(), null,
					null, true, true, true));
			auditoria.registrar(AccionAuditoria.CORREO_CONTROL_PROPUESTO, "cambio_correo_control", cambio.getId().toString(),
					actual == null ? null : Enmascarar.correo(actual), Enmascarar.correo(valor), "El operador "
							+ operador.nombreUsuario() + " propuso cambiar el correo externo de control (motivo: "
							+ cambio.getMotivo() + "). No cambia hasta que lo apruebe una persona de Promotoría del colegio. "
							+ "Se avisó al correo anterior, al institucional y a toda la Promotoría.");
		}));
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.CORREO_CONTROL_PROPUESTO, "colegio",
				Long.toString(colegioId), null, Enmascarar.correo(valor), "Propuesta del correo externo de control del "
						+ "colegio " + colegio.getNombre() + ": la aprueba su Promotoría."));
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

	/**
	 * Sprint 11, tanda 1 (sección 3.7): la transacción 4 solo guarda el logo y deja {@code COLEGIO_CREADO} en la bitácora del
	 * colegio. El año escolar NO lo crea el operador (la base lo rechaza con cualquier nombre de operador): lo configura el
	 * colegio, con la firma de su Promotoría o su Dirección.
	 */
	private void completarDatosIniciales(long colegioId, byte[] logo, String nombreLogo, OperadorFirmante operador) {
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			if (logo != null && logo.length > 0) {
				logos.guardar(logo, nombreLogo);
			}
			auditoria.registrar(AccionAuditoria.COLEGIO_CREADO, "colegio", Long.toString(colegioId), null,
					"sin año escolar: lo configura el colegio" + (logo != null && logo.length > 0 ? "; con logo" : "; sin logo"),
					"Tu colegio y tu cuenta los creó el operador " + operador.nombreUsuario() + " de la plataforma. Tu primer "
							+ "paso es configurar el año escolar y las fechas de clases.");
		}));
	}

	private Colegio colegioDeLaPlataforma(long colegioId) {
		return transaccion.execute(t -> colegios.findById(colegioId).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado")));
	}

	/** Sprint 11, tanda 1c: el colegio en preparación o en producción. */
	private Colegio colegioQueOpera(long colegioId) {
		Colegio colegio = colegioDeLaPlataforma(colegioId);
		if (!colegio.getEstado().opera()) {
			throw new ReglaNegocioException("El colegio no está operando.");
		}
		return colegio;
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

	/** S8-M5 y S8-A1: lo que rechaza la corrección de la ficha (con el colegio en contexto y una transacción abierta). */
	private void validarFicha(Colegio colegio, FichaColegioRequest ficha, String ruc, String documento) {
		if (titulares.titularActivado()) {
			throw new ReglaNegocioException("El titular ya activó su cuenta: la ficha la corrige la Promotoría del colegio "
					+ "desde «Marca del colegio». El operador ya no la cambia.");
		}
		if (primeras.existsByIdNotNull() && !Objects.equals(documento, colegio.getTitularDocumento())) {
			throw new ReglaNegocioException("El DNI del titular no cambia después de crear su cuenta: es lo que confirma al "
					+ "activarla. Si es otra persona, el colegio la registra desde su propia cuenta, una vez activada.");
		}
		if (camposCambiados(colegio, ficha, ruc, documento).isEmpty()) {
			throw new ReglaNegocioException("No hay cambios que guardar: la ficha es la misma.");
		}
	}

	private static List<String> camposCambiados(Colegio c, FichaColegioRequest f, String ruc, String documento) {
		List<String> cambios = new ArrayList<>();
		anotar(cambios, "nombre", c.getNombre(), texto(f.nombre()));
		anotar(cambios, "razón social", c.getRazonSocial(), texto(f.razonSocial()));
		anotar(cambios, "RUC", c.getRuc(), ruc);
		anotar(cambios, "dirección", c.getDireccion(), texto(f.direccion()));
		anotar(cambios, "correo institucional", c.getCorreoInstitucional(), texto(f.correoInstitucional()));
		Set<Nivel> niveles = f.niveles() == null || f.niveles().isEmpty() ? Set.of() : EnumSet.copyOf(f.niveles());
		anotar(cambios, "niveles", c.getNiveles().toString(), niveles.toString());
		anotar(cambios, "titular", c.getTitularNombre(), texto(f.titularNombre()));
		anotar(cambios, "DNI del titular", c.getTitularDocumento(), documento);
		String contrato = f.contratoCodigo() == null || f.contratoCodigo().isBlank() ? null : f.contratoCodigo().strip();
		anotar(cambios, "contrato", c.getContratoCodigo(), contrato);
		return cambios;
	}

	private static void anotar(List<String> cambios, String campo, String antes, String despues) {
		if (!Objects.equals(antes, despues)) {
			cambios.add(campo);
		}
	}

	private static String texto(String valor) {
		return valor == null ? null : valor.strip();
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
