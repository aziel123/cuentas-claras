package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.api.Consentimientos;
import pe.edu.virgenmaria.cuentasclaras.academico.api.HorarioComunicados;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.api.ComunicadosPorAvisar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 3 (sección 3.10, decisiones 158 a 160; E42, E44; B6): los avisos de comunicados a las familias, que crea
 * SOLO {@code sistema.comunicados} (trg_mensaje_nace). Lee los comunicados solo por {@code comunicados.api} (sin su texto).
 * <ul>
 *   <li><b>Urgentes</b> (los envía Dirección o Promotoría): apenas salen, entre las 07:00 y las 20:00 (fuera de ese
 *       horario, a las 07:00): al contacto principal por WhatsApp y a los demás apoderados por correo.</li>
 *   <li><b>Resumen diario</b> a la hora del colegio ({@code hora_resumen_comunicados}): UN mensaje por familia y día con
 *       los comunicados nuevos y los que siguen sin confirmar a las 48 horas (el recordatorio). Al contacto principal por
 *       WhatsApp mientras no pase el tope del mes; después, y a los demás apoderados, por correo (E42: diez comunicados, un
 *       solo resumen).</li>
 *   <li>Sin títulos ni texto en el resumen; el emisor, corto («Dirección», «Prof. Ana T.»). Si un nombre calza con la
 *       regla de cobranza, sale genérico («su docente»), B6. Por WhatsApp, con el «sí» del apoderado; por correo, salvo
 *       oposición. Clave idempotente por familia y día (y por comunicado en el urgente).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('SISTEMA_COMUNICADOS')")
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosComunicadosFamilias {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosComunicadosFamilias.class);

	static final LocalTime DESDE = LocalTime.of(7, 0);

	static final LocalTime HASTA = LocalTime.of(20, 0);

	static final int RECORDATORIO_HORAS = 48;

	/** Los comunicados de los últimos días que se consideran «nuevos» (lo más viejo ya se avisó o se recordó). */
	static final int DIAS_ATRAS = 7;

	/** Lo que hizo una pasada. */
	public record Resultado(int urgentes, int resumenes) {
	}

	private final ComunicadosPorAvisar comunicados;

	private final Consentimientos consentimientos;

	private final HorarioComunicados horario;

	private final ApoderadoRepository apoderados;

	private final MensajeRepository mensajes;

	private final CreadorMensajes creador;

	private final CalendarioHabil calendario;

	private final AuditoriaService auditoria;

	public AvisosComunicadosFamilias(ComunicadosPorAvisar comunicados, Consentimientos consentimientos,
			HorarioComunicados horario, ApoderadoRepository apoderados, MensajeRepository mensajes, CreadorMensajes creador,
			CalendarioHabil calendario, AuditoriaService auditoria) {
		this.comunicados = comunicados;
		this.consentimientos = consentimientos;
		this.horario = horario;
		this.apoderados = apoderados;
		this.mensajes = mensajes;
		this.creador = creador;
		this.calendario = calendario;
		this.auditoria = auditoria;
	}

	/** Una pasada (cada 5 minutos): los urgentes pendientes y, si ya es la hora, el resumen del día. */
	public Resultado pasada(LocalDateTime ahora) {
		LocalDate hoy = ahora.toLocalDate();
		if (!admiteMensajes(hoy) || ahora.toLocalTime().isBefore(DESDE) || !ahora.toLocalTime().isBefore(HASTA)) {
			return new Resultado(0, 0);
		}
		List<ComunicadosPorAvisar.Pendiente> pendientes = comunicados.sinAvisar(ahora.minusDays(DIAS_ATRAS));
		Set<Long> familias = new LinkedHashSet<>();
		pendientes.forEach(p -> familias.add(p.familiaId()));
		boolean tocaResumen = !ahora.toLocalTime().isBefore(horario.horaResumen());
		List<ComunicadosPorAvisar.PorRecordar> recordar = tocaResumen
				? comunicados.porRecordar(ahora.minusHours(RECORDATORIO_HORAS)) : List.of();
		recordar.forEach(r -> familias.add(r.familiaId()));
		Map<Long, List<Apoderado>> porFamilia = familias.isEmpty() ? Map.of()
				: apoderados.findByFamiliaIdInAndActivoTrueOrderByIdAsc(familias).stream()
						.collect(Collectors.groupingBy(a -> a.getFamilia().getId(), LinkedHashMap::new, Collectors.toList()));
		int urgentes = 0;
		for (ComunicadosPorAvisar.Pendiente p : pendientes) {
			if (p.urgente()) {
				urgentes += urgente(p, porFamilia.getOrDefault(p.familiaId(), List.of()), ahora);
			}
		}
		int resumenes = 0;
		if (tocaResumen) {
			Map<Long, List<ComunicadosPorAvisar.Pendiente>> nuevos = new LinkedHashMap<>();
			pendientes.stream().filter(p -> !p.urgente())
					.forEach(p -> nuevos.computeIfAbsent(p.familiaId(), f -> new ArrayList<>()).add(p));
			Map<Long, List<ComunicadosPorAvisar.PorRecordar>> porConfirmar = new LinkedHashMap<>();
			recordar.forEach(r -> porConfirmar.computeIfAbsent(r.familiaId(), f -> new ArrayList<>()).add(r));
			Set<Long> conResumen = new LinkedHashSet<>(nuevos.keySet());
			conResumen.addAll(porConfirmar.keySet());
			for (Long familia : conResumen) {
				resumenes += resumen(familia, nuevos.getOrDefault(familia, List.of()),
						porConfirmar.getOrDefault(familia, List.of()), porFamilia.getOrDefault(familia, List.of()), ahora);
			}
		}
		if (urgentes + resumenes > 0) {
			auditoria.registrar(AccionAuditoria.AVISOS_COMUNICADOS_ENVIADOS, "comunicado", null, null,
					(urgentes + resumenes) + " mensaje(s)", "Avisos de comunicados del " + hoy + ": " + urgentes
							+ " urgente(s) y " + resumenes + " resumen(es).");
		}
		return new Resultado(urgentes, resumenes);
	}

	private int urgente(ComunicadosPorAvisar.Pendiente p, List<Apoderado> deLaFamilia, LocalDateTime ahora) {
		List<String> parametros = List.of(emisorCorto(p.emisor()), p.tituloUrgente() == null ? "" : p.tituloUrgente());
		if (ContenidoSinCobranza.hablaDeCobranza(String.join("\n", parametros))) {
			LOG.warn("El urgente del comunicado {} coincidió con la regla de cobranza: no se avisó.", p.comunicadoId());
			return 0;
		}
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.COMUNICADO_URGENTE,
				PlantillaMensaje.COMUNICADO_URGENTE, parametros, "comunicado", p.comunicadoId());
		Optional<Mensaje> primero = enviar(deLaFamilia, contenido, "familia-" + p.familiaId(), false, ahora);
		primero.ifPresent(m -> comunicados.avisado(p.destinatarioId(), m.getId()));
		return primero.isPresent() ? 1 : 0;
	}

	private int resumen(Long familia, List<ComunicadosPorAvisar.Pendiente> nuevos,
			List<ComunicadosPorAvisar.PorRecordar> porConfirmar, List<Apoderado> deLaFamilia, LocalDateTime ahora) {
		Set<String> emisores = new LinkedHashSet<>();
		nuevos.forEach(n -> emisores.add(emisorCorto(n.emisor())));
		porConfirmar.forEach(r -> emisores.add(emisorCorto(r.emisor())));
		List<String> parametros = List.of(cuantos(nuevos.size(), porConfirmar.size()), deQuienes(emisores));
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(TipoMensaje.COMUNICADOS_RESUMEN,
				PlantillaMensaje.COMUNICADOS, parametros, "familia", familia);
		String sufijo = ahora.toLocalDate().toString();
		// Si el resumen de hoy ya existía (salió antes, a la hora del colegio), lo nuevo va en el de mañana. Se decide por
		// la clave del día (no por la hora de creación, que depende del reloj de la base).
		if (mensajes.existsByTipoAndFamiliaIdAndClaveEndingWith(TipoMensaje.COMUNICADOS_RESUMEN, familia, ":" + sufijo)) {
			return 0;
		}
		Optional<Mensaje> primero = enviar(deLaFamilia, contenido, sufijo, true, ahora);
		if (primero.isEmpty()) {
			return 0;
		}
		Long id = primero.get().getId();
		nuevos.forEach(n -> comunicados.avisado(n.destinatarioId(), id));
		porConfirmar.forEach(r -> comunicados.recordado(r.destinatarioId(), id));
		return 1;
	}

	/**
	 * El contacto principal por WhatsApp (con su «sí» y, en el resumen, bajo el tope del mes); los demás apoderados, por
	 * correo salvo oposición. Devuelve el primer mensaje creado (o que ya existía con esa clave).
	 */
	private Optional<Mensaje> enviar(List<Apoderado> deLaFamilia, CreadorMensajes.Contenido contenido, String sufijo,
			boolean conTope, LocalDateTime ahora) {
		Optional<Mensaje> primero = Optional.empty();
		Optional<Long> elPrincipal = deLaFamilia.isEmpty() ? Optional.empty()
				: consentimientos.contactoPrincipal(deLaFamilia.get(0).getFamilia().getId())
						.filter(id -> deLaFamilia.stream().anyMatch(a -> a.getId().equals(id)));
		if (elPrincipal.isEmpty()) {
			// QA-S9-2 (desviación 6 de la tanda 3): sin contacto principal marcado (o si el marcado ya no está activo), el
			// primer apoderado activo (por id) que dio el «sí» a los comunicados por WhatsApp. Antes nadie recibía por
			// WhatsApp, ni siquiera un urgente.
			elPrincipal = deLaFamilia.stream().filter(a -> consentimientos.recibeComunicados(a.getId(), true))
					.map(Apoderado::getId).findFirst();
		}
		for (Apoderado a : deLaFamilia) {
			boolean principal = elPrincipal.filter(a.getId()::equals).isPresent();
			boolean porWhatsapp = principal && consentimientos.recibeComunicados(a.getId(), true)
					&& (!conTope || bajoElTope(a, ahora));
			boolean porCorreo = consentimientos.recibeComunicados(a.getId(), false);
			Optional<Mensaje> creado = creador.paraApoderadoConConsentimiento(a, contenido, sufijo, porWhatsapp, porCorreo);
			if (primero.isEmpty()) {
				primero = creado;
			}
		}
		return primero;
	}

	private boolean bajoElTope(Apoderado a, LocalDateTime ahora) {
		LocalDateTime inicioMes = ahora.toLocalDate().withDayOfMonth(1).atStartOfDay();
		return mensajes.countByTipoAndApoderadoIdAndCanalAndCreadoEnGreaterThanEqual(TipoMensaje.COMUNICADOS_RESUMEN,
				a.getId(), CanalMensaje.WHATSAPP, inicioMes) < horario.topeResumenesMes();
	}

	/** «2 comunicados nuevos y 1 por confirmar». */
	static String cuantos(int nuevos, int porConfirmar) {
		List<String> partes = new ArrayList<>();
		if (nuevos > 0) {
			partes.add(nuevos == 1 ? "1 comunicado nuevo" : nuevos + " comunicados nuevos");
		}
		if (porConfirmar > 0) {
			partes.add(porConfirmar + " por confirmar");
		}
		return String.join(" y ", partes);
	}

	/** «Dirección y Prof. Ana T.» (hasta 3; más, «y otros»). */
	static String deQuienes(Set<String> emisores) {
		List<String> lista = new ArrayList<>(emisores);
		if (lista.size() > 3) {
			return String.join(", ", lista.subList(0, 3)) + " y otros";
		}
		if (lista.size() <= 1) {
			return lista.isEmpty() ? "el colegio" : lista.get(0);
		}
		return String.join(", ", lista.subList(0, lista.size() - 1)) + " y " + lista.get(lista.size() - 1);
	}

	/**
	 * El emisor sin el nombre del colegio: «Dirección», «Promotoría» o «Prof. Ana T.»; si el nombre de la persona calza
	 * con la regla de cobranza de la base, «su docente» (B6).
	 */
	public static String emisorCorto(String etiqueta) {
		if (etiqueta == null) {
			return "el colegio";
		}
		if (etiqueta.startsWith("Dirección")) {
			return "Dirección";
		}
		if (etiqueta.startsWith("Promotoría")) {
			return "Promotoría";
		}
		String corto = etiqueta.contains(",") ? etiqueta.substring(0, etiqueta.indexOf(',')) : etiqueta;
		return ContenidoSinCobranza.hablaDeCobranza(corto) ? "su docente" : corto;
	}

	private boolean admiteMensajes(LocalDate dia) {
		return dia.getDayOfWeek() != DayOfWeek.SUNDAY && !calendario.esFeriado(dia);
	}
}
