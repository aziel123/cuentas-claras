package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MarcaAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.MarcaAsistenciaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sprint 9, tanda 2 (E6, E52, A13): aplica la solicitud {@code CORRECCION_ASISTENCIA} aprobada en la bandeja (que ya
 * comprobó que quien aprueba es OTRA persona de Promotoría o Dirección y firmó). Inserta la marca
 * {@code CORRECCION_APROBADA} con el estado de la solicitud, a nombre de quien aprueba y con su sesión. Vuelve a validar:
 * la marca sigue vigente y quien la pidió sigue siendo docente de la sección (A13). La familia recibe la corrección en el
 * siguiente envío (marca de agua). En MySQL lo exige trg_marca_asistencia_nace.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorCorreccionAsistencia implements ManejadorSolicitud {

	private final MarcaAsistenciaRepository marcas;

	private final SeccionRepository secciones;

	private final DocentesDeSeccion docentes;

	private final SesionDeQuienEscribe sesiones;

	private final AuditoriaService auditoria;

	public ManejadorCorreccionAsistencia(MarcaAsistenciaRepository marcas, SeccionRepository secciones,
			DocentesDeSeccion docentes, SesionDeQuienEscribe sesiones, AuditoriaService auditoria) {
		this.marcas = marcas;
		this.secciones = secciones;
		this.docentes = docentes;
		this.sesiones = sesiones;
		this.auditoria = auditoria;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CORRECCION_ASISTENCIA;
	}

	@Override
	public String entidad() {
		return ServicioAsistencia.ENTIDAD_MARCA;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		MarcaAsistencia vigente = marcas.findById(Objects.requireNonNull(solicitud.getEntidadId(), "entidadId"))
				.orElseThrow(() -> new ReglaNegocioException("La marca de la solicitud ya no existe."));
		if (marcas.existsByReemplazaId(vigente.getId())) {
			throw new ReglaNegocioException("La marca cambió después de pedir la corrección (otra corrección o una "
					+ "justificación de la familia): rechaza esta solicitud y que el docente la pida de nuevo.");
		}
		if (!docentes.es(solicitud.getSolicitadoPor(), vigente.getSeccionId())) {
			throw new ReglaNegocioException("Quien pidió la corrección ya no es docente de la sección: rechaza la solicitud.");
		}
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		EstadoAsistencia estado = EstadoAsistencia.valueOf(datos.get("estadoNuevo"));
		LocalTime hora = datos.get("horaLlegada") == null ? null : LocalTime.parse(datos.get("horaLlegada"));
		MarcaAsistencia nueva = marcas.saveAndFlush(MarcaAsistencia.correccion(vigente, estado, hora, solicitud.getMotivo(),
				solicitud.getId(), sesiones.de(PersonaEnSesion.actual())));
		auditoria.registrar(AccionAuditoria.ASISTENCIA_CORREGIDA_DESPUES_DEL_AVISO, ServicioAsistencia.ENTIDAD_MARCA,
				nueva.getId().toString(), vigente.getEstado().name(), nueva.getEstado().name(), "Aprobó la corrección de "
						+ "la marca " + vigente.getId() + " del " + vigente.getFecha() + ": de " + vigente.getEstado().etiqueta()
						+ " a " + nueva.getEstado().etiqueta() + ". Pidió " + solicitud.getSolicitadoPor() + " y aprobó "
						+ aprobador + " (solicitud " + solicitud.getId() + "). La familia recibe la corrección.");
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		List<String> lineas = new ArrayList<>();
		marcas.findById(solicitud.getEntidadId()).ifPresent(m -> {
			secciones.findById(m.getSeccionId()).ifPresent(s -> lineas.add("Sección: " + s.etiqueta()));
			lineas.add("Día: " + m.getFecha());
			lineas.add("Hoy figura: " + m.getEstado().etiqueta() + " (" + m.getOrigen().etiqueta() + ")");
		});
		String nuevo = DatosSolicitud.leer(solicitud.getDatos()).get("estadoNuevo");
		if (nuevo != null) {
			lineas.add("Quedaría: " + EstadoAsistencia.valueOf(nuevo).etiqueta());
		}
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Al aprobar, la marca cambia a tu nombre y la familia recibe el aviso de la corrección. Confirma con el "
				+ "docente (o con el registro en papel) que de verdad fue así.";
	}
}
