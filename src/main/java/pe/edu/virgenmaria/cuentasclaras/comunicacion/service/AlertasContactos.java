package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContactoNormal;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alertas de los contactos de los apoderados para «Para revisar» de Promotoría (correcciones del sprint 5), comparando
 * siempre la forma NORMALIZADA ({@link ContactoNormal}: alias de Gmail con «+» o con puntos, celular con o sin 51):
 * <ul>
 *   <li>CRÍTICA (S5-A1): un contacto lleva más de 48 horas sin verificar: esa familia no recibe sus avisos.</li>
 *   <li>CRÍTICA (S5-M5): el mismo celular o correo en dos familias (un empleado que recibe los avisos de varias).</li>
 *   <li>CRÍTICA (S5-M5): un contacto de apoderado igual al de alguien del personal y SIN aprobar (no recibe nada);
 *       ATENCIÓN si otra persona lo aprobó (el personal que también es padre o madre).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
@Transactional(readOnly = true)
public class AlertasContactos implements AlertasRevision {

	static final String MODULO = "Familias";

	/** S5-A1: horas que un contacto puede esperar su verificación antes de la alerta. */
	static final int HORAS_SIN_VERIFICAR = 48;

	private final ApoderadoRepository apoderados;

	private final UsuarioRepository usuarios;

	private final MensajeRepository mensajes;

	private final Clock reloj;

	public AlertasContactos(ApoderadoRepository apoderados, UsuarioRepository usuarios, MensajeRepository mensajes,
			Clock reloj) {
		this.apoderados = apoderados;
		this.usuarios = usuarios;
		this.mensajes = mensajes;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDateTime limite = LocalDateTime.now(reloj).minusHours(HORAS_SIN_VERIFICAR);
		List<Apoderado> activos = apoderados.findByActivoTrueOrderByIdAsc();
		List<AlertaRevision> alertas = new ArrayList<>();

		long sinVerificar = activos.stream().filter(a -> pendienteDesde(a) != null && pendienteDesde(a).isBefore(limite))
				.count();
		if (sinVerificar > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, sinVerificar + " apoderado(s) con un celular o correo "
					+ "sin confirmar por más de " + HORAS_SIN_VERIFICAR + " horas: no reciben los avisos de pago. Llama a la "
					+ "familia y, si no reconoce el contacto, revisa quién lo registró.", "/alumnos"));
		}

		Map<String, Set<Long>> familiasPorContacto = new HashMap<>();
		for (Apoderado a : activos) {
			for (String contacto : new String[] { a.getTelefonoWhatsapp(), a.getCorreo() }) {
				String normal = ContactoNormal.de(contacto);
				if (!normal.isEmpty()) {
					familiasPorContacto.computeIfAbsent(normal, k -> new HashSet<>()).add(a.getFamilia().getId());
				}
			}
		}
		long compartidos = familiasPorContacto.values().stream().filter(f -> f.size() > 1).count();
		if (compartidos > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, compartidos + " celular(es) o correo(s) se repiten en "
					+ "dos o más familias (comparando alias y formatos). Confirma con cada familia que el contacto es suyo.",
					"/alumnos"));
		}

		List<Usuario> personal = usuarios.personalActivo();
		int sinAprobar = 0;
		int aprobados = 0;
		for (Apoderado a : activos) {
			for (boolean whatsapp : new boolean[] { true, false }) {
				String contacto = whatsapp ? a.getTelefonoWhatsapp() : a.getCorreo();
				if (contacto == null || personal.stream().noneMatch(u -> ContactoNormal.iguales(u.getTelefonoWhatsapp(),
						contacto) || ContactoNormal.iguales(u.getCorreo(), contacto))) {
					continue;
				}
				String aprobado = whatsapp ? a.getContactoAprobadoTelefono() : a.getContactoAprobadoCorreo();
				if (contacto.equals(aprobado)) {
					aprobados++;
				}
				else {
					sinAprobar++;
				}
			}
		}
		if (sinAprobar > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, sinAprobar + " contacto(s) de apoderados son del "
					+ "personal y nadie los aprobó: no reciben avisos. Revisa la bandeja de aprobaciones.", "/aprobaciones"));
		}
		if (aprobados > 0) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, aprobados + " contacto(s) de apoderados son también "
					+ "del personal (aprobados por otra persona). Revísalos de vez en cuando con la familia.", "/alumnos"));
		}
		return alertas;
	}

	/** Desde cuándo espera su verificación (el primer enlace enviado a ese contacto, o el último cambio), o null. */
	private LocalDateTime pendienteDesde(Apoderado a) {
		List<String> pendientes = new ArrayList<>();
		if (a.getTelefonoWhatsapp() != null && !a.telefonoVerificado()) {
			pendientes.add(a.getTelefonoWhatsapp());
		}
		if (a.getCorreo() != null && !a.correoVerificado()) {
			pendientes.add(a.getCorreo());
		}
		if (pendientes.isEmpty()) {
			return null;
		}
		return mensajes.findByTipoAndApoderadoIdOrderByIdAsc(TipoMensaje.VERIFICACION_CONTACTO, a.getId()).stream()
				.filter(m -> pendientes.contains(m.getDestino())).map(Mensaje::getCreadoEn).findFirst()
				.orElse(a.getActualizadoEn());
	}
}
