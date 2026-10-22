package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.api.EstadoDeAvisos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Sprint 9, tanda 3 (sección 3.10): si los avisos de un comunicado llegaron o se leyeron (para sus estadísticas). */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class EstadoDeAvisosMensajes implements EstadoDeAvisos {

	private final MensajeRepository mensajes;

	public EstadoDeAvisosMensajes(MensajeRepository mensajes) {
		this.mensajes = mensajes;
	}

	@Override
	public Map<Long, Entrega> de(Collection<Long> ids) {
		Map<Long, Entrega> resultado = new HashMap<>();
		if (ids.isEmpty()) {
			return resultado;
		}
		for (Mensaje m : mensajes.findByIdIn(ids)) {
			boolean leido = m.getEstado() == EstadoMensaje.LEIDO || m.getLeidoEn() != null;
			boolean entregado = leido || m.getEstado() == EstadoMensaje.ENTREGADO || m.getEntregadoEn() != null;
			resultado.put(m.getId(), new Entrega(entregado, leido));
		}
		return resultado;
	}
}
