package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf.DocumentoTextoPdf;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.RegeneracionLibreta;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.ExportacionColegio;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.MotivoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.repository.ExportacionColegioRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sprint 11, tanda 2 (sección 3.9; AP-12 y AP2-13): genera la exportación completa de los datos de UN colegio, en flujo:
 * un CSV por tabla de {@code exportacion/tablas.txt} (cada consulta filtra por su colegio de forma explícita), las
 * libretas emitidas regeneradas y comparadas con su huella (si una no coincide, la exportación queda FALLIDA y avisa:
 * no se entrega algo distinto de lo que vio la familia), los PDF de los comunicados, {@code MANIFIESTO.csv} con la SHA-256
 * de cada archivo y {@code LEEME.pdf}. El ZIP va cifrado con AES-256 y su clave se guarda cifrada con
 * {@code CC_CLAVE_CONECTORES} hasta que el titular la ve. Lo corre {@code sistema.exportacion} ({@code cc_sistema}).
 */
@Service
@PreAuthorize("hasRole('SISTEMA_EXPORTACION')")
public class Exportador {

	private static final Logger LOG = LoggerFactory.getLogger(Exportador.class);

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final ExportacionColegioRepository exportaciones;

	private final ColegioRepository colegios;

	private final LectorTablas lector;

	private final LibretaEmitidaRepository libretas;

	private final RegeneracionLibreta regeneracion;

	private final DocumentoTextoPdf documentos;

	private final SecretosConectores secretos;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final EntregaExportacion entrega;

	private final Clock reloj;

	private final TransactionTemplate transaccion;

	private final Path directorio;

	public Exportador(ExportacionColegioRepository exportaciones, ColegioRepository colegios, LectorTablas lector,
			LibretaEmitidaRepository libretas, RegeneracionLibreta regeneracion, DocumentoTextoPdf documentos,
			SecretosConectores secretos, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			EntregaExportacion entrega, Clock reloj, PlatformTransactionManager transacciones,
			@Value("${cuentasclaras.exportacion.directorio:${java.io.tmpdir}/cc-exportaciones}") String directorio) {
		this.exportaciones = exportaciones;
		this.colegios = colegios;
		this.lector = lector;
		this.libretas = libretas;
		this.regeneracion = regeneracion;
		this.documentos = documentos;
		this.secretos = secretos;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.entrega = entrega;
		this.reloj = reloj;
		this.transaccion = new TransactionTemplate(transacciones);
		this.directorio = Path.of(directorio);
	}

	/** Donde viven los ZIP (fuera de los respaldos, {@code CC_DIR_EXPORTACIONES}). */
	public Path directorio() {
		return directorio;
	}

