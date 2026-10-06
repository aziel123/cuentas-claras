package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.MovimientoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.MovimientoAbierto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.ObjetoAbierto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.Propuesta;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Corre las {@link ReglasEmparejamiento} sobre los movimientos sin pareja y lo que debía verse en el banco, y guarda
 * cada pareja como partida PROPUESTA (las EXACTAS las confirma el sistema al confirmarse el extracto; las SUGERIDAS, una
 * persona). Corre dentro de la transacción de quien llama: al registrar un extracto (Administración) o en la
 * conciliación automática ({@code sistema.conciliacion}). Sin {@code @PreAuthorize}: solo lo usan las clases de
 * {@code conciliacion} (regla ArchUnit).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class Emparejador {

	/** Días antes y después de los movimientos en que se buscan objetos (las ventanas son más cortas). */
	static final int MARGEN_DIAS = 7;

	private final MovimientoBancarioRepository movimientos;

	private final PartidaConciliacionRepository partidas;

	private final ObjetosConciliables objetos;

	private final PropiedadesConciliacion propiedades;

	public Emparejador(MovimientoBancarioRepository movimientos, PartidaConciliacionRepository partidas,
			ObjetosConciliables objetos, PropiedadesConciliacion propiedades) {
		this.movimientos = movimientos;
		this.partidas = partidas;
		this.objetos = objetos;
		this.propiedades = propiedades;
	}

	/** Propone parejas para los movimientos sin pareja de los extractos vigentes desde esa fecha. @return cuántas */
	public int proponerDesde(LocalDate desde) {
		return proponer(movimientos.sinParejaDesde(desde));
	}

	/** Propone parejas para esos movimientos (ya guardados y sin partida vigente). @return cuántas */
	public int proponer(List<MovimientoBancario> sinPareja) {
		if (sinPareja.isEmpty()) {
			return 0;
		}
		LocalDate primero = sinPareja.stream().map(MovimientoBancario::getFecha).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate ultimo = sinPareja.stream().map(MovimientoBancario::getFecha).max(Comparator.naturalOrder()).orElseThrow();
		List<ObjetoAbierto> abiertos = objetos.abiertos(primero.minusDays(MARGEN_DIAS), ultimo.plusDays(MARGEN_DIAS));
		if (abiertos.isEmpty()) {
			return 0;
		}
		Set<String> descartadas = partidas.findByEstadoAndMovimientoIdIn(EstadoPartida.DESCARTADA,
				sinPareja.stream().map(MovimientoBancario::getId).toList()).stream()
				.map(p -> p.getMovimiento().getId() + "|" + p.getObjetoTipo().clave(p.objetoId()))
				.collect(Collectors.toSet());
		Map<Long, MovimientoBancario> porId = sinPareja.stream()
				.collect(Collectors.toMap(MovimientoBancario::getId, Function.identity()));
		List<Propuesta> propuestas = ReglasEmparejamiento.proponer(sinPareja.stream().map(Emparejador::abierto).toList(),
				abiertos, parametros(descartadas)).propuestas();
		for (Propuesta p : propuestas) {
			partidas.save(PartidaConciliacion.proponer(porId.get(p.movimiento().id()), p.objeto().tipo(), p.objeto().id(),
					p.objeto().monto(), p.regla(), null));
		}
		return propuestas.size();
	}

	ReglasEmparejamiento.Parametros parametros(Set<String> descartadas) {
		return new ReglasEmparejamiento.Parametros(propiedades.diasToleranciaFecha(), propiedades.toleranciaMontoLiquidacion(),
				propiedades.patronAbonoRecaudacion(), descartadas);
	}

	static MovimientoAbierto abierto(MovimientoBancario m) {
		return new MovimientoAbierto(m.getId(), m.getFecha(), m.getTipo(), m.getMonto(), m.getNumeroOperacion(),
				m.getDescripcion(), m.getReferencia());
	}
}
