package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.ReembolsoRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;

import java.util.HashSet;
import java.util.Set;

/**
 * Quiénes NO pueden dar por buena (ni aprobar) la pareja de un objeto de la conciliación: quien lo cobró o lo registró,
 * quien depositó, quien registró el reembolso o la devolución, y quien SUBIÓ el lote de recaudación del que salió (también
 * cuando el banco abona pago por pago y el objeto es el pago de recaudación: QA-S4-6), más quienes prepararon sus cuentas.
 * La base lo vuelve a exigir en {@code trg_partida_conciliacion_estado} (S4-B2). Solo lectura, en la transacción de quien
 * llama.
 */
@Component
public class ResponsablesPartida {

	private final PagoRepository pagos;

	private final DepositoCajaRepository depositos;

	private final ReembolsoRepository reembolsos;

	private final LoteRecaudacionRepository lotes;

	private final LineaRecaudacionRepository lineas;

	private final ControlParticipantes participantes;

	public ResponsablesPartida(PagoRepository pagos, DepositoCajaRepository depositos, ReembolsoRepository reembolsos,
			LoteRecaudacionRepository lotes, LineaRecaudacionRepository lineas, ControlParticipantes participantes) {
		this.pagos = pagos;
		this.depositos = depositos;
		this.reembolsos = reembolsos;
		this.lotes = lotes;
		this.lineas = lineas;
		this.participantes = participantes;
	}

	/** Los responsables directos del objeto, ampliados con quienes prepararon sus cuentas. */
	public Set<String> de(ObjetoPartida tipo, Long objetoId) {
		Set<String> responsables = new HashSet<>();
		switch (tipo) {
			case PAGO -> pagos.findById(objetoId).ifPresent(p -> {
				responsables.add(p.getCajero());
				responsables.add(p.getCreadoPor());
				if (p.getLineaRecaudacionId() != null) {
					lineas.findById(p.getLineaRecaudacionId()).ifPresent(l -> responsables.add(l.getLote().getCreadoPor()));
				}
			});
			case DEPOSITO -> depositos.findById(objetoId).ifPresent(d -> {
				responsables.add(d.getCreadoPor());
				responsables.add(d.getCaja().getCajero());
			});
			case REEMBOLSO -> reembolsos.findById(objetoId).ifPresent(r -> responsables.add(r.getCreadoPor()));
			case LOTE_RECAUDACION -> lotes.findById(objetoId).ifPresent(t -> responsables.add(t.getCreadoPor()));
			case LINEA_RECAUDACION -> lineas.findById(objetoId).ifPresent(l -> {
				responsables.add(l.getLote().getCreadoPor());
				responsables.add(l.getDevueltoPor());
			});
			default -> {
				// La liquidación la registra el sistema (por la API de la pasarela).
			}
		}
		responsables.remove(null);
		return participantes.ampliar(responsables);
	}
}