	/** Crea la exportación del colegio en contexto (GENERANDO) y la genera. Devuelve su id. */
	public Long exportar(MotivoExportacion motivo, Long cambioEstadoId) {
		Long plataforma = transaccion.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA).map(Colegio::getId)
				.orElseThrow(() -> new IllegalStateException("Falta el colegio de plataforma")));
		Long id = transaccion.execute(t -> exportaciones.saveAndFlush(ExportacionColegio.nueva(plataforma, motivo,
				cambioEstadoId)).getId());
		generar(id);
		return id;
	}

	/** Genera el ZIP de una exportación GENERANDO del colegio en contexto. */
	public void generar(Long exportacionId) {
		long colegioId = ContextoColegio.actual();
		Colegio colegio = transaccion.execute(t -> colegios.findById(colegioId).orElseThrow());
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		String nombre = (colegio.getRuc() == null ? "colegio-" + colegioId : colegio.getRuc()) + "-" + ahora.format(DIA)
				+ "-exportacion-" + exportacionId + ".zip";
		Path archivo = directorio.resolve(nombre);
		String clave = ZipCifrado.nuevaClave();
		AtomicReference<String> falla = new AtomicReference<>();
		long filas = 0;
		List<ZipCifrado.Entrada> entradas;
		Map<String, Long> conteos = new LinkedHashMap<>();
		try {
			Files.createDirectories(directorio);
			try (ZipCifrado zip = new ZipCifrado(archivo, clave)) {
				for (TablasExportacion.Tabla tabla : TablasExportacion.cargar()) {
					ZipCifrado.Entrada e = tabla(zip, tabla, colegioId);
					conteos.put(tabla.nombre(), e.filas());
					filas += e.filas();
				}
				falla.set(libretas(zip));
				adjuntos(zip, colegioId);
				entradas = zip.entradas();
				byte[] manifiesto = manifiesto(entradas);
				String shaManifiesto = sha256(manifiesto);
				zip.agregar("MANIFIESTO.csv", manifiesto);
				zip.agregar("LEEME.pdf", leeme(colegio, exportacionId, ahora, conteos, shaManifiesto));
				if (falla.get() == null) {
					cerrarLista(exportacionId, colegio, zip, archivo, nombre, entradas.size() + 2, filas, shaManifiesto, clave,
							ahora);
					return;
				}
			}
		}
		catch (IOException e) {
			falla.set("no se pudo escribir el archivo (" + e.getClass().getSimpleName() + ")");
		}
		catch (ZipCifrado.ExportacionDemasiadoGrandeException e) {
			falla.set("supera el tope de 2 GB: hay que partirla");
		}
		catch (RuntimeException e) {
			LOG.error("La exportación {} del colegio {} falló", exportacionId, colegioId, e);
			falla.set("error al generarla (" + e.getClass().getSimpleName() + ")");
		}
		fallar(exportacionId, colegio, archivo, falla.get());
	}

	private ZipCifrado.Entrada tabla(ZipCifrado zip, TablasExportacion.Tabla tabla, long colegioId) {
		List<String> columnas = lector.columnas(tabla);
		long[] filas = { 0 };
		ZipCifrado.Entrada e = zip.agregar("datos/" + tabla.nombre() + ".csv", 0, salida -> {
			try (EscritorCsv csv = new EscritorCsv(salida)) {
				csv.encabezado(columnas);
				lector.recorrer(tabla, columnas, colegioId, csv::fila);
				filas[0] = csv.filas();
			}
		});
		return new ZipCifrado.Entrada(e.archivo(), filas[0], e.bytes(), e.sha256());
	}

	/** Las libretas regeneradas y comparadas con su huella: devuelve el motivo de la falla, o {@code null}. */
	private String libretas(ZipCifrado zip) {
		List<String> fallas = new ArrayList<>();
		transaccion.executeWithoutResult(t -> {
			List<LibretaEmitida> todas = libretas.findByCreadoEnGreaterThanEqualOrderByIdAsc(LocalDateTime.of(2000, 1, 1, 0, 0));
			Map<String, List<LibretaEmitida>> porSeccion = new LinkedHashMap<>();
			todas.forEach(l -> porSeccion.computeIfAbsent(l.getPeriodoId() + "/" + l.getSeccionId(), k -> new ArrayList<>())
					.add(l));
			for (List<LibretaEmitida> grupo : porSeccion.values()) {
				for (RegeneracionLibreta.Regenerada r : regeneracion.regenerar(grupo)) {
					LibretaEmitida l = r.libreta();
					if (!r.coincide()) {
						fallas.add(l.codigoVerificacion());
						continue;
					}
					zip.agregar("libretas/" + l.getAnioEscolarId() + "/" + l.getPeriodoId() + "/" + l.getSeccionId() + "/"
							+ l.codigoVerificacion() + ".pdf", regeneracion.pdf(List.of(r)));
				}
			}
		});
		return fallas.isEmpty() ? null : (fallas.size() == 1 ? "la libreta " + fallas.getFirst() : fallas.size()
				+ " libretas") + " no coincide" + (fallas.size() == 1 ? "" : "n") + " con su huella";
	}

	private void adjuntos(ZipCifrado zip, long colegioId) {
		transaccion.executeWithoutResult(t -> lector.adjuntos(colegioId, (id, contenido) -> {
			if (contenido != null) {
				zip.agregar("comunicados/" + id + ".pdf", contenido);
			}
		}));
	}

	static byte[] manifiesto(List<ZipCifrado.Entrada> entradas) {
		java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
		try (EscritorCsv csv = new EscritorCsv(salida)) {
			csv.encabezado(List.of("archivo", "filas", "bytes", "sha256"));
			for (ZipCifrado.Entrada e : entradas) {
				csv.fila(List.of(e.archivo(), e.filas(), e.bytes(), e.sha256()));
			}
		}
		return salida.toByteArray();
	}

	private byte[] leeme(Colegio colegio, Long id, LocalDateTime ahora, Map<String, Long> conteos, String shaManifiesto) {
		List<String> p = new ArrayList<>();
		p.add("Colegio: " + colegio.getNombre() + (colegio.getRuc() == null ? "" : " · RUC " + colegio.getRuc()));
		p.add("Exportación " + id + " del " + ahora.format(FECHA) + " (hora de Lima).");
		p.add("");
		p.add("Contenido: un archivo CSV por tabla en la carpeta datos (UTF-8, separado por comas; se abre con Excel), las "
				+ "libretas emitidas en PDF (regeneradas y comparadas con su huella) y los PDF de los comunicados.");
		p.add("Cómo comprobar el archivo: la huella SHA-256 de MANIFIESTO.csv es " + shaManifiesto + ". MANIFIESTO.csv "
				+ "lista la huella de cada archivo. El correo externo de control del colegio recibió las huellas del ZIP y del "
				+ "manifiesto el día que se generó: si coinciden, el archivo no cambió.");
		p.add("");
		p.add("Filas por tabla:");
		conteos.forEach((t, n) -> {
			if (n > 0) {
				p.add("  " + t + ": " + n);
			}
		});
		p.add("");
		p.add("Plazos: con la autorización expresa del titular, los datos se conservan hasta 12 meses después de la "
				+ "suspensión; sin ella, 30 días después del cierre. Lo tributario (5 años, Código Tributario art. 87 num. 7) "
				+ "lo conserva el colegio con esta exportación. El XML y la constancia (CDR) de cada comprobante los guardan "
				+ "el OSE y SUNAT.");
		return documentos.generar("Exportación de los datos del colegio", p);
	}

	private void cerrarLista(Long id, Colegio colegio, ZipCifrado zip, Path archivo, String nombre, int archivos, long filas,
			String shaManifiesto, String clave, LocalDateTime ahora) throws IOException {
		zip.close();
		String shaZip = sha256(archivo);
		long bytes = Files.size(archivo);
		transaccion.executeWithoutResult(t -> {
			ExportacionColegio e = exportaciones.bloquear(id).orElseThrow();
			e.lista(nombre, archivos, filas, bytes, shaZip, shaManifiesto, secretos.cifrarClaveExportacion(clave,
					colegio.getId(), id), secretos.version(), ahora);
			exportaciones.saveAndFlush(e);
			auditoria.registrar(AccionAuditoria.EXPORTACION_COLEGIO_GENERADA, "exportacion_colegio", id.toString(), null,
					"ZIP " + shaZip, "Exportación " + e.getMotivo().name().toLowerCase(java.util.Locale.ROOT) + " de "
							+ colegio.getNombre() + ": " + archivos + " archivos, " + filas + " filas, " + bytes + " bytes. "
							+ "SHA-256 del ZIP " + shaZip + " y del manifiesto " + shaManifiesto + ".");
			eventos.publishEvent(new AvisoPlataforma(colegio.getId(), AvisoPlataforma.Tipo.EXPORTACION_LISTA,
					List.of(motivo(e.getMotivo()), shaZip, shaManifiesto), "exportacion_colegio", id, null,
					null, false, true, true));
			entrega.iniciar(e);
		});
	}

	private void fallar(Long id, Colegio colegio, Path archivo, String motivo) {
		try {
			Files.deleteIfExists(archivo);
		}
		catch (IOException e) {
			LOG.warn("No se pudo borrar el archivo de la exportación fallida {}", id);
		}
		transaccion.executeWithoutResult(t -> {
			ExportacionColegio e = exportaciones.bloquear(id).orElseThrow();
			e.fallida(motivo);
			exportaciones.saveAndFlush(e);
			auditoria.registrar(AccionAuditoria.EXPORTACION_FALLIDA, "exportacion_colegio", id.toString(), null, "FALLIDA",
					"La exportación de " + colegio.getNombre() + " no se entrega: " + motivo + ". Alerta CRÍTICA a la "
							+ "Promotoría, al correo de control y a los operadores.");
			eventos.publishEvent(new AvisoPlataforma(colegio.getId(), AvisoPlataforma.Tipo.EXPORTACION_FALLIDA,
					List.of(motivo), "exportacion_colegio", id, null, null, false, true, true));
		});
		eventos.publishEvent(new ExportacionFallida(colegio.getId(), id, motivo));
	}

	static String motivo(MotivoExportacion motivo) {
		return switch (motivo) {
			case SUSPENSION -> "por la suspensión";
			case CIERRE -> "antes del cierre";
			case PEDIDO -> "a pedido del colegio";
		};
	}

	static String sha256(byte[] contenido) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static String sha256(Path archivo) throws IOException {
		try (var entrada = Files.newInputStream(archivo)) {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[64 * 1024];
			int n;
			while ((n = entrada.read(buffer)) > 0) {
				sha.update(buffer, 0, n);
			}
			return HexFormat.of().formatHex(sha.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** AP2-13: para la alerta CRÍTICA a los operadores (en su colegio, después de confirmar). */
	public record ExportacionFallida(Long colegioId, Long exportacionId, String motivo) {
	}

	/** Una exportación del colegio en contexto que no se pudo crear (la regla de negocio, para la pantalla). */
	public static ReglaNegocioException sinExportacion(String que) {
		return new ReglaNegocioException(que);
	}
}
