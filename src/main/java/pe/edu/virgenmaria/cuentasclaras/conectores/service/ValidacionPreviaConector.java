package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ValidacionPrevia;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;

import java.util.List;

/**
 * Correcciones del sprint 8 (QA-S8-6): antes de abrir la transacción de la aprobación de un {@code CAMBIO_CONECTOR}, la
 * bandeja pide a cada adaptador lo que necesita ver de OTROS colegios ({@link AdaptadorConector#validarFueraDeTransaccion}):
 * por ejemplo, que ninguna serie del OSE la use otra sede con el mismo RUC.
 */
@Component
public class ValidacionPreviaConector implements ValidacionPrevia {

	private final pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository conectores;

	private final ConectoresColegio vigentes;

	private final List<AdaptadorConector> adaptadores;

	private final TransactionTemplate lectura;

	public ValidacionPreviaConector(pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository conectores,
			ConectoresColegio vigentes, List<AdaptadorConector> adaptadores, PlatformTransactionManager transacciones) {
		this.conectores = conectores;
		this.vigentes = vigentes;
		this.adaptadores = List.copyOf(adaptadores);
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CAMBIO_CONECTOR;
	}

	@Override
	public void validar(SolicitudCambio solicitud) {
		ConectorVigente candidato = lectura.execute(t -> conectores.findById(solicitud.getEntidadId())
				.filter(c -> c.getEstado() == EstadoConector.PROPUESTO).map(vigentes::descifradoParaPrueba).orElse(null));
		if (candidato == null) {
			return;
		}
		adaptadores.stream().filter(a -> a.tipo() == candidato.tipo() && a.admite(candidato.proveedor()))
				.forEach(a -> a.validarFueraDeTransaccion(candidato));
	}
}
