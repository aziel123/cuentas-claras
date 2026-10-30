package pe.edu.virgenmaria.cuentasclaras.academico.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo académico del arranque de desarrollo, listo para una demostración en vivo (lo mismo que la demo pública, con
 * {@link EjemploAcademico}): la cuenta «docente» es tutora de 3.° Primaria A (Comunicación,
 * Matemática, Personal Social, Ciencia y Tecnología, Arte y Cultura) y enseña Matemática en 4.° y 5.° Primaria A, con las
 * áreas del catálogo, propuestas por «administracion» y aprobadas por «director». La evaluación 2026 va por bimestres; el I
 * bimestre queda cerrado con las notas de todas sus hojas y las libretas emitidas (también las de los hijos de la cuenta
 * «apoderado», y la versión 2 de la de Mateo por una corrección que aprueba «promotor»); el II, abierto. Hay asistencia de
 * los últimos días hábiles (registro tardío aprobado por «director») y, si hoy hay clases, la de hoy de 3.° Primaria A.
 * Los comunicados van después, en {@code ComunicadosDemoDev} (lo académico no depende de comunicados). Solo con H2 en
 * memoria. Corre al final del arranque, antes de que se acepte el ingreso
 * ({@code EsperaDatosDemoDev}).
 */
@Component
@Profile("dev")
@Order(21)
public class AcademicoDemoDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(AcademicoDemoDev.class);

	static final int ANIO = 2026;

	/** Mateo Quispe Huamán (5.° Primaria A), hijo de Rosa Huamán Ccori (cuenta «apoderado»): su libreta llega a la versión 2. */
	static final String DNI_CORRECCION = "78451236";

	static final List<EjemploAcademico.Calificable> CALIFICABLES = List.of(
			new EjemploAcademico.Calificable(Grado.PRIMARIA_3, "A", TipoAsignacion.TUTOR,
					List.of("COM", "MAT", "PS", "CYT", "AYC")),
			new EjemploAcademico.Calificable(Grado.PRIMARIA_4, "A", TipoAsignacion.DOCENTE, List.of("MAT")),
			new EjemploAcademico.Calificable(Grado.PRIMARIA_5, "A", TipoAsignacion.DOCENTE, List.of("MAT")));

	static final EjemploAcademico.Personas PERSONAS = new EjemploAcademico.Personas("promotor", "director",
			"administracion", "docente");

	/** Los días hábiles pasados con asistencia (dentro de los 5 que admite el registro tardío). */
	static final int DIAS_DE_ASISTENCIA = 3;

	private final EjemploAcademico academico;

	private final PersonaDemo personas;

	private final Clock reloj;

	private final String urlBaseDatos;

	private final boolean habilitado;

	private final pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository solicitudes;

	private final org.springframework.transaction.support.TransactionTemplate lectura;

	public AcademicoDemoDev(EjemploAcademico academico, PersonaDemo personas, Clock reloj,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado,
			pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository solicitudes,
			org.springframework.transaction.PlatformTransactionManager transacciones) {
		this.solicitudes = solicitudes;
		this.lectura = new org.springframework.transaction.support.TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.academico = academico;
		this.personas = personas;
		this.reloj = reloj;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si cargó lo académico */
	boolean crearSiCorresponde() {
		if (!habilitado || !pe.edu.virgenmaria.cuentasclaras.comun.basedatos.BaseH2Local.es(urlBaseDatos)
				|| !personas.existen(DatosDemoDev.COLEGIO_PRINCIPAL, "promotor", "director", "administracion", "docente")) {
			return false;
		}
		// Demo persistente (H2 en archivo): en un segundo arranque lo académico ya existe (no se carga otra vez).
		if (Boolean.TRUE.equals(pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(
				DatosDemoDev.COLEGIO_PRINCIPAL, () -> lectura.execute(t -> !solicitudes.findByTipoOrderByIdDesc(
						pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud.ASIGNACION_DOCENTE).isEmpty())))) {
			LOG.info("Ya hay asignaciones docentes: lo académico de demostración no se carga otra vez.");
			return false;
		}
		long inicio = System.nanoTime();
		long colegio = DatosDemoDev.COLEGIO_PRINCIPAL;
		LocalDate hoy = LocalDate.now(reloj);
		EjemploAcademico.Calificado calificado = academico.calificar(colegio, PERSONAS, ANIO, hoy, CALIFICABLES,
				DNI_CORRECCION);
		int dias = academico.asistencia(colegio, PERSONAS, ANIO, hoy, CALIFICABLES, DIAS_DE_ASISTENCIA,
				CALIFICABLES.getFirst());
		LOG.info("Académico de demostración: «docente» con {} áreas del catálogo en 3.°, 4.° y 5.° Primaria A (aprobadas por "
				+ "«director»); {}; asistencia en {} día(s) de sección ({} ms).", CALIFICABLES.stream()
				.mapToInt(c -> c.areas().size()).sum(), calificado == null ? "sin bimestres (hoy no deja un I bimestre "
						+ "terminado)" : "I bimestre cerrado con " + calificado.notas() + " notas y libretas emitidas"
						+ (calificado.conVersion2() ? " (una versión 2 por corrección)" : "") + ", II bimestre abierto",
				dias, (System.nanoTime() - inicio) / 1_000_000);
		return true;
	}
}
