package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.EnviosHuella;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo que ya salió de la huella diaria (correcciones del sprint 5, S5-M4 y S5-B3), leído de los mensajes HUELLA_BITACORA
 * del colegio actual: aunque alguien borre las filas de huella_bitacora, el mensaje (y la copia en el celular de
 * Promotoría) conserva la secuencia y el código.
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class HuellasEnviadas implements EnviosHuella {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final MensajeRepository mensajes;

	public HuellasEnviadas(MensajeRepository mensajes) {
		this.mensajes = mensajes;
	}

	@Override
	public Optional<HuellaEnviada> mayorEnviada() {
		return mensajes.findTop60ByTipoOrderByIdDesc(TipoMensaje.HUELLA_BITACORA).stream()
				.map(HuellasEnviadas::leer).filter(Objects::nonNull)
				.max(Comparator.comparingLong(HuellaEnviada::secuencia));
	}

	@Override
	public boolean salio(Long huellaId) {
		return mensajes.existsByTipoAndEntidadAndEntidadIdAndEstadoIn(TipoMensaje.HUELLA_BITACORA, "huella_bitacora",
				huellaId, EnumSet.of(EstadoMensaje.ENVIADO, EstadoMensaje.ENTREGADO, EstadoMensaje.LEIDO));
	}

	/** Los parámetros de la plantilla HUELLA: fecha, secuencia, código, ... */
	static HuellaEnviada leer(Mensaje mensaje) {
		List<String> p = mensaje.parametrosLista();
		if (p.size() < 3) {
			return null;
		}
		try {
			return new HuellaEnviada(LocalDate.parse(p.get(0), FECHA), Long.parseLong(p.get(1)), p.get(2));
		}
		catch (NumberFormatException | DateTimeParseException e) {
			return null;
		}
	}
}
