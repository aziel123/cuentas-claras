package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.Consentimientos;
import pe.edu.virgenmaria.cuentasclaras.academico.api.InasistenciasDelDia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 2 (sección 3.6, decisión 151; A1, A2, E37, E39, E41, E52): los avisos de asistencia a las familias, que
 * crea SOLO {@code sistema.asistencia} (trg_mensaje_nace) en la transacción de su pasada. Lee lo académico solo por
 * {@code academico.api} (no puede saber quién debe: el aviso de una familia con deuda es idéntico al de una al día).
 * <ul>
 *   <li><b>Primer envío</b> (la hora del aviso del colegio, 10:30 por defecto): UN mensaje por familia con todos sus hijos
 *       que faltaron, llegaron tarde o tienen la falta justificada (A1), y la corrección de lo que cambió en días
 *       anteriores después del último envío (A2: lo hecho después de las 16:00 sale a las 10:30 del día siguiente).</li>
 *   <li><b>Segundo envío</b> (16:00 por defecto): lo que cambió después de la marca de agua: faltas registradas después
 *       del primer envío y correcciones ({@code cc_asistencia_corregida_v2}).</li>
 *   <li>Solo días hábiles, entre 07:00 y 20:00; hasta {@value #MAX_APODERADOS_POR_DEFECTO} apoderados por familia; por
 *       WhatsApp con el «sí» del apoderado, si no por correo salvo oposición. Clave idempotente por familia, día y envío.</li>
 *   <li>Solo el primer nombre del alumno, estados fijos y horas. Si un nombre coincidiera con una palabra de cobranza
 *       (la regla de trg_mensaje_nace), sale «su hijo(a)» y queda en el log técnico.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('SISTEMA_ASISTENCIA')")
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosAsistenciaFamilias {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosAsistenciaFamilias.class);

	static final int MAX_APODERADOS_POR_DEFECTO = 2;

	static final LocalTime DESDE = LocalTime.of(7, 0);

	static final LocalTime HASTA = LocalTime.of(20, 0);

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy",
			Locale.forLanguageTag("es-PE"));

	private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	/** Qué envío es. */
	public enum Envio {
		PRIMERO,
		SEGUNDO
	}

	/** Lo que hizo una pasada: qué envío (o ninguno) y cuántos mensajes creó. */
	public record Resultado(Envio envio, int mensajes) {

		static final Resultado NADA = new Resultado(null, 0);
	}

	private final InasistenciasDelDia inasistencias;

	private final Consentimientos consentimientos;

	private final ApoderadoRepository apoderados;

	private final CreadorMensajes creador;

	private final CalendarioHabil calendario;

	private final AuditoriaService auditoria;

	private final int maxApoderados;

	public AvisosAsistenciaFamilias(InasistenciasDelDia inasistencias, Consentimientos consentimientos,
			ApoderadoRepository apoderados, CreadorMensajes creador, CalendarioHabil calendario, AuditoriaService auditoria,
			@Value("${cuentasclaras.academico.max-apoderados-aviso:2}") int maxApoderados) {
		this.inasistencias = inasistencias;
		this.consentimientos = consentimientos;
		this.apoderados = apoderados;
		this.creador = creador;
		this.calendario = calendario;
		this.auditoria = auditoria;
		this.maxApoderados = Math.min(Math.max(maxApoderados, 1), MAX_APODERADOS_POR_DEFECTO);
	}

	/** Una pasada (cada 5 minutos): si le toca al colegio el primer o el segundo envío, lo prepara y sube la marca de agua. */
	public Resultado pasada(LocalDateTime ahora) {
		LocalDate hoy = ahora.toLocalDate();
		if (!calendario.esHabil(hoy) || ahora.toLocalTime().isBefore(DESDE) || !ahora.toLocalTime().isBefore(HASTA)) {
			return Resultado.NADA;
		}
		InasistenciasDelDia.Horas horas = inasistencias.horas();
		InasistenciasDelDia.Corte corte = inasistencias.corte();
		LocalDateTime primero = hoy.atTime(horas.aviso());
		LocalDateTime segundo = hoy.atTime(horas.segundoAviso());
		Envio envio;
		if (!ahora.isBefore(primero) && (corte.ultimoEnvio() == null || corte.ultimoEnvio().isBefore(primero))) {
			envio = Envio.PRIMERO;
		}
		else if (!ahora.isBefore(segundo) && (corte.ultimoEnvio() == null || corte.ultimoEnvio().isBefore(segundo))) {
			envio = Envio.SEGUNDO;
		}
		else {
			return Resultado.NADA;
		}
		// La marca de agua se lee ANTES que las marcas: lo que llegue mientras tanto sale en el envío siguiente.
		long hasta = inasistencias.ultimaMarcaId();
		Map<Long, List<String>> delDia = new LinkedHashMap<>();
		List<InasistenciasDelDia.Cambio> corregidas = new ArrayList<>();
		if (envio == Envio.PRIMERO) {
			// QA-S9-8: el estado de cada alumno en la marca de agua (no el vigente): una corrección que llegó mientras se
			// preparaba este envío sale en el siguiente como corrección de lo que la familia sí recibió.
			for (InasistenciasDelDia.Inasistencia i : inasistencias.delDiaHasta(hoy, hasta)) {
				delDia.computeIfAbsent(i.familiaId(), f -> new ArrayList<>()).add(linea(i.primerNombre(), i.estado(),
						i.horaLlegada()));
			}
		}
		for (InasistenciasDelDia.Cambio c : inasistencias.despuesDe(corte.ultimaMarcaId())) {
			if (c.marcaId() > hasta || (c.registroNuevo() && !c.ahoraSeAvisa())) {
				continue;
			}
			boolean deHoy = c.fecha().equals(hoy);
			if (deHoy && envio == Envio.PRIMERO) {
				continue;
			}
			// QA-S9-3: el primer envío de un colegio (sin marca de agua: recién pasó a producción o recién se activaron los
			// avisos) no manda como «corrección» toda la asistencia anterior registrada en la preparación: solo la de hoy.
			if (!deHoy && corte.ultimoEnvio() == null) {
				continue;
			}
			if (deHoy && c.registroNuevo()) {
				delDia.computeIfAbsent(c.familiaId(), f -> new ArrayList<>()).add(linea(c.primerNombre(), c.ahora(),
						c.horaLlegada()));
			}
			else {
				corregidas.add(c);
			}
		}
		Set<Long> familias = new java.util.LinkedHashSet<>(delDia.keySet());
		corregidas.forEach(c -> familias.add(c.familiaId()));
		Map<Long, List<Apoderado>> porFamilia = familias.isEmpty() ? Map.of()
				: apoderados.findByFamiliaIdInAndActivoTrueOrderByIdAsc(familias).stream()
						.collect(Collectors.groupingBy(a -> a.getFamilia().getId(), LinkedHashMap::new,
								Collectors.toList()));
		int creados = 0;
		for (Map.Entry<Long, List<String>> familia : delDia.entrySet()) {
			List<String> parametros = List.of(hoy.format(FECHA), String.join(". ", familia.getValue()));
			creados += enviar(porFamilia.getOrDefault(familia.getKey(), List.of()), new CreadorMensajes.Contenido(
					TipoMensaje.ASISTENCIA_DIA, PlantillaMensaje.ASISTENCIA, sinCobranza(parametros, familia.getValue()),
					"familia", familia.getKey()), hoy + ":" + envio.name());
		}
		for (InasistenciasDelDia.Cambio c : corregidas) {
			List<String> parametros = List.of(c.primerNombre(), c.fecha().format(FECHA_CORTA), c.ahora(), c.antes(),
					c.registradoPor());
			creados += enviar(porFamilia.getOrDefault(c.familiaId(), List.of()), new CreadorMensajes.Contenido(
					TipoMensaje.ASISTENCIA_CORREGIDA, PlantillaMensaje.ASISTENCIA_CORREGIDA,
					sinCobranza(parametros, List.of(c.primerNombre())), "marca_asistencia", c.marcaId()), envio.name());
		}
		inasistencias.marcarAvisado(hasta, ahora);
		if (creados > 0) {
			auditoria.registrar(AccionAuditoria.AVISOS_ASISTENCIA_ENVIADOS, "aviso_asistencia_corte", null, null,
					creados + " mensaje(s)", "Avisos de asistencia del " + hoy + " (" + (envio == Envio.PRIMERO
							? "primer envío" : "segundo envío") + "): " + delDia.size() + " familia(s) con el aviso del día y "
							+ corregidas.size() + " corrección(es). Marca de agua: " + hasta + ".");
		}
		return new Resultado(envio, creados);
	}

	/** Hasta 2 apoderados de la familia que reciban por algún canal (WhatsApp con el «sí»; si no, correo salvo oposición). */
	private int enviar(List<Apoderado> deLaFamilia, CreadorMensajes.Contenido contenido, String sufijo) {
		int destinatarios = 0;
		int creados = 0;
		for (Apoderado a : deLaFamilia) {
			if (destinatarios >= maxApoderados) {
				break;
			}
			boolean porWhatsapp = consentimientos.recibeAsistencia(a.getId(), true);
			boolean porCorreo = consentimientos.recibeAsistencia(a.getId(), false);
			if (creador.paraApoderadoConConsentimiento(a, contenido, sufijo, porWhatsapp, porCorreo).isPresent()) {
				destinatarios++;
				creados++;
			}
		}
		return creados;
	}

	/** «Mateo: falta», «Lucía: llegó tarde (08:12)». */
	private static String linea(String nombre, String estado, LocalTime hora) {
		return nombre + ": " + estado + (hora == null ? "" : " (" + hora.format(HORA) + ")");
	}

	/**
	 * Si los parámetros propios (SIN el nombre del colegio, que la regla de trg_mensaje_nace ya no revisa: B6) coincidieran
	 * con la regla de cobranza, los nombres de los alumnos salen como «su hijo(a)» y el de quien registró, como «su
	 * docente» (sección 15).
	 */
	private List<String> sinCobranza(List<String> parametros, List<String> conNombres) {
		String texto = String.join("\n", parametros);
		if (!ContenidoSinCobranza.hablaDeCobranza(texto)) {
			return parametros;
		}
		LOG.warn("Un aviso de asistencia coincidió con la regla de cobranza: sale con «su hijo(a)» en lugar del nombre.");
		Function<String, String> sinNombre = p -> p.replaceAll("^[^:]+:", "Su hijo(a):")
				.replaceAll("\\. [^:.]+:", ". Su hijo(a):");
		List<String> limpios = new ArrayList<>();
		for (String p : parametros) {
			String limpio = conNombres.contains(p) ? "su hijo(a)" : p.contains(":") ? sinNombre.apply(p) : p;
			limpios.add(ContenidoSinCobranza.hablaDeCobranza(limpio) ? "su docente" : limpio);
		}
		return limpios;
	}
}
