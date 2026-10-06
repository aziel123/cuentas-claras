package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.config.PropiedadesCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.LocalDate;

/**
 * Abre la caja del cajero en el día con su primer cobro, en su PROPIA transacción (confirmada antes de que el cobro la
 * bloquee). Si dos cobros de la misma cajera la abren a la vez, el segundo falla por el UNIQUE (cajero, fecha) con
 * {@code DataIntegrityViolationException}: quien llama la ignora y usa la caja que abrió el otro.
 * Se llama ANTES de abrir la transacción del cobro (dentro de ella pediría una segunda conexión del pool).
 */
@Component
public class AperturaCaja {

	private final CajaDiariaRepository cajas;

	private final AuditoriaService auditoria;

	private final PropiedadesCaja propiedades;

	public AperturaCaja(CajaDiariaRepository cajas, AuditoriaService auditoria, PropiedadesCaja propiedades) {
		this.cajas = cajas;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void asegurar(String cajero, LocalDate fecha) {
		if (cajas.findByCajeroAndFecha(cajero, fecha).isPresent()) {
			return;
		}
		CajaDiaria caja = cajas.save(CajaDiaria.abrir(cajero, fecha, propiedades.fondoFijo()));
		auditoria.registrar(AccionAuditoria.CAJA_ABIERTA, "caja_diaria", caja.getId().toString(), null,
				caja.getEstado().name(), "Caja de " + cajero + " del " + Calendario.formatear(fecha) + " con fondo fijo "
						+ Dinero.formatear(caja.getFondoFijo()) + ".");
	}

	/**
	 * Sprint 4: la caja del canal (PASARELA) del día, en su propia transacción, antes de la transacción del pago. La abre
	 * su actor de sistema; si otra la abre a la vez, el UNIQUE (cajero, fecha) hace fallar a la segunda y se usa la otra.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void asegurarCanal(CanalCaja canal, LocalDate fecha) {
		ActorSistema actor = switch (canal) {
			case PASARELA -> ActorSistema.PASARELA;
			case RECAUDACION -> ActorSistema.RECAUDACION;
			case VENTANILLA -> throw new IllegalArgumentException("La ventanilla la abre su cajera");
		};
		if (cajas.findByCajeroAndFecha(actor.usuario(), fecha).isPresent()) {
			return;
		}
		CajaDiaria caja = cajas.save(CajaDiaria.abrirCanal(canal, actor, fecha));
		auditoria.registrar(AccionAuditoria.CAJA_ABIERTA, "caja_diaria", caja.getId().toString(), null,
				caja.getEstado().name(), "Caja del canal " + canal.etiqueta() + " del " + Calendario.formatear(fecha)
						+ " (" + actor.usuario() + "): solo pagos digitales que entran solos, sin efectivo ni cierre.");
	}
}
