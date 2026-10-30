package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.CreadorMensajes;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.DescargaExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.EstadoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.ExportacionColegio;
import pe.edu.virgenmaria.cuentasclaras.exportacion.repository.DescargaExportacionRepository;
import pe.edu.virgenmaria.cuentasclaras.exportacion.repository.ExportacionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Sprint 11, tanda 2 (sección 3.9 y AP2-4): la entrega de la exportación al titular (la cuenta de la primera Promotoría
 * del colegio). El enlace va SOLO por correo; en la página escribe su DNI (el del contrato) y recibe un código SOLO por
 * WhatsApp, atado a esta exportación (10 minutos). Con los dos ve la clave UNA vez (se descifra y se borra:
 * {@code EXPORTACION_CLAVE_DESCIFRADA}) y descarga hasta 3 veces. A los 5 intentos fallidos de DNI o código se anula el
 * enlace (hay que pedir otra entrega) y cada intento fallido deja su evento. Cada descarga y cada descifrado avisan al
 * correo de control. Lo corre {@code sistema.exportacion}, con el colegio de la exportación en contexto.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_EXPORTACION')")
@Transactional(noRollbackFor = EntregaExportacion.IntentoFallidoException.class)
public class EntregaExportacion {

	public static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	/** Lo que ve el titular en la página de la entrega. */
	public record VistaEntrega(String colegio, boolean dniConfirmado, boolean codigoEnviado, int descargasRestantes,
			LocalDateTime venceEn, int intentosRestantes) {
	}

	private final ExportacionColegioRepository exportaciones;

	private final DescargaExportacionRepository descargas;

	private final ColegioRepository colegios;

	private final PrimeraPromotoriaRepository primeras;

	private final UsuarioRepository usuarios;

	private final CreadorMensajes creador;

	private final DerivadorSecreto derivador;

