package pe.edu.virgenmaria.cuentasclaras.aprobaciones.web;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.web.ContadorMenu;
import pe.edu.virgenmaria.cuentasclaras.seguridad.web.FuenteContador;

/**
 * Rediseño de la interfaz: junto a «Aprobaciones» en el menú, las solicitudes pendientes que quien está en sesión puede
 * resolver (no cuenta las que pidió ni en las que participó, igual que la bandeja). Promotoría y Dirección.
 */
@Component
@FuenteContador(servicio = BandejaAprobaciones.class, metodo = "contarPorResolver")
public class ContadorAprobaciones implements ContadorMenu {

	private final BandejaAprobaciones bandeja;

	public ContadorAprobaciones(BandejaAprobaciones bandeja) {
		this.bandeja = bandeja;
	}

	@Override
	public ModuloApp modulo() {
		return ModuloApp.APROBACIONES;
	}

	@Override
	public long contar() {
		return bandeja.contarPorResolver();
	}

	@Override
	public String descripcion(long cantidad) {
		return cantidad == 1 ? "1 por aprobar" : cantidad + " por aprobar";
	}
}
