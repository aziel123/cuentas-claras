package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import pe.edu.virgenmaria.cuentasclaras.operacion.log.ContadorErrores.ErrorAgrupado;

import java.time.Instant;
import java.util.List;

/**
 * El estado técnico en un momento (sprint 7, sección 10.1). No cambia el estado público de {@code /actuator/health}: un
 * respaldo atrasado no es «aplicación caída». Lo leen {@code /panel/sistema} y las alertas técnicas. Sin datos
 * personales.
 *
 * @param discoLibre porcentaje libre del disco donde corre la aplicación ({@code null} si no se pudo leer)
 */
public record FotoTecnica(Instant momento, EstadoRespaldo respaldo, boolean tareasActivas, List<EstadoProceso> procesos,
		List<ErrorAgrupado> errores, boolean bitacoraAlDia, Integer discoLibre, boolean baseResponde,
		boolean poolAgotado, boolean alertasEncendidas, String version) {

	public List<EstadoProceso> atrasados() {
		return procesos.stream().filter(EstadoProceso::atrasado).toList();
	}
}
