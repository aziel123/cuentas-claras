package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CierreCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reapertura de una caja aprobada por otra persona: solo la caja de HOY, cerrada y sin depósito. La caja guarda el id de
 * su solicitud (M1): en MySQL el trigger exige que esa solicitud esté APROBADA (la bandeja la aprueba antes de aplicar),
 * sea de reapertura de ESTA caja, se haya resuelto el día de la caja por alguien que no es su cajero y no se haya usado
 * antes. Reinicia el conteo; el cierre anterior queda registrado tal cual, con su diferencia: reabrir no «arregla» un
 * faltante, solo permite seguir cobrando. El cierre siguiente no es ciego y queda marcado «tras reapertura».
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorReaperturaCaja implements ManejadorSolicitud {

	private final CajaDiariaRepository cajas;

	private final CierreCajaRepository cierres;

	private final DepositoCajaRepository depositos;

	private final AuditoriaService auditoria;

	private final NombresUsuarios nombres;

	private final Clock reloj;

	public ManejadorReaperturaCaja(CajaDiariaRepository cajas, CierreCajaRepository cierres,
			DepositoCajaRepository depositos, AuditoriaService auditoria, NombresUsuarios nombres, Clock reloj) {
		this.cajas = cajas;
		this.cierres = cierres;
		this.depositos = depositos;
		this.auditoria = auditoria;
		this.nombres = nombres;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.REAPERTURA_CAJA;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		CajaDiaria caja = cajas.bloquearPorId(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La caja ya no existe."));
		if (caja.getEstado() != EstadoCaja.CERRADA) {
			throw new ReglaNegocioException("La caja ya está abierta.");
		}
		if (!caja.getFecha().equals(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("Solo se reabre la caja del mismo día: la del "
					+ Calendario.formatear(caja.getFecha()) + " ya quedó cerrada.");
		}
		if (depositos.existsByCajaId(caja.getId())) {
			throw new ReglaNegocioException("El efectivo de esta caja ya se depositó: no se puede reabrir.");
		}
		caja.reabrir(solicitud.getId());
		cajas.saveAndFlush(caja);
		auditoria.registrar(AccionAuditoria.CAJA_REABIERTA, "caja_diaria", caja.getId().toString(),
				EstadoCaja.CERRADA.name(), EstadoCaja.ABIERTA.name(), "Caja de " + caja.getCajero() + " del "
						+ Calendario.formatear(caja.getFecha()) + " reabierta. Pedido por " + solicitud.getSolicitadoPor()
						+ ", aprobado por " + aprobador + ". Motivo: " + solicitud.getMotivo()
						+ ". Su cierre anterior queda registrado tal cual.");
	}

	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return cajas.findById(solicitud.getEntidadId()).map(c -> Set.of(c.getCajero())).orElse(Set.of());
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		CajaDiaria caja = cajas.findById(solicitud.getEntidadId()).orElse(null);
		if (caja == null) {
			return List.of();
		}
		List<String> lineas = new ArrayList<>();
		lineas.add("Caja de " + nombres.de(caja.getCajero()) + " (" + caja.getCajero() + ") del "
				+ Calendario.formatear(caja.getFecha()));
		cierres.findFirstByCajaIdOrderByNumeroDesc(caja.getId()).ifPresent(c -> lineas.add("Su cierre N.° "
				+ c.getNumero() + ": esperado " + Dinero.formatear(c.getEsperado()) + ", contado "
				+ Dinero.formatear(c.getContado()) + ", " + ServicioCierreCaja.diferenciaTexto(c.getDiferencia()) + " ("
				+ c.getEstado().etiqueta().toLowerCase() + ")"));
		lineas.add("Al reabrir, el cierre anterior no se borra ni se corrige. El siguiente cierre no será ciego (la cajera ya "
				+ "vio el esperado): quedará marcado «tras reapertura» y se revisará con comentario.");
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return cierres.findFirstByCajaIdOrderByNumeroDesc(solicitud.getEntidadId()).filter(CierreCaja::conDiferencia)
				.map(c -> "Esa caja cerró con " + ServicioCierreCaja.diferenciaTexto(c.getDiferencia())
						+ ": reabrir no la corrige. Si es para «arreglar» el faltante, recházala.")
				.orElse(null);
	}
}
