package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comunicados.analizador.AnalizadorPdf;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.AdjuntoValidado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.ComunicadoAdjunto;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoAdjuntoRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sprint 9, tanda 3 (sección 3.9, decisión 157, A9, A10, B3, B5 y B12): recibe el PDF de un comunicado y lo hace revisar
 * por el analizador en un PROCESO HIJO ({@link LanzadorAnalizador}); esta clase nunca abre un PDF.
 * <ul>
 *   <li>Antes: hasta 2 MB, extensión {@code .pdf}; en la demo, solo el PDF de ejemplo (por su SHA-256, E48).</li>
 *   <li>B12: más de 10 PDF por persona o 30 por colegio en la última hora (contados en la bitácora, que sobrevive a un
 *       reinicio): se rechaza sin lanzar el hijo.</li>
 *   <li>A10: UN hijo a la vez (un hilo, cola de 4; llena, «intente en unos minutos»), y como máximo 1 tarea por persona y
 *       2 por colegio en cola o en curso: un colegio no deja sin servicio a los demás.</li>
 *   <li>Cada intento queda en la bitácora ({@code ADJUNTO_REVISADO}: resultado y SHA-256, sin texto), aunque se rechace.</li>
 *   <li>Después: el padre recalcula los dos SHA-256, pasa el texto del PDF por el {@link FiltroContenido} y decide si
 *       requiere revisión (B3: menos de 50 caracteres por página o imágenes).</li>
 * </ul>
 */
@Service
public class AdjuntosComunicado {

	private static final Logger LOG = LoggerFactory.getLogger(AdjuntosComunicado.class);

	public static final int MAX_POR_PERSONA_HORA = 10;

	public static final int MAX_POR_COLEGIO_HORA = 30;

	static final int MAX_POR_PERSONA_EN_CURSO = 1;

	static final int MAX_POR_COLEGIO_EN_CURSO = 2;

	static final String COLA_LLENA = "Estamos revisando otros archivos; intente en unos minutos.";

	private final LanzadorAnalizador lanzador;

	private final FiltroContenido filtro;

	private final AuditoriaService auditoria;

	private final ComunicadoAdjuntoRepository adjuntos;

	private final PdfEjemplo ejemplo;

	private final Clock reloj;

	private final ThreadPoolExecutor ejecutor;

	private final Map<String, AtomicInteger> enCurso = new ConcurrentHashMap<>();

