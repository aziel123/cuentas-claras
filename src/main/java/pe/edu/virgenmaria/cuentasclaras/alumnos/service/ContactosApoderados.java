package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContactoNormal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ContactosDeFamilias;

/** Sprint 6, tanda 2: los celulares y correos de los apoderados activos del colegio actual, comparados normalizados. */
@Component
@Transactional(readOnly = true)
public class ContactosApoderados implements ContactosDeFamilias {

	private final ApoderadoRepository apoderados;

	public ContactosApoderados(ApoderadoRepository apoderados) {
		this.apoderados = apoderados;
	}

	@Override
	public boolean esDeUnApoderado(String contacto) {
		if (ContactoNormal.de(contacto).isEmpty()) {
			return false;
		}
		for (Apoderado a : apoderados.findByActivoTrueOrderByIdAsc()) {
			if (ContactoNormal.iguales(a.getTelefonoWhatsapp(), contacto) || ContactoNormal.iguales(a.getCorreo(), contacto)) {
				return true;
			}
		}
		return false;
	}
}
