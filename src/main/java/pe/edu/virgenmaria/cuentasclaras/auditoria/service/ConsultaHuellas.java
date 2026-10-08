package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.HuellaHoraRepository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Lectura de las huellas por hora para el resumen diario (sprint 6, tanda 2; P18): la huella de las 19:00 sale en el
 * mensaje de las 19:30 y queda en la foto, así un recorte de la bitácora por la tarde ya tiene una copia fuera del sistema
 * (residual S5-M4). Solo lectura; la usan {@code sistema.panel} y Promotoría.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class ConsultaHuellas {

	/** Una huella por hora guardada: su momento, la secuencia del último evento y los 16 primeros caracteres del hash. */
	public record HuellaDeLaHora(LocalDateTime momento, long secuencia, String codigo) {
	}

	private final HuellaHoraRepository horas;

	public ConsultaHuellas(HuellaHoraRepository horas) {
		this.horas = horas;
	}

	/**
	 * La última huella por hora guardada hasta {@code hasta} (si a las 19:00 no hubo eventos nuevos, es la anterior: la
	 * bitácora no cambió desde entonces). Vacío si el colegio aún no tiene ninguna.
	 */
	public Optional<HuellaDeLaHora> ultimaHasta(LocalDateTime hasta) {
		Objects.requireNonNull(hasta, "hasta");
		return horas.findFirstByMomentoLessThanEqualOrderByMomentoDesc(hasta)
				.map(h -> new HuellaDeLaHora(h.getMomento(), h.getSecuencia(), h.getCodigo()));
	}
}
