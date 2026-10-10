package pe.edu.virgenmaria.cuentasclaras.privacidad.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.DiasHabiles;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.familias.model.AvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.model.EstadoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.repository.AvisoFamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.privacidad.config.PropiedadesPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.privacidad.repository.AccesoDatoPersonalRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Alertas de la Ley 29733 en «Para revisar» de Promotoría (sprint 7, tanda 3; secciones 8.2 y 8.3):
 * <ul>
 *   <li>ATENCIÓN: una persona vio hoy más fichas de familias o alumnos que el umbral (decisión 96: 50). Una cajera que
 *       copia los contactos de las familias se nota el mismo día.</li>
 *   <li>Pedidos sobre datos personales sin atender: ATENCIÓN a los 7 días hábiles y CRÍTICA (también al celular) si pasó
 *       el último día del plazo.</li>
 * </ul>
 * Los textos no llevan el nombre de la familia ni sus datos: solo el número del aviso, el nombre de usuario de la persona
 * del personal y fechas.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
@Transactional(readOnly = true)
public class AlertasPrivacidad implements AlertasRevision {

	static final String MODULO = "Datos personales";

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final List<TipoAcceso> FICHAS = Arrays.stream(TipoAcceso.values()).filter(TipoAcceso::ficha).toList();

	private final AccesoDatoPersonalRepository accesos;

	private final AvisoFamiliaRepository avisos;

	private final UsuarioRepository usuarios;

	private final DiasHabiles calendario;

	private final PropiedadesPrivacidad propiedades;

	private final Clock reloj;

	public AlertasPrivacidad(AccesoDatoPersonalRepository accesos, AvisoFamiliaRepository avisos,
			UsuarioRepository usuarios, DiasHabiles calendario, PropiedadesPrivacidad propiedades, Clock reloj) {
		this.accesos = accesos;
		this.avisos = avisos;
		this.usuarios = usuarios;
		this.calendario = calendario;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDate hoy = LocalDate.now(reloj);
		List<AlertaRevision> alertas = new ArrayList<>();
		for (Object[] fila : accesos.personasConMasFichas(hoy.atStartOfDay(), FICHAS, propiedades.accesosUmbralDiario())) {
			Long usuarioId = (Long) fila[0];
			long fichas = ((Number) fila[1]).longValue();
			String persona = usuarios.findById(usuarioId).map(Usuario::getNombreUsuario).orElse("una persona");
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, persona + " vio hoy " + fichas + " fichas de familias "
					+ "o alumnos (el límite de un día normal es " + propiedades.accesosUmbralDiario() + "). Revisa qué "
					+ "consultó y por qué.", "/auditoria/accesos?usuarioId=" + usuarioId + "&desde=" + hoy + "&hasta=" + hoy));
		}
		for (AvisoFamilia aviso : avisos.findByEstadoOrderByIdAsc(EstadoAvisoFamilia.ABIERTO)) {
			if (aviso.getTipo() != TipoAvisoFamilia.DATOS_PERSONALES || aviso.getDerecho() == null) {
				continue;
			}
			LocalDate recibido = aviso.getCreadoEn().toLocalDate();
			LocalDate vence = aviso.getDerecho().vence(recibido, calendario, propiedades.plazoAccesoDiasHabiles(),
					propiedades.plazoOtrosDiasHabiles());
			String pedido = "El pedido de " + aviso.getDerecho().nombre().toLowerCase(java.util.Locale.ROOT)
					+ " de datos personales N.° " + aviso.getId();
			if (hoy.isAfter(vence)) {
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, pedido + " venció el " + vence.format(FECHA)
						+ " sin respuesta. Respóndelo hoy: la ley fija el plazo.", "/avisos-familias",
						new Aviso(TipoAviso.OTRA_CRITICA, "datos-personales:" + aviso.getId())));
			}
			else if (calendario.habilesEntre(recibido, hoy) >= propiedades.avisoDiasHabiles()) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, pedido + " lleva "
						+ calendario.habilesEntre(recibido, hoy) + " días hábiles sin respuesta. Vence el "
						+ vence.format(FECHA) + ".", "/avisos-familias"));
			}
		}
		return alertas;
	}
}
