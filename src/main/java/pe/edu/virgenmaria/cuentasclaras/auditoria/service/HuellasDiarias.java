package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.HuellaGuardada;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.HuellaGuardadaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Huella diaria de la bitácora (sprint 5, G13). Solo la usa {@code sistema.auditoria} desde su proceso:
 * <ul>
 *   <li>{@link #reverificarHuellas}: cada huella guardada de los últimos días debe seguir en la bitácora con el mismo
 *       código. Si alguien recortó la cola de la cadena (y retrocedió el eslabón), la huella ya no coincide: queda
 *       {@link AccionAuditoria#HUELLA_NO_COINCIDE} resaltado y Promotoría lo recibe en el mensaje del día.</li>
 *   <li>{@link #registrar}: guarda la huella del día (el último evento del colegio hasta las 23:59:59) y publica
 *       {@link HuellaDelDia} para que salga por mensaje.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('SISTEMA_AUDITORIA')")
public class HuellasDiarias {

	private final HuellaGuardadaRepository huellas;

	private final EventoAuditoriaRepository eventos;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher publicador;

	public HuellasDiarias(HuellaGuardadaRepository huellas, EventoAuditoriaRepository eventos, AuditoriaService auditoria,
			ApplicationEventPublisher publicador) {
		this.huellas = huellas;
		this.eventos = eventos;
		this.auditoria = auditoria;
		this.publicador = publicador;
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
				auditoria.registrar(AccionAuditoria.HUELLA_NO_COINCIDE, "huella_bitacora", huella.getId().toString(),
						"evento " + huella.getSecuencia() + " · " + huella.getCodigo(),
						evento.isPresent() ? "otro código" : "el evento ya no está",
						"La huella del " + huella.getFecha() + " ya no coincide con la bitácora: la bitácora fue recortada o "
								+ "alterada. Compárala con la copia que recibió Promotoría.");
			}
		}
		return distintas;
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
		int delDia = (int) eventos.countByColegioIdAndOcurridoEnGreaterThanEqualAndOcurridoEnLessThan(colegio,
				fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
		HuellaGuardada huella = huellas.saveAndFlush(HuellaGuardada.de(fecha, ultimo.get().getSecuencia(),
				ultimo.get().getHash(), delDia));
		HuellaDelDia evento = new HuellaDelDia(colegio, huella.getId(), fecha, huella.getSecuencia(), huella.getCodigo(),
				delDia, verificacionOk);
		publicador.publishEvent(evento);
		auditoria.registrar(AccionAuditoria.HUELLA_ENVIADA, "huella_bitacora", huella.getId().toString(), null,
				"evento " + huella.getSecuencia() + " · " + huella.getCodigo(), "Huella del " + fecha + " (" + delDia
						+ " eventos). Bitácora verificada esta mañana: " + (verificacionOk ? "sí" : "NO") + ".");
		return Optional.of(evento);
	}

	private static Long colegio() {
		Long colegio = ContextoColegio.actual();
		if (colegio == null || colegio <= 0) {
			throw new IllegalStateException("La huella diaria se registra en un colegio");
		}
		return colegio;
	}
}
