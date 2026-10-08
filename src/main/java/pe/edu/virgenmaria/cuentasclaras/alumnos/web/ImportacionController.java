package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.PlantillaImportacionAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ResultadoImportacion;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ServicioImportacionAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.VistaPreviaImportacion;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.AnioEscolarVista;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.PropiedadesExcel;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValidadorArchivoXlsx;

import java.io.IOException;
import java.util.UUID;

/**
 * Asistente de importación desde Excel en 3 pasos: subir → revisar → confirmar. Sin lógica: delega en
 * {@link ServicioImportacionAlumnos}. La revisión se guarda SOLO en la sesión de quien subió el archivo (clave
 * {@value #CLAVE_SESION}) y se borra al confirmar o cancelar.
 */
@Controller
@RequestMapping("/alumnos")
public class ImportacionController {

	public static final String CLAVE_SESION = "importacionPendiente";

	private static final String RUTA_SUBIR = "/alumnos/importar";

	private static final String VISTA_SUBIR = "alumnos/importar-subir";

	private static final MediaType XLSX = MediaType.parseMediaType(
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ServicioImportacionAlumnos servicio;

	private final ServicioEstructura estructura;

	private final PropiedadesExcel propiedades;

	public ImportacionController(ServicioImportacionAlumnos servicio, ServicioEstructura estructura,
			PropiedadesExcel propiedades) {
		this.servicio = servicio;
		this.estructura = estructura;
		this.propiedades = propiedades;
	}

	@GetMapping("/importar")
	public String subir(Model model) {
		// El historial exige el permiso de importar: Promotoría y Caja reciben 403 aquí.
		model.addAttribute("recientes", servicio.historial().stream().limit(3).toList());
		prepararSubir(model);
		return VISTA_SUBIR;
	}

	@GetMapping("/importar/plantilla")
	public ResponseEntity<byte[]> plantilla() {
		return ResponseEntity.ok()
				.contentType(XLSX)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(PlantillaImportacionAlumnos.NOMBRE_ARCHIVO).build().toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(servicio.plantilla());
	}

	@PostMapping("/importar")
	public String recibir(@RequestParam(required = false) Long anioId,
			@RequestParam(name = "archivo", required = false) MultipartFile archivo, HttpSession sesion, Model model)
			throws IOException {
		sesion.removeAttribute(CLAVE_SESION);
		if (archivo == null || archivo.isEmpty()) {
			return conError(model, "Elige el archivo .xlsx que quieres subir.");
		}
		byte[] contenido = archivo.getSize() > propiedades.maxBytes().toBytes()
				? ValidadorArchivoXlsx.leerConLimite(archivo.getInputStream(), propiedades.maxBytes().toBytes())
				: archivo.getBytes();
		try {
			VistaPreviaImportacion previa = servicio.previsualizar(anioId, archivo.getOriginalFilename(), contenido,
					archivo.getSize());
			sesion.setAttribute(CLAVE_SESION, previa);
		}
		catch (ReglaNegocioException e) {
			return conError(model, e.getMessage());
		}
		return "redirect:/alumnos/importar/revision";
	}

	@GetMapping("/importar/revision")
	public String revision(HttpSession sesion, Model model) {
		if (!(sesion.getAttribute(CLAVE_SESION) instanceof VistaPreviaImportacion previa)) {
			return "redirect:" + RUTA_SUBIR;
		}
		model.addAttribute("previa", previa);
		return "alumnos/importar-revisar";
	}

	@PostMapping("/importar/confirmar")
	public String confirmar(@RequestParam(required = false) String token, HttpSession sesion, Model model,
			RedirectAttributes avisos) {
		VistaPreviaImportacion previa = sesion.getAttribute(CLAVE_SESION) instanceof VistaPreviaImportacion v ? v : null;
		try {
			ResultadoImportacion resultado = servicio.confirmar(previa, uuid(token));
			sesion.removeAttribute(CLAVE_SESION);
			model.addAttribute("resultado", resultado);
			return "alumnos/importar-resultado";
		}
		catch (ReglaNegocioException e) {
			sesion.removeAttribute(CLAVE_SESION);
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:" + RUTA_SUBIR;
		}
	}

	@PostMapping("/importar/cancelar")
	public String cancelar(HttpSession sesion, RedirectAttributes avisos) {
		sesion.removeAttribute(CLAVE_SESION);
		avisos.addFlashAttribute("exito", "Listo: cancelaste la importación. No se guardó nada.");
		return "redirect:" + RUTA_SUBIR;
	}

	@GetMapping("/importaciones")
	public String historial(Model model) {
		model.addAttribute("importaciones", servicio.historial());
		return "alumnos/importaciones";
	}

	private String conError(Model model, String mensaje) {
		model.addAttribute("error", mensaje);
		prepararSubir(model);
		return VISTA_SUBIR;
	}

	private void prepararSubir(Model model) {
		model.addAttribute("anios", estructura.listarAnios().stream().filter(a -> !a.cerrado()).toList());
		model.addAttribute("anioPorDefecto", estructura.listarAnios().stream().filter(AnioEscolarVista::enCurso)
				.map(AnioEscolarVista::id).findFirst().orElse(null));
		model.addAttribute("maximo", megas());
		model.addAttribute("maxFilas", String.format("%,d", propiedades.maxFilas()));
	}

	private String megas() {
		return propiedades.maxBytes().toMegabytes() + " MB";
	}

	private static UUID uuid(String texto) {
		try {
			return texto == null ? null : UUID.fromString(texto);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}
}
