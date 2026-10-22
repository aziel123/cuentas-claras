package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Sprint 9, tanda 3 (sección 3.7 y 9): cuántos mensajes salieron este mes por grupo (pagos y cobranza, asistencia,
 * comunicados y los demás), para estimar el costo de WhatsApp en {@code /panel/sistema}. Solo conteos.
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
public class ConsumoMensajes {

	/** Un grupo y cuántos mensajes tuvo en el mes. */
	public record Grupo(String nombre, long mensajes) {
	}

	private static final Set<TipoMensaje> ASISTENCIA = EnumSet.of(TipoMensaje.ASISTENCIA_DIA,
			TipoMensaje.ASISTENCIA_CORREGIDA);

	private static final Set<TipoMensaje> COMUNICADOS = EnumSet.of(TipoMensaje.COMUNICADOS_RESUMEN,
			TipoMensaje.COMUNICADO_URGENTE);

	private final MensajeRepository mensajes;

	private final Clock reloj;

	public ConsumoMensajes(MensajeRepository mensajes, Clock reloj) {
		this.mensajes = mensajes;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<Grupo> delMes() {
		LocalDateTime desde = LocalDate.now(reloj).withDayOfMonth(1).atStartOfDay();
		long asistencia = 0;
		long comunicados = 0;
		long pagos = 0;
		long otros = 0;
		for (TipoMensaje tipo : Arrays.asList(TipoMensaje.values())) {
			long cuantos = mensajes.countByTipoAndCreadoEnGreaterThanEqual(tipo, desde);
			if (ASISTENCIA.contains(tipo)) {
				asistencia += cuantos;
			}
			else if (COMUNICADOS.contains(tipo)) {
				comunicados += cuantos;
			}
			else if (tipo.financiero() || tipo == TipoMensaje.RECORDATORIO_VENCIMIENTO || tipo == TipoMensaje.CUOTA_VENCIDA) {
				pagos += cuantos;
			}
			else {
				otros += cuantos;
			}
		}
		return List.of(new Grupo("Pagos y cobranza", pagos), new Grupo("Asistencia", asistencia),
				new Grupo("Comunicados", comunicados), new Grupo("Otros avisos", otros));
	}
}
