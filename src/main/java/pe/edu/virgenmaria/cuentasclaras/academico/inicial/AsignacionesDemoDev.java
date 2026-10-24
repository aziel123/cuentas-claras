package pe.edu.virgenmaria.cuentasclaras.academico.inicial;

import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.BaseH2Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
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
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

/**
 * Rediseño (tablero de Dirección): en desarrollo, las mismas dos asignaciones que AsignacionesDemo hace en la demo
 * pública (esa clase es solo del perfil demo) para la cuenta «docente»
 * (tutora de 3.° Primaria A y Matemática de 4.° Primaria A), propuestas por «administracion» y aprobadas por «director».
 * Así el inicio de Dirección muestra docentes asignados y las secciones que aún no tienen docente. Solo con H2 en memoria.
 */
@Component
@Profile("dev")
@Order(21)
public class AsignacionesDemoDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(AsignacionesDemoDev.class);

	private static final String MOTIVO = "Carga horaria del año (ejemplo de desarrollo)";

	private final Asignaciones asignaciones;

	private final BandejaAprobaciones bandeja;

	private final SolicitudCambioRepository solicitudes;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final UsuarioRepository usuarios;

	private final PersonaDemo personas;

	private final TransactionTemplate lectura;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public AsignacionesDemoDev(Asignaciones asignaciones, BandejaAprobaciones bandeja,
			SolicitudCambioRepository solicitudes, AnioEscolarRepository anios, SeccionRepository secciones,
			UsuarioRepository usuarios, PersonaDemo personas, PlatformTransactionManager transacciones,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.asignaciones = asignaciones;
		this.bandeja = bandeja;
		this.solicitudes = solicitudes;
		this.anios = anios;
		this.secciones = secciones;
		this.usuarios = usuarios;
		this.personas = personas;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		if (!habilitado || !BaseH2Local.es(urlBaseDatos)
				|| !personas.existen(DatosDemoDev.COLEGIO_PRINCIPAL, "administracion", "director", "docente")) {
			return;
		}
		long colegio = DatosDemoDev.COLEGIO_PRINCIPAL;
		// Demo persistente: en un segundo arranque las asignaciones ya existen (no se proponen otra vez).
		if (!leer(colegio, () -> solicitudes.findByTipoOrderByIdDesc(TipoSolicitud.ASIGNACION_DOCENTE)).isEmpty()) {
			LOG.info("Ya hay asignaciones docentes: no se crean las de demostración.");
			return;
		}
		try {
			Long docente = leer(colegio, () -> usuarios.findByNombreUsuario("docente").orElseThrow().getId());
			proponerYAprobar(colegio, new ProponerAsignacionRequest(seccion(colegio, Grado.PRIMARIA_3), docente,
					TipoAsignacion.TUTOR, null, MOTIVO));
			proponerYAprobar(colegio, new ProponerAsignacionRequest(seccion(colegio, Grado.PRIMARIA_4), docente,
					TipoAsignacion.DOCENTE, "Matemática", MOTIVO));
			LOG.info("Asignaciones de demostración: «docente» es tutora de 3.° Primaria A y enseña Matemática en 4.° "
					+ "Primaria A (aprobadas por «director»).");
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudieron crear las asignaciones de demostración: {}", e.getMessage());
		}
	}

	private void proponerYAprobar(long colegio, ProponerAsignacionRequest pedido) {
		Long asignacion = personas.como(colegio, "administracion", () -> asignaciones.proponer(pedido));
		Long solicitud = leer(colegio, () -> solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
				.filter(s -> s.getTipo() == TipoSolicitud.ASIGNACION_DOCENTE && asignacion.equals(s.getEntidadId()))
				.findFirst().orElseThrow().getId());
		personas.como(colegio, "director", () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	private Long seccion(long colegio, Grado grado) {
		return leer(colegio, () -> {
			Long anioId = anios.findByAnio(2026).orElseThrow().getId();
			return secciones.findByAnioEscolarIdAndGradoAndNombre(anioId, grado, "A").map(Seccion::getId).orElseThrow();
		});
	}

	private <T> T leer(long colegio, java.util.function.Supplier<T> consulta) {
		return ContextoColegio.en(colegio, () -> this.lectura.execute(t -> consulta.get()));
	}
}
