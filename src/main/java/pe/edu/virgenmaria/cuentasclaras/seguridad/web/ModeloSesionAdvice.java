package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.EstadoDelColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.MarcaColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.marca.Marca;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Monograma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.GrupoMenu;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agrega a toda vista el usuario en sesión y su menú, armado en el servidor desde {@link ModuloApp}
 * (sin {@code thymeleaf-extras-springsecurity}).
 */
@ControllerAdvice
public class ModeloSesionAdvice {

	private final ColegioService colegios;

	private final Marca marca;

	private final ObjectProvider<ContadorMenu> contadores;

	private final ObjectProvider<EstadoDelColegio> estados;

	public ModeloSesionAdvice(ColegioService colegios, Marca marca, ObjectProvider<ContadorMenu> contadores,
			ObjectProvider<EstadoDelColegio> estados) {
		this.colegios = colegios;
		this.marca = marca;
		this.contadores = contadores;
		this.estados = estados;
	}

	/**
	 * Sprint 11, tanda 1 (secciones 3.4 y 3.8): la franja ámbar con la fecha de una baja o una suspensión aprobada (el
	 * personal) o el modo consulta (las familias). Sin datos del motivo.
	 */
	@ModelAttribute("franjaColegio")
	public String franjaColegio(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		EstadoDelColegio estado = estados.getIfAvailable();
		if (usuario == null || estado == null) {
			return null;
		}
		return estado.franja(usuario.colegioId(), esFamilia(usuario)).orElse(null);
	}

	/** Con pocas opciones (Caja, Docente, la familia) todos los grupos van abiertos: no hay nada que esconder. */
	static final int OPCIONES_PARA_PLEGAR = 8;

	@ModelAttribute("usuarioActual")
	public SesionVista usuarioActual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		if (usuario == null) {
			return null;
		}
		String rol = usuario.roles().stream().sorted().map(Rol::etiqueta).collect(Collectors.joining(" · "));
		return new SesionVista(usuario.nombreCompleto(), usuario.getUsername(), rol,
				colegios.nombreDe(usuario.colegioId()), usuario.debeCambiarClave());
	}

	/**
	 * Rediseño (portal de familias en celular): si quien está en sesión es solo apoderado. Con eso el armazón muestra la
	 * barra inferior (Inicio, Estado de cuenta, Pagar y Avisos). No da permisos: solo decide qué navegación se ve.
	 */
	@ModelAttribute("esFamilia")
	public boolean esFamilia(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return usuario != null && usuario.roles().size() == 1 && usuario.roles().contains(Rol.APODERADO);
	}

	@ModelAttribute("menu")
	public List<ElementoMenu> menu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		if (usuario == null) {
			return List.of();
		}
		return ModuloApp.para(usuario.getAuthorities()).stream().map(ElementoMenu::de).toList();
	}

	/**
	 * Rediseño de la interfaz (hallazgo 3): el mismo menú agrupado en secciones plegables ({@link GrupoMenu}). Solo los
	 * grupos con algún módulo del rol; abierto el grupo de la página actual y el primero. Los contadores salen de los
	 * {@link ContadorMenu} de los módulos que ya están en el menú del rol.
	 */
	@ModelAttribute("menuGrupos")
	public List<GrupoVista> menuGrupos(@AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
		if (usuario == null) {
			return List.of();
		}
		List<ModuloApp> visibles = ModuloApp.para(usuario.getAuthorities());
		Set<ModuloApp> enMenu = Set.copyOf(visibles);
		Map<ModuloApp, ElementoMenu> conContador = new java.util.EnumMap<>(ModuloApp.class);
		contadores.orderedStream().filter(c -> enMenu.contains(c.modulo()) && c.modulo().disponible()).forEach(c -> {
			long n = c.contar();
			if (n > 0) {
				conContador.put(c.modulo(), ElementoMenu.de(c.modulo()).conContador(n, c.descripcion(n), c.urgente()));
			}
		});
		String ruta = request.getRequestURI();
		boolean todoAbierto = visibles.size() <= OPCIONES_PARA_PLEGAR;
		List<GrupoVista> grupos = new ArrayList<>();
		for (GrupoMenu grupo : GrupoMenu.values()) {
			List<ElementoMenu> elementos = grupo.modulos().stream().filter(enMenu::contains)
					.map(m -> conContador.getOrDefault(m, ElementoMenu.de(m))).toList();
			if (elementos.isEmpty()) {
				continue;
			}
			boolean actual = elementos.stream().anyMatch(e -> e.disponible()
					&& (ruta.equals(e.ruta()) || ruta.startsWith(e.ruta() + "/")));
			long pendientes = elementos.stream().filter(ElementoMenu::urgente).mapToLong(ElementoMenu::contador).sum();
			grupos.add(new GrupoVista(grupo.name(), grupo.etiqueta(), todoAbierto || actual || grupos.isEmpty()
					|| pendientes > 0, elementos, pendientes));
		}
		return grupos;
	}

	/**
	 * Las iniciales de la barra cuando no hay logo: del colegio de la sesión o, sin sesión (ingreso, errores), del producto.
	 * Auditoría del PR #19 (hallazgo 3): se calculan aquí, con salida por defecto si el nombre está vacío.
	 */
	@ModelAttribute("monograma")
	public String monograma(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		String colegio = usuario == null ? null : colegios.nombreDe(usuario.colegioId());
		return Monograma.de(colegio, marca.nombre());
	}

	@ModelAttribute("rutaActual")
	public String rutaActual(HttpServletRequest request) {
		return request.getRequestURI();
	}

	/**
	 * Sprint 8, tanda 1 (decisión 125): las páginas públicas (ingreso, errores) llevan la marca del producto, no el nombre
	 * de un colegio: la lista de clientes no se publica y el ingreso es uno para todos.
	 */
	@ModelAttribute("nombreInstitucional")
	public String nombreInstitucional() {
		return marca.nombre();
	}

	/**
	 * Sprint 8, tanda 1 (decisión 123): la marca del colegio de la sesión (nombre, razón social, RUC, dirección y si tiene
	 * logo), para la cabecera y las pantallas que la muestran. {@code null} sin sesión: las páginas públicas no nombran a
	 * ningún colegio.
	 */
	@ModelAttribute("marcaColegio")
	public MarcaColegio marcaColegio(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		if (usuario == null) {
			return null;
		}
		return colegios.marcaDe(usuario.colegioId()).orElse(null);
	}
}
