package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.api.PendienteExterno;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.api.PendientesExternos;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Sprint 11, tanda 1b (F-D5, decisión 209): lo que se aprueba en otra pantalla, junto a la bandeja. Recibe la lista de
 * beans del puerto {@link PendientesExternos} (cada módulo dueño implementa el suyo): {@code aprobaciones} no depende de
 * ellos. No aprueba nada: «Revisar» solo enlaza a la pantalla del módulo.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class BandejaExterna {

	private final List<PendientesExternos> puertos;

	public BandejaExterna(List<PendientesExternos> puertos) {
		this.puertos = List.copyOf(puertos);
	}

	/** Todo lo pendiente fuera de la bandeja, de más antiguo a más nuevo. */
	public List<PendienteExterno> pendientes() {
		String usuario = usuario();
		return puertos.stream().flatMap(p -> p.pendientes(usuario).stream())
				.sorted(Comparator.comparing(PendienteExterno::desde, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())))
				.toList();
	}

	/** Lo que quien está en sesión puede resolver (para «Pendientes»). */
	public List<PendienteExterno> porResolver() {
		return pendientes().stream().filter(PendienteExterno::puedeResolver).toList();
	}

	/** Lo que pidió o en lo que participó: espera a otra persona. */
	public List<PendienteExterno> esperandoAOtraPersona() {
		return pendientes().stream().filter(p -> !p.puedeResolver()).toList();
	}

	/** La suma del contador del menú. */
	public long contarPorResolver() {
		String usuario = usuario();
		// Por el proxy de cada puerto (su transacción de lectura), no por el método por defecto de la interfaz.
		return puertos.stream().mapToLong(p -> p.pendientes(usuario).stream().filter(PendienteExterno::puedeResolver).count())
				.sum();
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}
}
