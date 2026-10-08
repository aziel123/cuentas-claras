package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.HuellaGuardada;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.HuellaHora;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.HuellaGuardadaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.HuellaHoraRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Huella diaria de la bitácora (sprint 5, G13; correcciones S5-M4). Solo la usa {@code sistema.auditoria} desde su
 * proceso:
 * <ul>
 *   <li>{@link #reverificarHuellas}: cada huella guardada (diaria y por hora) de los últimos días debe seguir en la
 *       bitácora con el mismo código. Si alguien recortó la cola de la cadena, queda
 *       {@link AccionAuditoria#HUELLA_NO_COINCIDE} resaltado y Promotoría lo recibe en el mensaje del día.</li>
 *   <li>{@link #registrarHora}: la huella de la hora en horario de caja (un recorte del mismo día ya no pasa
 *       desapercibido).</li>
 *   <li>{@link #registrar}: guarda la huella del día y la publica para que salga por mensaje, con la huella ANTERIOR
 *       (Promotoría compara el mensaje de hoy con el de ayer). La secuencia nueva nunca es menor que la de una huella ya
 *       guardada o ya enviada (si lo es, la bitácora retrocedió: {@link AccionAuditoria#HUELLA_RETROCEDIO}, no se
 *       guarda y el mensaje dice «NO»); si faltan días, {@link AccionAuditoria#HUELLA_FALTAN_DIAS}.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('SISTEMA_AUDITORIA')")
public class HuellasDiarias {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final HuellaGuardadaRepository huellas;

	private final HuellaHoraRepository horas;

	private final EventoAuditoriaRepository eventos;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher publicador;

	private final ObjectProvider<EnviosHuella> envios;

	public HuellasDiarias(HuellaGuardadaRepository huellas, HuellaHoraRepository horas, EventoAuditoriaRepository eventos,
			AuditoriaService auditoria, ApplicationEventPublisher publicador, ObjectProvider<EnviosHuella> envios) {
		this.huellas = huellas;
		this.horas = horas;
		this.eventos = eventos;
		this.auditoria = auditoria;
		this.publicador = publicador;
		this.envios = envios;
	}

	/** @return las fechas de las huellas guardadas desde {@code desde} que ya NO coinciden con la bitácora */
	@Transactional
	public List<LocalDate> reverificarHuellas(LocalDate desde) {
		Long colegio = colegio();
		List<LocalDate> distintas = new ArrayList<>();
		for (HuellaGuardada huella : huellas.findByFechaGreaterThanEqualOrderByFechaAsc(desde)) {
			Optional<EventoAuditoria> evento = eventos.findBySecuencia(huella.getSecuencia());
			boolean coincide = evento.filter(e -> colegio.equals(e.getColegioId()))
					.filter(e -> huella.coincideCon(e.getHash())).isPresent();
			if (!coincide) {
				distintas.add(huella.getFecha());
				noCoincide(huella.getId(), "huella_bitacora", huella.getSecuencia(), huella.getCodigo(), evento.isPresent(),
						"La huella del " + huella.getFecha());
			}
		}
		for (HuellaHora hora : horas.findByMomentoGreaterThanEqualOrderByMomentoAsc(desde.atStartOfDay())) {
			Optional<EventoAuditoria> evento = eventos.findBySecuencia(hora.getSecuencia());
			boolean coincide = evento.filter(e -> colegio.equals(e.getColegioId()))
					.filter(e -> hora.coincideCon(e.getHash())).isPresent();
			if (!coincide) {
				distintas.add(hora.getMomento().toLocalDate());
				noCoincide(hora.getId(), "huella_hora", hora.getSecuencia(), hora.getCodigo(), evento.isPresent(),
						"La huella de las " + hora.getMomento().toLocalTime().truncatedTo(ChronoUnit.MINUTES) + " del "
								+ hora.getMomento().toLocalDate());
			}
		}
		return distintas;
	}

	private void noCoincide(Long id, String entidad, long secuencia, String codigo, boolean existe, String cual) {
		auditoria.registrar(AccionAuditoria.HUELLA_NO_COINCIDE, entidad, id.toString(), "evento " + secuencia + " · "
				+ codigo, existe ? "otro código" : "el evento ya no está", cual + " ya no coincide con la bitácora: la "
						+ "bitácora fue recortada o alterada. Compárala con la copia que recibió Promotoría.");
	}

	/**
	 * S5-M4: la huella de la hora (horario de caja). Si no hubo eventos nuevos desde la última, no guarda nada; si el
	 * último evento es ANTERIOR a la última huella guardada o enviada, la bitácora retrocedió.
	 *
	 * @return la huella guardada, si hubo eventos nuevos
	 */
	@Transactional
	public Optional<HuellaHora> registrarHora(LocalDateTime momento) {
		Long colegio = colegio();
		Optional<EventoAuditoria> ultimo = eventos.findFirstByColegioIdAndOcurridoEnLessThanOrderBySecuenciaDesc(colegio,
				momento);
		if (ultimo.isEmpty()) {
			return Optional.empty();
		}
		long secuencia = ultimo.get().getSecuencia();
		long piso = piso();
		if (secuencia < piso) {
			retrocedio(secuencia, piso, "la huella de las " + momento.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
			return Optional.empty();
		}
		if (horas.findFirstByOrderBySecuenciaDesc().filter(h -> h.getSecuencia() >= secuencia).isPresent()) {
			return Optional.empty();
		}
		return Optional.of(horas.saveAndFlush(HuellaHora.de(momento.truncatedTo(ChronoUnit.MICROS), secuencia,
				ultimo.get().getHash())));
	}

	/**
	 * Guarda la huella de {@code fecha} (si aún no existe y el colegio ya tiene eventos) y la publica.
	 *
	 * @param verificacionOk resultado de la reverificación de esta mañana (va en el mensaje)
	 */
	@Transactional
	public Optional<HuellaDelDia> registrar(LocalDate fecha, boolean verificacionOk) {
		Long colegio = colegio();
		if (huellas.findByFecha(fecha).isPresent()) {
			return Optional.empty();
		}
		Optional<EventoAuditoria> ultimo = eventos.findFirstByColegioIdAndOcurridoEnLessThanOrderBySecuenciaDesc(colegio,
				fecha.plusDays(1).atStartOfDay());
		if (ultimo.isEmpty()) {
			return Optional.empty();
		}
		Optional<HuellaGuardada> previa = huellas.findFirstByOrderByFechaDesc();
		Optional<EnviosHuella.HuellaEnviada> enviada = mayorEnviada();
		String anterior = previa.map(h -> "evento " + h.getSecuencia() + ", código " + h.getCodigo() + " del "
				+ h.getFecha().format(FECHA))
				.or(() -> enviada.map(e -> "evento " + e.secuencia() + ", código " + e.codigo() + " del "
						+ e.fecha().format(FECHA)))
				.orElse("ninguna");
		boolean ok = verificacionOk;
		LocalDate ultimaFecha = previa.map(HuellaGuardada::getFecha)
				.or(() -> enviada.map(EnviosHuella.HuellaEnviada::fecha)).orElse(null);
		Optional<EnviosHuella.HuellaEnviada> borrada = enviada.filter(e -> huellas.findByFecha(e.fecha()).isEmpty());
		if (borrada.isPresent()) {
			ok = false;
			auditoria.registrar(AccionAuditoria.HUELLA_FALTAN_DIAS, "huella_bitacora", null,
					"evento " + borrada.get().secuencia(), "ya no está", "La huella del " + borrada.get().fecha().format(FECHA)
							+ " (evento " + borrada.get().secuencia() + ") salió por mensaje a Promotoría pero ya no está "
							+ "guardada: alguien la borró de la base.");
		}
		else if (ultimaFecha != null && ultimaFecha.isBefore(fecha.minusDays(1))) {
			ok = false;
			auditoria.registrar(AccionAuditoria.HUELLA_FALTAN_DIAS, "huella_bitacora", null, ultimaFecha.toString(),
					fecha.toString(), "Falta la huella de " + (ChronoUnit.DAYS.between(ultimaFecha, fecha) - 1)
							+ " día(s) entre el " + ultimaFecha.format(FECHA) + " y el " + fecha.format(FECHA)
							+ (previa.isEmpty() ? " (y no queda ninguna huella guardada de las que se enviaron)" : "")
							+ ": alguien pudo borrarlas o el proceso no corrió.");
		}
		long secuencia = ultimo.get().getSecuencia();
		long piso = piso();
		int delDia = (int) eventos.countByColegioIdAndOcurridoEnGreaterThanEqualAndOcurridoEnLessThan(colegio,
				fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
		String codigo = ultimo.get().getHash().substring(0, 16);
		if (secuencia < piso) {
			retrocedio(secuencia, piso, "la huella del " + fecha.format(FECHA));
			HuellaDelDia evento = new HuellaDelDia(colegio, null, fecha, secuencia, codigo, delDia, false, anterior);
			publicador.publishEvent(evento);
			return Optional.of(evento);
		}
		HuellaGuardada huella = huellas.saveAndFlush(HuellaGuardada.de(fecha, secuencia, ultimo.get().getHash(), delDia));
		HuellaDelDia evento = new HuellaDelDia(colegio, huella.getId(), fecha, huella.getSecuencia(), huella.getCodigo(),
				delDia, ok, anterior);
		publicador.publishEvent(evento);
		auditoria.registrar(AccionAuditoria.HUELLA_ENVIADA, "huella_bitacora", huella.getId().toString(), null,
				"evento " + huella.getSecuencia() + " · " + huella.getCodigo(), "Huella del " + fecha + " (" + delDia
						+ " eventos). Bitácora verificada esta mañana: " + (ok ? "sí" : "NO") + ". Huella anterior: "
						+ anterior + ".");
		return Optional.of(evento);
	}

	/** La mayor secuencia ya guardada (diaria o por hora) o ya enviada por mensaje: la bitácora no puede volver atrás. */
	private long piso() {
		long piso = huellas.findFirstByOrderBySecuenciaDesc().map(HuellaGuardada::getSecuencia).orElse(0L);
		piso = Math.max(piso, horas.findFirstByOrderBySecuenciaDesc().map(HuellaHora::getSecuencia).orElse(0L));
		return Math.max(piso, mayorEnviada().map(EnviosHuella.HuellaEnviada::secuencia).orElse(0L));
	}

	private Optional<EnviosHuella.HuellaEnviada> mayorEnviada() {
		EnviosHuella proveedor = envios.getIfAvailable();
		return proveedor == null ? Optional.empty() : proveedor.mayorEnviada();
	}

	private void retrocedio(long secuencia, long piso, String cual) {
		auditoria.registrar(AccionAuditoria.HUELLA_RETROCEDIO, "huella_bitacora", null, "evento " + piso,
				"evento " + secuencia, "El último evento del colegio para " + cual + " es el " + secuencia
						+ ", ANTERIOR al " + piso + " de una huella ya guardada o enviada a Promotoría: la bitácora fue "
						+ "recortada. No se guarda la huella; compárala con los mensajes que recibió Promotoría.");
	}

	private static Long colegio() {
		Long colegio = ContextoColegio.actual();
		if (colegio == null || colegio <= 0) {
			throw new IllegalStateException("La huella diaria se registra en un colegio");
		}
		return colegio;
	}
}
