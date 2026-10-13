package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Sprint 8, tanda 1 (decisión 113): la ficha de un colegio para el operador y su lista de verificación como sí/no y
 * conteos (nunca nombres, documentos, contactos ni montos). Los puntos de la tanda 1 son los que ya se pueden calcular;
 * la lista completa (pensiones, saldo inicial, conectores) llega en la tanda 2.
 *
 * @param titularDestino a dónde se envió el enlace del titular (enmascarado), o {@code null} si todavía no hay titular
 * @param contactoPorAplicar correcciones del sprint 8 (S8-A1): el cambio del contacto del titular que propuso un operador
 *            y debe aplicar OTRO, o {@code null}
 * @param correoControlPorAprobar correcciones del sprint 8 (S8-A2): el correo de control propuesto (enmascarado) que
 *            espera la aprobación de la Promotoría, o {@code null}
 */
public record FichaPlataforma(Long id, String nombre, String razonSocial, String ruc, String direccion,
		String correoInstitucional, Set<Nivel> niveles, EstadoColegio estado, String contratoCodigo, String titularNombre,
		String titularDocumentoEnmascarado, String titularUsuario, String titularDestino, boolean titularActivado,
		boolean conLogo, String correoControl, LocalDateTime creadoEn, String creadoPor, LocalDateTime produccionDesde,
		List<PuntoLista> lista, AltaTitular.ContactoPorAplicar contactoPorAplicar, String correoControlPorAprobar) {

	/** Un punto de la lista de verificación: paso, hecho o pendiente y un conteo (nunca datos personales). */
	public record PuntoLista(String paso, boolean hecho, boolean obligatorio, String conteo) {
	}

	public boolean enPreparacion() {
		return estado == EstadoColegio.PREPARACION;
	}
}
