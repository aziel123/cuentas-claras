package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AvisosPromotoriaListos;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Alertas al celular de Promotoría (sprint 6, tanda 2; decisiones 70 y 71). {@code sistema.panel} toma las alertas que ya
 * existen ({@link AlertasRevision}, sin duplicar su lógica), se queda con las que tienen {@link Aviso} y son CRÍTICAS (o
 * las dos ATENCIÓN que también salen) y las publica, las críticas primero; la mensajería crea los mensajes en esta misma
 * transacción, una vez por alerta y destinatario, con texto fijo.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_PANEL')")
public class DifusionAvisos {

	private final ObjectProvider<AlertasRevision> alertas;

	private final ApplicationEventPublisher eventos;

	public DifusionAvisos(ObjectProvider<AlertasRevision> alertas, ApplicationEventPublisher eventos) {
		this.alertas = alertas;
		this.eventos = eventos;
	}

	/** @return los avisos que se publicaron en esta pasada (la mensajería decide a quién y si ya se avisaron) */
	@Transactional
	public List<Aviso> difundir(LocalDate fecha) {
		Objects.requireNonNull(fecha, "fecha");
		Map<String, Aviso> avisos = new LinkedHashMap<>();
		alertas.orderedStream().filter(AlertasRevision::difundible).flatMap(a -> a.alertas().stream())
				.filter(AlertaRevision::difundible).sorted(AlertaRevision.POR_GRAVEDAD)
				.forEach(a -> avisos.putIfAbsent(a.aviso().tipo() + ":" + a.aviso().referencia(), a.aviso()));
		List<Aviso> lista = List.copyOf(avisos.values());
		if (!lista.isEmpty()) {
			eventos.publishEvent(new AvisosPromotoriaListos(ContextoColegio.actual(), fecha, lista));
		}
		return lista;
	}
}
