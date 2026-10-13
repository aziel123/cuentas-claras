package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVista;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.VistaConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sprint 8, tanda 2: lo que muestra {@code /conectores} (Promotoría, Dirección y Administración). Nunca un secreto: solo
 * su pista (los 4 últimos caracteres), y la cuenta y el CCI enmascarados.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
@Transactional(readOnly = true)
public class ConsultaConectores {

	private final ConectorColegioRepository conectores;

	private final SecretosConectores secretos;

	private final ConectoresColegio colegio;

	public ConsultaConectores(ConectorColegioRepository conectores, SecretosConectores secretos,
			ConectoresColegio colegio) {
		this.conectores = conectores;
		this.secretos = secretos;
		this.colegio = colegio;
	}

	public VistaConectores vista() {
		boolean puedeProponer = tieneRol("ROLE_PROMOTOR") || tieneRol("ROLE_ADMINISTRACION");
		List<ConectorColegio> todos = conectores.findAllByOrderByIdDesc();
		List<VistaConectores.SeccionTipo> secciones = new ArrayList<>();
		for (TipoConector tipo : TipoConector.values()) {
			List<ConectorColegio> delTipo = todos.stream().filter(c -> c.getTipo() == tipo).toList();
			ConectorVista vigente = delTipo.stream().filter(c -> c.getEstado() == EstadoConector.VIGENTE).findFirst()
					.map(c -> vista(c, false)).orElse(null);
			List<ConectorVista> propuestos = delTipo.stream().filter(c -> c.getEstado() == EstadoConector.PROPUESTO)
					.map(c -> vista(c, puedeProponer)).toList();
			List<ConectorVista> historial = delTipo.stream().filter(c -> c.getEstado() == EstadoConector.REEMPLAZADO
					|| c.getEstado() == EstadoConector.RECHAZADO).limit(10).map(c -> vista(c, false)).toList();
			secciones.add(new VistaConectores.SeccionTipo(tipo.name(), tipo.etiqueta(), tipo.descripcion(),
					tipo.mueveDinero(), tipo.proveedores().stream().map(ProveedorConector::name).toList(), vigente,
					propuestos, historial, sinConector(tipo)));
		}
		return new VistaConectores(List.copyOf(secciones), puedeProponer, secretos.disponible(), colegio.compartida());
	}

	/** El conector (de este colegio) para la ruta de prueba; el de otro colegio no existe (404). */
	public boolean existe(Long id) {
		return conectores.findById(id).isPresent();
	}

	private String sinConector(TipoConector tipo) {
		boolean compartida = colegio.compartida();
		return switch (tipo) {
			case OSE -> compartida ? "Sin conector: las boletas se emiten con el comprobante simulado (sin valor ante SUNAT)."
					: "Sin conector: se usa el emisor configurado en la instalación.";
			case PASARELA -> compartida ? "Sin conector: el colegio no tiene pago en línea."
					: "Sin conector: se usa la pasarela configurada en la instalación (o no hay pago en línea).";
			case CUENTA_BANCARIA -> "Sin conector: el cierre de caja no sabe en qué cuenta se deposita el efectivo.";
			case RECAUDACION -> "Sin conector: la recaudación usa el formato genérico.";
		};
	}

	private static ConectorVista vista(ConectorColegio c, boolean puedeProbar) {
		ConfiguracionConector configuracion = ConfiguracionConector.deJson(c.getTipo(), c.getConfiguracion());
		return new ConectorVista(c.getId(), c.getTipo().name(), c.getTipo().etiqueta(), c.getProveedor().etiqueta(),
				c.getModo().etiqueta(), configuracion.resumen(), c.getSecretoPista(), c.getEstado().name(),
				c.getEstado().etiqueta(), c.getEstado().variante(), c.getPropuestoPor(), c.getCreadoEn(), c.getResueltoPor(),
				c.getResueltoEn(), c.getPruebaOk(), c.getPruebaDetalle(), c.getSolicitudId(),
				puedeProbar && c.getEstado() == EstadoConector.PROPUESTO);
	}

	private static boolean tieneRol(String rol) {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && autenticacion.getAuthorities().stream().anyMatch(a -> rol.equals(a.getAuthority()));
	}

	static List<String> nombres(Enum<?>[] valores) {
		return Arrays.stream(valores).map(Enum::name).toList();
	}
}
