package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.SupresionesDeLaBitacora;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.ActaSupresion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ActaSupresionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 11, tanda 2 (3.11): las actas EJECUTADAS de todos los colegios (solo su huella y su última secuencia), para la
 * verificación de la cadena. Es de las pocas clases que ven todos los colegios ({@code ContextoColegio.comoSistema}).
 */
@Component
public class ActasEjecutadasBitacora implements SupresionesDeLaBitacora {

	private final ActaSupresionRepository actas;

	private final TransactionTemplate lectura;

	public ActasEjecutadasBitacora(ActaSupresionRepository actas, PlatformTransactionManager transacciones) {
		this.actas = actas;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	@Override
	public Map<Long, ActaEjecutada> actasEjecutadas() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			// Con una transacción abierta no se cambia de colegio: sin actas, un tachado rompe la verificación (lo seguro).
			return Map.of();
		}
		return ContextoColegio.comoSistema(() -> lectura.execute(t -> actas.findByEstado(ActaSupresion.Estado.EJECUTADA)
				.stream().filter(a -> a.getHuellaEventos() != null && a.getUltimaSecuencia() != null)
				.map(a -> new ActaEjecutada(a.getColegioId(), a.getId(), a.getHuellaEventos(), a.getUltimaSecuencia()))
				.collect(Collectors.toMap(ActaEjecutada::colegioId, Function.identity()))));
	}
}