	private final SecretosConectores secretos;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public EntregaExportacion(ExportacionColegioRepository exportaciones, DescargaExportacionRepository descargas,
			ColegioRepository colegios, PrimeraPromotoriaRepository primeras, UsuarioRepository usuarios,
			CreadorMensajes creador, DerivadorSecreto derivador, SecretosConectores secretos, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.exportaciones = exportaciones;
		this.descargas = descargas;
		this.colegios = colegios;
		this.primeras = primeras;
		this.usuarios = usuarios;
		this.creador = creador;
		this.derivador = derivador;
		this.secretos = secretos;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/**
	 * Empieza la entrega de una exportación LISTA: el enlace, solo por correo al titular. Si el titular no tiene correo, no
	 * empieza (queda en la bitácora: dos operadores corrigen su contacto y se vuelve a pedir).
	 *
	 * @return si salió el enlace
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean iniciar(ExportacionColegio e) {
		Usuario titular = primeras.findFirstByIdNotNull().flatMap(p -> usuarios.findById(p.getUsuarioId())).orElse(null);
		if (titular == null) {
			auditoria.registrar(AccionAuditoria.EXPORTACION_ENTREGA_INICIADA, "exportacion_colegio", e.getId().toString(),
					null, "SIN TITULAR", "El colegio no tiene titular registrado: la exportación queda LISTA y se entrega "
							+ "cuando dos operadores registren al titular (o vence a los 30 días).");
			return false;
		}
		long numero = e.getIntentosFallidos() + 1L + (e.getTokenHash() == null ? 0 : 1);
		var mensaje = creador.enlaceExportacionPorCorreo(titular, new CreadorMensajes.Contenido(TipoMensaje.EXPORTACION_ENLACE,
				PlantillaMensaje.EXPORTACION_ENLACE, List.of(e.getVenceEn().format(DIA)), "exportacion_colegio", e.getId()),
				"EXPORTACION_ENLACE:" + e.getId() + ":" + numero + ":" + System.nanoTime());
		if (mensaje.isEmpty()) {
			auditoria.registrar(AccionAuditoria.EXPORTACION_ENTREGA_INICIADA, "exportacion_colegio", e.getId().toString(),
					null, "SIN CORREO", "El titular no tiene correo registrado: el enlace de la exportación va solo por "
							+ "correo. Dos operadores corrigen su contacto y se pide otra entrega.");
			return false;
		}
		String token = derivador.tokenExportacion(ContextoColegio.actual(), mensaje.get().getClave());
		e.iniciarEntrega(Sha256.hex(token), LocalDateTime.now(reloj).plusDays(ExportacionColegio.DIAS_VIGENCIA));
		exportaciones.saveAndFlush(e);
		auditoria.registrar(AccionAuditoria.EXPORTACION_ENTREGA_INICIADA, "exportacion_colegio", e.getId().toString(), null,
				"ENLACE", "El enlace de la exportación salió solo por correo al titular " + titular.getNombreUsuario()
						+ ". El código le llega por WhatsApp al confirmar su DNI.");
		return true;
	}

	/** Vuelve a mandar el enlace (un operador, a pedido del titular): el anterior deja de servir. */
	public boolean reenviar(Long exportacionId) {
		return iniciar(exportaciones.bloquear(exportacionId).orElseThrow(() -> new RecursoNoEncontradoException(
				"Exportación no encontrada")));
	}

	@Transactional(readOnly = true)
	public VistaEntrega ver(String token) {
		ExportacionColegio e = porToken(token);
		Colegio colegio = colegios.findById(ContextoColegio.actual()).orElseThrow();
		return new VistaEntrega(colegio.getNombre(), e.getDniConfirmadoEn() != null, e.getCodigoHash() != null,
				ExportacionColegio.MAX_DESCARGAS - e.getDescargas(), e.getVenceEn(),
				ExportacionColegio.MAX_INTENTOS - e.getIntentosFallidos());
	}

	/** El DNI del contrato: si coincide, sale el código por WhatsApp. */
	public void confirmarDni(String token, String dni) {
		ExportacionColegio e = porTokenBloqueada(token);
		Colegio colegio = colegios.findById(ContextoColegio.actual()).orElseThrow();
		String escrito = dni == null ? "" : dni.replaceAll("\\s", "");
		if (colegio.getTitularDocumento() == null || !iguales(escrito, colegio.getTitularDocumento())) {
			fallido(e, "el DNI no coincide con el del contrato");
		}
		LocalDateTime ahora = LocalDateTime.now(reloj);
		e.dniConfirmado(ahora);
		Usuario titular = titular();
		String huella = Sha256.hex("exportacion|" + e.getId());
		LocalDateTime vence = ahora.plus(VIGENCIA_CODIGO);
		String[] codigo = new String[1];
		creador.codigoPorWhatsapp(titular, new CreadorMensajes.Contenido(TipoMensaje.CODIGO_EXPORTACION,
				PlantillaMensaje.CODIGO_EXPORTACION, List.of("", vence.format(HORA)), "exportacion_colegio", e.getId()),
				"CODIGO_EXPORTACION:" + e.getId() + ":" + System.nanoTime(), clave -> {
					codigo[0] = derivador.codigoExportacion(ContextoColegio.actual(), clave);
					return Sha256.hex(codigo[0]);
				}, huella).orElseThrow(() -> new ReglaNegocioException("El titular no tiene un celular con WhatsApp "
						+ "registrado: el código llega solo por WhatsApp. Escribe al proveedor."));
		e.codigoEnviado(Sha256.hex(codigo[0]), vence);
		exportaciones.saveAndFlush(e);
		auditoria.registrar(AccionAuditoria.EXPORTACION_CODIGO_ENVIADO, "exportacion_colegio", e.getId().toString(), null,
				"WHATSAPP", "El titular confirmó su DNI; el código de la entrega salió solo por WhatsApp (10 minutos).");
	}

	/**
	 * El código: si coincide (y no venció), devuelve la clave del ZIP la PRIMERA vez (se descifra, se borra de la base y
	 * avisa al correo de control); después, {@code null} (la clave ya se vio).
	 */
	public String confirmarCodigo(String token, String codigo) {
		ExportacionColegio e = porTokenBloqueada(token);
		exigirCodigo(e, codigo);
		if (!e.claveDisponible()) {
			return null;
		}
		LocalDateTime ahora = LocalDateTime.now(reloj);
		Integer version = e.getClaveVersion();
		byte[] cifrada = e.tomarClave(ahora);
		String clave = secretos.descifrarClaveExportacion(cifrada, version, ContextoColegio.actual(), e.getId());
		exportaciones.saveAndFlush(e);
		auditoria.registrar(AccionAuditoria.EXPORTACION_CLAVE_DESCIFRADA, "exportacion_colegio", e.getId().toString(), null,
				"VISTA", "El titular vio la clave del ZIP (una vez): se descifró y se borró de la base.");
		avisar(e, "el titular vio la clave del archivo", "una sola vez");
		return ZipCifrado.agrupada(clave);
	}

	/** Una descarga (hasta 3): devuelve el archivo. La primera deja la exportación ENTREGADA. */
	public Path descargar(String token, String codigo, String ip, Path directorio) {
		ExportacionColegio e = porTokenBloqueada(token);
		exigirCodigo(e, codigo);
		if (e.getDescargas() >= ExportacionColegio.MAX_DESCARGAS) {
			throw new ReglaNegocioException("Ya se descargó " + ExportacionColegio.MAX_DESCARGAS + " veces: pide otra "
					+ "exportación al proveedor.");
		}
		Path archivo = directorio.resolve(Objects.requireNonNull(e.getArchivo(), "archivo"));
		if (!java.nio.file.Files.isRegularFile(archivo)) {
			throw new ReglaNegocioException("El archivo ya no está (venció a los 30 días): pide otra exportación.");
		}
		int numero = e.getDescargas() + 1;
		descargas.saveAndFlush(DescargaExportacion.de(e.getId(), numero, ip));
		e.descargada(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		exportaciones.saveAndFlush(e);
		auditoria.registrar(AccionAuditoria.EXPORTACION_DESCARGADA, "exportacion_colegio", e.getId().toString(), null,
				"descarga " + numero, "El titular descargó la exportación (descarga " + numero + " de "
						+ ExportacionColegio.MAX_DESCARGAS + ").");
		avisar(e, "se descargó el archivo", "descarga " + numero + " de " + ExportacionColegio.MAX_DESCARGAS);
		return archivo;
	}

	private void exigirCodigo(ExportacionColegio e, String codigo) {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		if (e.getCodigoHash() == null) {
			throw new ReglaNegocioException("Primero confirma tu DNI: el código te llega por WhatsApp.");
		}
		if (e.getCodigoVenceEn() == null || e.getCodigoVenceEn().isBefore(ahora)) {
			throw new ReglaNegocioException("El código venció (vale 10 minutos): vuelve a escribir tu DNI para recibir otro.");
		}
		String escrito = codigo == null ? "" : codigo.replaceAll("\\s", "");
		if (!iguales(Sha256.hex(escrito), e.getCodigoHash())) {
			fallido(e, "el código no coincide");
		}
	}

	/** AP2-4: deja el intento fallido (en su propia escritura) y, al quinto, anula el enlace. Siempre lanza. */
	private void fallido(ExportacionColegio e, String que) {
		boolean anulado = e.intentoFallido();
		exportaciones.saveAndFlush(e);
		auditoria.registrar(AccionAuditoria.EXPORTACION_INTENTO_FALLIDO, "exportacion_colegio", e.getId().toString(), null,
				"intento " + e.getIntentosFallidos(), "Intento fallido en la entrega de la exportación: " + que + "."
						+ (anulado ? " Era el quinto: el enlace quedó anulado." : ""));
		throw new IntentoFallidoException(anulado
				? "Llegaste a " + ExportacionColegio.MAX_INTENTOS + " intentos: el enlace quedó anulado. Pide otra entrega al "
						+ "proveedor."
				: "No coincide: " + que + ". Te quedan " + (ExportacionColegio.MAX_INTENTOS - e.getIntentosFallidos())
						+ " intentos.");
	}

	private void avisar(ExportacionColegio e, String que, String detalle) {
		eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.EXPORTACION_MOVIMIENTO,
				List.of(que, detalle), "exportacion_colegio", e.getId(), que.substring(0, 3) + e.getDescargas()
						+ (e.getClaveVistaEn() == null ? "" : "C"), titular().getId(), false, true, false));
	}

