package pe.edu.virgenmaria.cuentasclaras.academico.notas.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.AreaDelCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sprint 10, tanda 2 (sección 3.6, E23): la {@code CALIFICACION_TARDIA} aprobada por OTRA persona de Dirección o Promotoría
 * no escribe notas: habilita al docente que la pidió a guardar UN lote {@code REGISTRO_TARDIO} en las 48 horas siguientes,
 * solo en las competencias sin nota (RegistroNotas con la solicitud; en MySQL, trg_lote_calificacion_nace). Al aprobar
 * vuelve a validar: el periodo sigue CERRADO y quien la pidió sigue calificando esa área en la sección.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorCalificacionTardia implements ManejadorSolicitud {

	private final AsignacionDocenteRepository asignaciones;

	private final PeriodoEvaluacionRepository periodos;

	private final ContextoNotas contexto;

	private final DocentesDeArea docentes;

	private final AuditoriaService auditoria;

	public ManejadorCalificacionTardia(AsignacionDocenteRepository asignaciones, PeriodoEvaluacionRepository periodos,
			ContextoNotas contexto, DocentesDeArea docentes, AuditoriaService auditoria) {
		this.asignaciones = asignaciones;
		this.periodos = periodos;
		this.contexto = contexto;
		this.docentes = docentes;
		this.auditoria = auditoria;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CALIFICACION_TARDIA;
	}

	@Override
	public String entidad() {
		return TardiasAprobadas.ENTIDAD;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Pedido pedido = pedido(solicitud);
		if (pedido.periodo().getEstado() != EstadoPeriodo.CERRADO) {
			throw new ReglaNegocioException("El periodo no está cerrado: el docente registra en la hoja de siempre.");
		}
		if (!docentes.califica(solicitud.getSolicitadoPor(), pedido.seccion().getId(), pedido.area())) {
			throw new ReglaNegocioException("Quien la pidió ya no califica " + pedido.area().nombre() + " en "
					+ pedido.seccion().etiqueta() + ": rechaza la solicitud.");
		}
		auditoria.registrar(pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria.SOLICITUD_APROBADA,
				TardiasAprobadas.ENTIDAD, pedido.asignacion().getId().toString(), null, pedido.periodo().getId() + ":"
						+ pedido.area().id(), "Aprobó la nota tardía de " + pedido.area().nombre() + " en "
						+ pedido.seccion().etiqueta() + " (" + pedido.periodo().getNombre() + "): " + solicitud.getSolicitadoPor()
						+ " tiene " + TardiasAprobadas.HORAS + " horas para registrar las competencias sin nota. Aprobó "
						+ aprobador + " (solicitud " + solicitud.getId() + ").");
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		List<String> lineas = new ArrayList<>();
		try {
			Pedido p = pedido(solicitud);
			lineas.add("Sección: " + p.seccion().etiqueta());
			lineas.add("Área: " + p.area().nombre());
			lineas.add("Periodo: " + p.periodo().getNombre() + " (" + p.periodo().getEstado().etiqueta() + ")");
			lineas.add("Plazo: " + TardiasAprobadas.HORAS + " horas desde que apruebes, solo para competencias sin nota");
		}
		catch (ReglaNegocioException e) {
			lineas.add(e.getMessage());
		}
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Al aprobar, el docente podrá registrar notas de un periodo ya cerrado. Confirma que esas competencias sí se "
				+ "trabajaron y por qué no se registraron a tiempo.";
	}

	private Pedido pedido(SolicitudCambio solicitud) {
		AsignacionDocente asignacion = asignaciones.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La asignación de la solicitud ya no existe."));
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		Seccion seccion = contexto.seccion(asignacion.getSeccionId());
		PeriodoEvaluacion periodo = periodos.findById(Long.valueOf(datos.get("periodo_id")))
				.filter(p -> p.getAnioEscolarId().equals(seccion.getAnioEscolar().getId()))
				.orElseThrow(() -> new ReglaNegocioException("El periodo de la solicitud no es del año de la sección."));
		AreaDelCatalogo area = contexto.area(seccion, contexto.configuracion(seccion), Long.parseLong(datos.get("area_id")));
		return new Pedido(asignacion, seccion, periodo, area);
	}

	private record Pedido(AsignacionDocente asignacion, Seccion seccion, PeriodoEvaluacion periodo, AreaDelCatalogo area) {
	}
}
