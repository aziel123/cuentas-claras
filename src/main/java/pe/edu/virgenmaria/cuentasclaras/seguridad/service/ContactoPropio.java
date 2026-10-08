package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

/**
 * Sprint 6, tanda 2 (P6): el contacto nuevo de alguien del personal no es de un apoderado ni de OTRA persona del personal
 * (comparado normalizado: alias y formatos). Lo exigen la solicitud y, otra vez, su aplicación. No exige rol: lo usan
 * {@link ServicioContactoPersonal} (que ya lo exigió) y {@link ManejadorContactoPersonal} (dentro de la aprobación).
 */
@Component
@Transactional(readOnly = true)
public class ContactoPropio {

	private final UsuarioRepository usuarios;

	private final ContactosDeFamilias familias;

	public ContactoPropio(UsuarioRepository usuarios, ContactosDeFamilias familias) {
		this.usuarios = usuarios;
		this.familias = familias;
	}

	public void exigir(Usuario usuario, String contacto, String que) {
		if (contacto == null) {
			return;
		}
		if (familias.esDeUnApoderado(contacto)) {
			throw new ReglaNegocioException("Ese " + que + " es de un apoderado del colegio: el personal recibe la huella, "
					+ "el resumen y las alertas en un contacto propio.");
		}
		usuarios.quienTieneElContacto(contacto).filter(otro -> !otro.getId().equals(usuario.getId())).ifPresent(otro -> {
			throw new ReglaNegocioException("Ese " + que + " ya es de otra persona del personal.");
		});
	}
}
