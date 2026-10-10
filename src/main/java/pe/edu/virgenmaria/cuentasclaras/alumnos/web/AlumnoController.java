package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ActualizarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.BusquedaAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarResponsableRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.EdicionAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatriculaResultado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatricularRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.OpcionesRegistro;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistrarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistroResultado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RetirarAlumnoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatoInvalidoException;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioMatriculas;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * Alumnos: lista con búsqueda, ficha, registro, corrección, matrícula, cambio de sección y de responsable de pago y
 * retiro. Sin lógica: valida, delega en los servicios y muestra el resultado. Los permisos de escritura los exigen
 * los servicios (Dirección y Administración).
 */
@Controller
@RequestMapping("/alumnos")
public class AlumnoController {

	private static final String VISTA_LISTA = "alumnos/lista";

	private static final String VISTA_FORMULARIO = "alumnos/formulario";

	private static final String VISTA_EDITAR = "alumnos/editar";

	private final ServicioAlumnos alumnos;

	private final ServicioMatriculas matriculas;

	private final ServicioEstructura estructura;

	public AlumnoController(ServicioAlumnos alumnos, ServicioMatriculas matriculas, ServicioEstructura estructura) {
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.estructura = estructura;
	}

	@GetMapping
	@RegistraAcceso(TipoAcceso.BUSQUEDA)
	public String lista(@RequestParam(required = false) String q, @RequestParam(required = false) Long anio,
			@RequestParam(required = false) Long seccion, @RequestParam(required = false) EstadoAlumno estado,
			@RequestParam(defaultValue = "0") int pagina, @AuthenticationPrincipal UsuarioAutenticado sesion,
			Model model) {
		BusquedaAlumnos busqueda = new BusquedaAlumnos(q, anio, seccion, estado);
		prepararLista(model, busqueda, sesion);
		var resultado = alumnos.buscar(busqueda, pagina);
		model.addAttribute("alumnos", resultado);
		AccesoMostrado.filas(resultado.getNumberOfElements());
		return VISTA_LISTA;
	}

