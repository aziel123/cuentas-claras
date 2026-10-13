package pe.edu.virgenmaria.cuentasclaras.conectores.dto;

import java.util.List;

/**
 * Sprint 8, tanda 2: los conectores del colegio por tipo (el vigente, los propuestos y el historial), si quien mira
 * puede proponer (Promotoría o Administración) y si hay clave para guardar secretos.
 *
 * @param porTipo       una sección por tipo de conector, en el orden de {@code TipoConector}
 * @param puedeProponer Promotoría o Administración
 * @param conClave      la instalación tiene {@code CC_CLAVE_CONECTORES} (sin ella no se guarda un token ni una llave)
 * @param compartida    instalación COMPARTIDA (sin conector, el OSE es el simulado y no hay pago en línea)
 */
public record VistaConectores(List<SeccionTipo> porTipo, boolean puedeProponer, boolean conClave, boolean compartida) {

	/** Un tipo de conector: su vigente (o {@code null}), lo que espera aprobación y lo anterior. */
	public record SeccionTipo(String tipo, String etiqueta, String descripcion, boolean mueveDinero,
			List<String> proveedores, ConectorVista vigente, List<ConectorVista> propuestos, List<ConectorVista> historial,
			String sinConector) {
	}
}
