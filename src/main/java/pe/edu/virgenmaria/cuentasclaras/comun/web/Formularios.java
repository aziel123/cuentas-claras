package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.springframework.beans.NotReadablePropertyException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.Arrays;

/**
 * Ayudas de presentación para los controladores (sin reglas de negocio): mostrar un error junto a su campo y decidir
 * si se muestran los botones de edición. Los permisos de verdad los exige cada servicio con {@code @PreAuthorize}.
 */
public final class Formularios {

	private Formularios() {
	}

	/**
	 * Muestra el mensaje debajo del campo indicado. Si el campo no está en el formulario, lo muestra arriba
	 * (atributo {@code error}).
	 */
	public static void errorEnCampo(BindingResult validacion, Model model, String campo, String mensaje) {
		if (campo != null) {
			try {
				validacion.rejectValue(campo, "invalido", mensaje);
				return;
			}
			catch (NotReadablePropertyException | IllegalArgumentException e) {
				// El campo no existe en este formulario: va arriba.
			}
		}
		model.addAttribute("error", mensaje);
	}

	/** El primer error del formulario, sin textos técnicos de conversión. */
	public static String primerError(BindingResult validacion) {
		ObjectError error = validacion.getAllErrors().getFirst();
		boolean conversion = error.getCodes() != null
				&& Arrays.stream(error.getCodes()).anyMatch(c -> c.startsWith("typeMismatch"));
		return conversion ? "Revisa los datos: alguno no tiene el formato esperado." : error.getDefaultMessage();
	}

	/** Dirección y Administración ven los botones para registrar y corregir (Promotoría solo consulta). */
	public static boolean puedeEditar(UsuarioAutenticado sesion) {
		return sesion != null && (sesion.roles().contains(Rol.DIRECTOR) || sesion.roles().contains(Rol.ADMINISTRACION));
	}
}
