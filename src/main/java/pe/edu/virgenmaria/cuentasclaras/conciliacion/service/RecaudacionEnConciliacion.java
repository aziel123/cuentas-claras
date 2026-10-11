package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.ConectoresColegio;

/**
 * Sprint 8, tanda 2 (sección 3.6, H4): cómo abona el banco la recaudación del colegio en contexto (por lote o pago por
 * pago) y con qué glosa (la regla EXACTA de la conciliación). Salen de su conector RECAUDACION vigente; sin conector, de
 * {@code cuentasclaras.conciliacion} (como hasta el sprint 7).
 */
@Component
public class RecaudacionEnConciliacion {

	private final ConectoresColegio conectores;

	private final PropiedadesConciliacion propiedades;

	public RecaudacionEnConciliacion(ConectoresColegio conectores, PropiedadesConciliacion propiedades) {
		this.conectores = conectores;
		this.propiedades = propiedades;
	}

	public String patronAbono() {
		return conectores.vigente(TipoConector.RECAUDACION)
				.map(c -> c.configuracionDe(ConfiguracionConector.Recaudacion.class).patronAbono())
				.map(p -> p == null ? "" : p.strip()).orElse(propiedades.patronAbonoRecaudacion());
	}

	public PropiedadesConciliacion.AbonoRecaudacion abono() {
		return conectores.vigente(TipoConector.RECAUDACION)
				.map(c -> PropiedadesConciliacion.AbonoRecaudacion.valueOf(c.configuracionDe(
						ConfiguracionConector.Recaudacion.class).abono()))
				.orElse(propiedades.abonoRecaudacion());
	}
}
