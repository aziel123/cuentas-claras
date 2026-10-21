package pe.edu.virgenmaria.cuentasclaras.aprobaciones.web;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.web.ContadorMenu;

/** Rediseño de la interfaz: las solicitudes pendientes junto a «Aprobaciones» en el menú (Promotoría y Dirección). */
@Component
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
		return bandeja.contarPendientes();
	}

	@Override
	public String descripcion(long cantidad) {
		return cantidad == 1 ? "1 por aprobar" : cantidad + " por aprobar";
	}
}