	public AdjuntosComunicado(LanzadorAnalizador lanzador, FiltroContenido filtro, AuditoriaService auditoria,
			ComunicadoAdjuntoRepository adjuntos, PdfEjemplo ejemplo, Clock reloj) {
		this.lanzador = lanzador;
		this.filtro = filtro;
		this.auditoria = auditoria;
		this.adjuntos = adjuntos;
		this.ejemplo = ejemplo;
		this.reloj = reloj;
		this.ejecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4), r -> {
			Thread hilo = new Thread(r, "analizador-pdf");
			hilo.setDaemon(true);
			return hilo;
		}, new ThreadPoolExecutor.AbortPolicy());
	}

	@PreDestroy
	void cerrar() {
		ejecutor.shutdownNow();
	}

	/**
	 * Revisa un PDF subido para un comunicado. Devuelve el saneado listo para guardar, o lanza
	 * {@link ReglaNegocioException} con el motivo claro («tiene JavaScript», «pesa más de 2 MB»…).
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','DOCENTE')")
	public AdjuntoValidado validar(String nombreArchivo, byte[] bytes) {
		UsuarioAutenticado persona = SesionDelEmisor.persona();
		if (bytes == null || bytes.length == 0) {
			throw new ReglaNegocioException("El archivo está vacío.");
		}
		if (bytes.length > AnalizadorPdf.MAX_BYTES) {
			throw new ReglaNegocioException("El PDF pesa más de 2 MB: expórtelo de nuevo con menos imágenes.");
		}
		if (nombreArchivo == null || !nombreArchivo.toLowerCase(Locale.ROOT).strip().endsWith(".pdf")) {
			throw new ReglaNegocioException("Adjunte un archivo PDF (termina en .pdf).");
		}
		String shaOriginal = HuellaComunicado.sha256(bytes);
		if (ejemplo.soloElDeEjemplo() && !ejemplo.esElDeEjemplo(shaOriginal)) {
			throw new ReglaNegocioException("En la demo solo se puede adjuntar el PDF de ejemplo (datos ficticios): "
					+ "descárgalo desde el recorrido guiado.");
		}
		LocalDateTime haceUnaHora = LocalDateTime.now(reloj).minusHours(1);
		if (auditoria.contarDesdeDelUsuarioActual(AccionAuditoria.ADJUNTO_REVISADO, haceUnaHora) >= MAX_POR_PERSONA_HORA) {
			throw new ReglaNegocioException("Subiste " + MAX_POR_PERSONA_HORA + " PDF en la última hora: espera un poco "
					+ "antes de subir otro.");
		}
		if (auditoria.contarDesde(AccionAuditoria.ADJUNTO_REVISADO, haceUnaHora) >= MAX_POR_COLEGIO_HORA) {
			throw new ReglaNegocioException("El colegio subió " + MAX_POR_COLEGIO_HORA + " PDF en la última hora: "
					+ "inténtalo más tarde.");
		}
		String clavePersona = "u:" + persona.colegioId() + ":" + persona.usuarioId();
		String claveColegio = "c:" + persona.colegioId();
		if (!reservar(clavePersona, MAX_POR_PERSONA_EN_CURSO)) {
			throw new ReglaNegocioException("Ya estamos revisando un PDF tuyo: espera a que termine.");
		}
		if (!reservar(claveColegio, MAX_POR_COLEGIO_EN_CURSO)) {
			liberar(clavePersona);
			throw new ReglaNegocioException(COLA_LLENA);
		}
		LanzadorAnalizador.Salida salida;
		try {
			salida = analizar(bytes);
		}
		finally {
			liberar(clavePersona);
			liberar(claveColegio);
		}
		if (salida == null) {
			registrar(shaOriginal, "COLA_LLENA", null);
			throw new ReglaNegocioException(COLA_LLENA);
		}
		if (!salida.aceptado()) {
			registrar(shaOriginal, "RECHAZADO", salida.motivo());
			throw new ReglaNegocioException("El PDF no se aceptó: " + salida.motivo() + ". Expórtelo de nuevo «como PDF» "
					+ "desde Word o su programa, sin formularios, adjuntos ni enlaces a otros sitios.");
		}
		FiltroContenido.Resultado texto = filtro.revisar(salida.texto());
		if (!texto.aceptado()) {
			registrar(shaOriginal, "RECHAZADO_POR_CONTENIDO", texto.regla().name());
			throw new ReglaNegocioException("El texto del PDF " + texto.motivo() + (texto.fragmento() == null ? ""
					: " («" + texto.fragmento() + "»)") + ". Los comunicados no tratan cobranzas ni piden datos o pagos.");
		}
		byte[] saneado = salida.saneado();
		String sha = HuellaComunicado.sha256(saneado);
		int paginas = Integer.parseInt(salida.resultado().getProperty("paginas", "0"));
		boolean revision = requiereRevision(salida);
		registrar(shaOriginal, "ACEPTADO", revision ? "requiere revisión" : null);
		return new AdjuntoValidado(saneado, paginas, sha, shaOriginal, revision);
	}

	/** Guarda el saneado (en la transacción del envío). Vuelve a comprobar su huella: lo que se guarda es lo revisado. */
	@Transactional(propagation = Propagation.MANDATORY)
	public ComunicadoAdjunto guardar(AdjuntoValidado adjunto) {
		if (!HuellaComunicado.sha256(adjunto.saneado()).equals(adjunto.sha256()) || adjunto.paginas() < 1
				|| adjunto.paginas() > AnalizadorPdf.MAX_PAGINAS || adjunto.bytes() > AnalizadorPdf.MAX_BYTES) {
			throw new ReglaNegocioException("El PDF cambió desde la vista previa: vuelve a adjuntarlo.");
		}
		return adjuntos.saveAndFlush(ComunicadoAdjunto.nuevo(adjunto.saneado(), adjunto.paginas(), adjunto.sha256(),
				adjunto.sha256Original(), adjunto.requiereRevision()));
	}

	/** B3: poco texto en alguna página o imágenes (lo dice el hijo; el padre lo vuelve a calcular con sus números). */
	static boolean requiereRevision(LanzadorAnalizador.Salida salida) {
		String caracteres = salida.resultado().getProperty("caracteres", "");
		boolean pocoTexto = caracteres.isBlank() || Arrays.stream(caracteres.split(",")).map(String::strip)
				.anyMatch(c -> !c.matches("[0-9]+") || Integer.parseInt(c) < AnalizadorPdf.MIN_CARACTERES_POR_PAGINA);
		return pocoTexto || Boolean.parseBoolean(salida.resultado().getProperty("imagenes", "true"))
				|| Boolean.parseBoolean(salida.resultado().getProperty("requiereRevision", "true"));
	}

	/** En el hilo del analizador; {@code null} si la cola está llena o se esperó demasiado. */
	private LanzadorAnalizador.Salida analizar(byte[] bytes) {
		Future<LanzadorAnalizador.Salida> futuro;
		try {
			futuro = ejecutor.submit(() -> lanzador.analizar(bytes, filtro.dominiosPermitidos()));
		}
		catch (RejectedExecutionException e) {
			return null;
		}
		try {
			// Lo que tarde en la cola (como máximo 4 delante, de hasta 10 s cada una) más el propio análisis.
			return futuro.get(70, TimeUnit.SECONDS);
		}
		catch (TimeoutException e) {
			futuro.cancel(true);
			return null;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			futuro.cancel(true);
			return null;
		}
		catch (ExecutionException e) {
			LOG.warn("El analizador de PDF no se pudo lanzar: {}", e.getCause() == null ? e.getClass().getSimpleName()
					: e.getCause().getClass().getSimpleName());
			return new LanzadorAnalizador.Salida(AnalizadorPdf.NO_REVISADO, new java.util.Properties(), null, "", false);
		}
	}

	private boolean reservar(String clave, int maximo) {
		AtomicInteger contador = enCurso.computeIfAbsent(clave, k -> new AtomicInteger());
		while (true) {
			int actual = contador.get();
			if (actual >= maximo) {
				return false;
			}
			if (contador.compareAndSet(actual, actual + 1)) {
				return true;
			}
		}
	}

	private void liberar(String clave) {
		Optional.ofNullable(enCurso.get(clave)).ifPresent(c -> c.updateAndGet(v -> Math.max(0, v - 1)));
	}

	/** El evento de cada intento, en su propia transacción (queda aunque el envío no siga). Sin texto del PDF. */
	private void registrar(String sha, String resultado, String detalle) {
		auditoria.registrar(AccionAuditoria.ADJUNTO_REVISADO, "comunicado_adjunto", null, null, resultado,
				"PDF " + sha + ": " + resultado.toLowerCase(Locale.ROOT).replace('_', ' ')
						+ (detalle == null ? "" : " (" + detalle + ")") + ".");
	}
}