	/** Un filtro mal escrito en la URL muestra la lista completa con un aviso, no una página de error. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public String filtroMalEscrito(@AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		prepararLista(model, BusquedaAlumnos.todos(), sesion);
		model.addAttribute("alumnos", alumnos.buscar(BusquedaAlumnos.todos(), 0));
		model.addAttribute("error", "Revisa los filtros: elige el año, la sección y el estado de las listas.");
		return VISTA_LISTA;
	}

	@GetMapping("/nuevo")
	public String formulario(@RequestParam(required = false) Long familia, Model model) {
		OpcionesRegistro opciones = alumnos.prepararRegistro(familia);
		model.addAttribute("solicitud", RegistrarAlumnoRequest.vacio(
				opciones.apoderadosFamilia().isEmpty() ? null : opciones.apoderadosFamilia().getFirst().id()));
		prepararFormulario(model, opciones);
		return VISTA_FORMULARIO;
	}

	@PostMapping("/nuevo")
	public String registrar(@RequestParam(required = false) Long familia,
			@Valid @ModelAttribute("solicitud") RegistrarAlumnoRequest solicitud, BindingResult validacion, Model model,
			RedirectAttributes avisos) {
		// Primero las listas: también exige el permiso de registrar (Promotoría recibe 403 aquí).
		prepararFormulario(model, alumnos.prepararRegistro(familia));
		if (validacion.hasErrors()) {
			return VISTA_FORMULARIO;
		}
		try {
			RegistroResultado resultado = alumnos.registrar(solicitud);
			avisos.addFlashAttribute("exito", "Listo: registraste a " + solicitud.nombres().strip() + " "
					+ solicitud.apellidoPaterno().strip() + (solicitud.seccionId() != null ? " y quedó matriculado." : "."));
			avisos.addFlashAttribute("advertencia", resultado.advertencia());
			return "redirect:/alumnos/" + resultado.alumnoId();
		}
		catch (DatoInvalidoException e) {
			Formularios.errorEnCampo(validacion, model, e.campo(), e.getMessage());
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
		}
		return VISTA_FORMULARIO;
	}

	@GetMapping("/{id:\\d+}")
	@RegistraAcceso(TipoAcceso.FICHA_ALUMNO)
	public String ficha(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		var ficha = alumnos.obtenerFicha(id);
		model.addAttribute("ficha", ficha);
		AccesoMostrado.alumno(id, ficha.cabecera().familiaId());
		model.addAttribute("puedeEditar", Formularios.puedeEditar(sesion));
		return "alumnos/ficha";
	}

	@GetMapping("/{id:\\d+}/editar")
	@RegistraAcceso(TipoAcceso.FICHA_ALUMNO)
	public String editar(@PathVariable Long id, Model model) {
		EdicionAlumno edicion = alumnos.datosParaEditar(id);
		AccesoMostrado.alumno(id, edicion.cabecera().familiaId());
		model.addAttribute("cabecera", edicion.cabecera());
		model.addAttribute("solicitud", edicion.datos());
		model.addAttribute("tiposDocumento", TipoDocumento.values());
		return VISTA_EDITAR;
	}

	@PostMapping("/{id:\\d+}/editar")
	public String actualizar(@PathVariable Long id, @Valid @ModelAttribute("solicitud") ActualizarAlumnoRequest solicitud,
			BindingResult validacion, Model model, RedirectAttributes avisos) {
		model.addAttribute("cabecera", alumnos.datosParaEditar(id).cabecera());
		model.addAttribute("tiposDocumento", TipoDocumento.values());
		if (validacion.hasErrors()) {
			return VISTA_EDITAR;
		}
		try {
			alumnos.actualizar(id, solicitud);
			avisos.addFlashAttribute("exito", "Listo: se corrigieron los datos. El cambio quedó en la bitácora.");
			return "redirect:/alumnos/" + id;
		}
		catch (DatoInvalidoException e) {
			Formularios.errorEnCampo(validacion, model, e.campo(), e.getMessage());
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
		}
		return VISTA_EDITAR;
	}

	@PostMapping("/{id:\\d+}/matricula")
	public String matricular(@PathVariable Long id, @Valid MatricularRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return redirigirAFicha(id);
		}
		try {
			MatriculaResultado resultado = matriculas.matricular(id, solicitud);
			avisos.addFlashAttribute("exito", "Listo: el alumno quedó matriculado.");
			avisos.addFlashAttribute("advertencia", resultado.advertencia());
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return redirigirAFicha(id);
	}

	@PostMapping("/matriculas/{id:\\d+}/seccion")
	public String cambiarSeccion(@PathVariable Long id, @RequestParam Long alumnoId, @Valid CambiarSeccionRequest solicitud,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return redirigirAFicha(alumnoId);
		}
		try {
			Long alumno = matriculas.cambiarSeccion(id, solicitud);
			avisos.addFlashAttribute("exito", "Listo: el alumno cambió de sección.");
			return redirigirAFicha(alumno);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return redirigirAFicha(alumnoId);
		}
	}

	@PostMapping("/{id:\\d+}/responsable")
	public String cambiarResponsable(@PathVariable Long id, @Valid CambiarResponsableRequest solicitud,
			BindingResult validacion, RedirectAttributes avisos) {
		return ejecutar(id, validacion, avisos, () -> alumnos.cambiarResponsablePago(id, solicitud),
				"Listo: se pidió el cambio de responsable de pago. Lo aprueba otra persona de Promotoría o Dirección en "
						+ "Aprobaciones; hasta entonces se cobra al responsable actual.");
	}

	@PostMapping("/{id:\\d+}/retirar")
	public String retirar(@PathVariable Long id, @Valid RetirarAlumnoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		return ejecutar(id, validacion, avisos, () -> alumnos.retirar(id, solicitud),
				"Listo: se pidió el retiro. Lo aprueba otra persona de Promotoría o Dirección en Aprobaciones; "
						+ "hasta entonces el alumno sigue activo.");
	}

	private static String ejecutar(Long id, BindingResult validacion, RedirectAttributes avisos, Runnable accion,
			String exito) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return redirigirAFicha(id);
		}
		try {
			accion.run();
			avisos.addFlashAttribute("exito", exito);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return redirigirAFicha(id);
	}

	private void prepararLista(Model model, BusquedaAlumnos busqueda, UsuarioAutenticado sesion) {
		model.addAttribute("busqueda", busqueda);
		model.addAttribute("anios", estructura.listarAnios());
		model.addAttribute("secciones", estructura.listarSecciones());
		model.addAttribute("estados", EstadoAlumno.values());
		model.addAttribute("puedeEditar", Formularios.puedeEditar(sesion));
	}

	private static void prepararFormulario(Model model, OpcionesRegistro opciones) {
		model.addAttribute("opciones", opciones);
		model.addAttribute("tiposDocumento", TipoDocumento.values());
		model.addAttribute("parentescos", Parentesco.values());
	}

	private static String redirigirAFicha(Long id) {
		return "redirect:/alumnos/" + id;
	}
}
