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

	private final pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaExterna externa;

	public ContadorAprobaciones(BandejaAprobaciones bandeja,
			pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaExterna externa) {
		this.bandeja = bandeja;
		this.externa = externa;
	}

	@Override
	public ModuloApp modulo() {
		return ModuloApp.APROBACIONES;
	}

	@Override
	public long contar() {
		// Sprint 11, tanda 1b (F-D5): también lo que se aprueba en otra pantalla, con la misma regla de puedeResolver.
		return bandeja.contarPorResolver() + externa.contarPorResolver();
	}

	@Override
	public String descripcion(long cantidad) {
		return cantidad == 1 ? "1 por aprobar" : cantidad + " por aprobar";
	}
}
