package pe.edu.virgenmaria.cuentasclaras.privacidad.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.privacidad.config.PropiedadesPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.FiltroAccesos;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AccesosDatosPersonales;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.DatosConPlazoVencido;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.MisDatos;

import java.time.LocalDate;

/**
 * Ley 29733 (sprint 7, tanda 3; sección 13). Sin lógica: delega en los servicios, que exigen el rol.
 * <ul>
 *   <li>{@code GET /privacidad}: aviso de privacidad (público, versión vigente).</li>
 *   <li>{@code GET /familia/mis-datos}: los datos de la familia en sesión (apoderado; nunca recibe un id).</li>
 *   <li>{@code GET /auditoria/accesos}: quién vio datos personales, por persona y fechas (Promotoría).</li>
 *   <li>{@code GET /auditoria/datos-vencidos}: contactos de familias que se fueron sin deuda hace más del plazo
 *       (Promotoría).</li>
 * </ul>
 */
@Controller
public class PrivacidadController {

	private final MisDatos misDatos;

	private final AccesosDatosPersonales accesos;

	private final DatosConPlazoVencido vencidos;

	private final PropiedadesPrivacidad propiedades;

	public PrivacidadController(MisDatos misDatos, AccesosDatosPersonales accesos, DatosConPlazoVencido vencidos,
			PropiedadesPrivacidad propiedades) {
		this.misDatos = misDatos;
		this.accesos = accesos;
		this.vencidos = vencidos;
		this.propiedades = propiedades;
	}

	@GetMapping("/privacidad")
	public String aviso(Model model) {
		model.addAttribute("version", propiedades.versionAviso());
		model.addAttribute("plazoAcceso", propiedades.plazoAccesoDiasHabiles());
		model.addAttribute("plazoOtros", propiedades.plazoOtrosDiasHabiles());
		return "privacidad/aviso";
	}

	@GetMapping("/familia/mis-datos")
	public String misDatos(Model model) {
		model.addAttribute("datos", misDatos.deMiFamilia());
		return "familia/mis-datos";
	}

	@GetMapping("/auditoria/accesos")
	public String accesos(@RequestParam(required = false) Long usuarioId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, Model model) {
		FiltroAccesos filtro = new FiltroAccesos(usuarioId, desde, hasta);
		model.addAttribute("filtro", filtro);
		model.addAttribute("personal", accesos.personal());
		try {
			model.addAttribute("accesos", accesos.consultar(filtro));
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
		}
		return "auditoria/accesos";
	}

	@GetMapping("/auditoria/datos-vencidos")
	public String datosVencidos(Model model) {
		model.addAttribute("familias", vencidos.familias());
		model.addAttribute("meses", propiedades.contactosMeses());
		return "auditoria/datos-vencidos";
	}

	/** Una fecha o un filtro mal escrito en la URL muestra un aviso, no una página de error. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public String filtroMalEscrito(Model model) {
		model.addAttribute("filtro", new FiltroAccesos(null, null, null));
		model.addAttribute("personal", accesos.personal());
		model.addAttribute("error", "Revisa los filtros: usa el calendario para elegir las fechas.");
		return "auditoria/accesos";
	}
}