	private ExportacionColegio porToken(String token) {
		ExportacionColegio e = exportaciones.findByTokenHash(Sha256.hex(Objects.requireNonNullElse(token, "")))
				.orElseThrow(() -> new RecursoNoEncontradoException("Este enlace no existe o ya no sirve"));
		if (e.getEstado() != EstadoExportacion.LISTA && e.getEstado() != EstadoExportacion.ENTREGADA
				|| e.getTokenVenceEn() == null || e.getTokenVenceEn().isBefore(LocalDateTime.now(reloj))) {
			throw new RecursoNoEncontradoException("Este enlace ya no sirve");
		}
		return e;
	}

	private ExportacionColegio porTokenBloqueada(String token) {
		return exportaciones.bloquear(porToken(token).getId()).orElseThrow();
	}

	private Usuario titular() {
		return primeras.findFirstByIdNotNull().flatMap(p -> usuarios.findById(p.getUsuarioId()))
				.orElseThrow(() -> new ReglaNegocioException("El colegio no tiene titular"));
	}

	private static boolean iguales(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	/** Un intento fallido: se guarda (no revierte la transacción) y se muestra a quien escribe. */
	public static final class IntentoFallidoException extends ReglaNegocioException {

		public IntentoFallidoException(String mensaje) {
			super(mensaje);
		}
	}
}
