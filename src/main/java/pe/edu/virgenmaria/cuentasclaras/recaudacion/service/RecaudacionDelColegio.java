package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.AdaptadorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.ConectoresColegio;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.AdaptadorRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.BancoRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.ModalidadRecaudacion;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 8, tanda 2 (sección 3.6, H4): la recaudación bancaria del colegio en contexto. El banco (su formato) y la
 * modalidad salen de su conector RECAUDACION vigente; sin conector, de {@code cuentasclaras.recaudacion} (como hasta el
 * sprint 7). También es el {@link AdaptadorConector} de la recaudación: un banco cuyo formato todavía no existe no pasa
 * la prueba (se usa el genérico, decisión 24).
 */
@Component
public class RecaudacionDelColegio implements AdaptadorConector {

	private final ConectoresColegio conectores;

	private final PropiedadesRecaudacion propiedades;

	private final List<AdaptadorRecaudacion> formatos;

	public RecaudacionDelColegio(ConectoresColegio conectores, PropiedadesRecaudacion propiedades,
			List<AdaptadorRecaudacion> formatos) {
		this.conectores = conectores;
		this.propiedades = propiedades;
		this.formatos = List.copyOf(formatos);
	}

	private Optional<ConectorVigente> conector() {
		return conectores.vigente(TipoConector.RECAUDACION);
	}

	/** El banco cuyo formato se lee y se exporta. */
	public BancoRecaudacion banco() {
		return conector().map(c -> BancoRecaudacion.valueOf(c.proveedor().name())).orElse(propiedades.banco());
	}

	public ModalidadRecaudacion modalidad() {
		return conector().map(c -> ModalidadRecaudacion.valueOf(c.configuracionDe(ConfiguracionConector.Recaudacion.class)
				.modalidad())).orElse(propiedades.modalidad());
	}

	@Override
	public TipoConector tipo() {
		return TipoConector.RECAUDACION;
	}

	@Override
	public boolean admite(ProveedorConector proveedor) {
		return TipoConector.RECAUDACION.proveedores().contains(proveedor);
	}

	@Override
	public ResultadoPrueba probar(ConectorVigente candidato) {
		BancoRecaudacion banco = BancoRecaudacion.valueOf(candidato.proveedor().name());
		if (formatos.stream().noneMatch(f -> f.banco() == banco)) {
			return ResultadoPrueba.fallida("Todavía no está el formato del banco " + banco.etiqueta() + ": usa el formato "
					+ "genérico hasta tenerlo.");
		}
		return ResultadoPrueba.correcta("El formato " + banco.etiqueta() + " está disponible. El primer archivo del banco "
				+ "se confirma a ciegas, como siempre.");
	}
}
