package pe.edu.virgenmaria.cuentasclaras.academico.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

/**
 * Sprint 9, tanda 1 (sección 3.14): las asignaciones del colegio de un visitante de la demo, hechas por sus personas con
 * los mismos servicios del colegio. Administración propone a la cuenta «docente» como tutor de 3.° Primaria A y como
 * docente de Matemática de 4.° Primaria A, y Dirección las aprueba en la bandeja con su firma. Así quien entra como
 * docente ve sus dos secciones y los nombres de sus alumnos (ficticios), y nada de cobranza. Solo en el perfil
 * {@code demo}.
 */
@Component
@Profile("demo")
public class AsignacionesDemo {

	static final String MOTIVO = "Carga horaria del año (ejemplo de la demo)";

	private final Asignaciones asignaciones;

	private final BandejaAprobaciones bandeja;

	private final SolicitudCambioRepository solicitudes;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final UsuarioRepository usuarios;

	private final PersonaDemo personas;

	private final TransactionTemplate lectura;

	public AsignacionesDemo(Asignaciones asignaciones, BandejaAprobaciones bandeja, SolicitudCambioRepository solicitudes,
			AnioEscolarRepository anios, SeccionRepository secciones, UsuarioRepository usuarios, PersonaDemo personas,
			PlatformTransactionManager transacciones) {
		this.asignaciones = asignaciones;
		this.bandeja = bandeja;
		this.solicitudes = solicitudes;
		this.anios = anios;
		this.secciones = secciones;
		this.usuarios = usuarios;
		this.personas = personas;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	/** Propone (Administración) y aprueba (Dirección) las dos asignaciones de la cuenta «docente». */
	public void cargar(long colegioId, String administracion, String direccion, String docente, int anio) {
		Long docenteId = leer(colegioId, () -> usuarios.findByNombreUsuario(docente).orElseThrow().getId());
		Long tutoria = seccion(colegioId, anio, Grado.PRIMARIA_3);
		Long matematica = seccion(colegioId, anio, Grado.PRIMARIA_4);
		proponerYAprobar(colegioId, administracion, direccion,
				new ProponerAsignacionRequest(tutoria, docenteId, TipoAsignacion.TUTOR, null, MOTIVO));
		proponerYAprobar(colegioId, administracion, direccion,
				new ProponerAsignacionRequest(matematica, docenteId, TipoAsignacion.DOCENTE, "Matemática", MOTIVO));
	}

	private void proponerYAprobar(long colegioId, String administracion, String direccion, ProponerAsignacionRequest pedido) {
		Long asignacion = personas.como(colegioId, administracion, () -> asignaciones.proponer(pedido));
		Long solicitud = leer(colegioId, () -> solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
				.filter(s -> s.getTipo() == TipoSolicitud.ASIGNACION_DOCENTE && asignacion.equals(s.getEntidadId()))
				.findFirst().orElseThrow().getId());
		personas.como(colegioId, direccion, () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	private Long seccion(long colegioId, int anio, Grado grado) {
		return leer(colegioId, () -> {
			Long anioId = anios.findByAnio(anio).orElseThrow().getId();
			return secciones.findByAnioEscolarIdAndGradoAndNombre(anioId, grado, "A").map(Seccion::getId).orElseThrow();
		});
	}

	private <T> T leer(long colegioId, java.util.function.Supplier<T> lectura) {
		return ContextoColegio.en(colegioId, () -> this.lectura.execute(t -> lectura.get()));
	}
}
