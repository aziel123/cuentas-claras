package pe.edu.virgenmaria.cuentasclaras.alumnos.inicial;

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
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.RegistroAlumnos;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Datos de demostración del colegio para desarrollo local (después de los usuarios de {@link DatosDemoDev}):
 * <ul>
 *   <li>Colegio Virgen María: años 2026 (en curso) y 2027 (planificado) con una sección A por grado (y B en
 *       2.° Primaria y 1.° Secundaria), y las familias del prototipo, con hermanos, matriculadas en 2026.</li>
 *   <li>Colegio de Prueba B: su propio año 2026 y un alumno con el MISMO DNI que uno del colegio principal
 *       (para ver que son personas distintas y que ninguno ve al otro).</li>
 * </ul>
 * Solo con el perfil {@code dev}, con H2 en memoria, si {@code cuentasclaras.demo.datos-colegio} es {@code true}
 * (por defecto) y si el colegio principal aún no tiene años. Los DNI y celulares son ficticios.
 */
@Component
@Profile("dev")
@Order(2)
public class DatosDemoColegioDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoColegioDev.class);

	private static final Set<Grado> CON_SECCION_B = Set.of(Grado.PRIMARIA_2, Grado.SECUNDARIA_1);

	record ApoderadoDemo(String dni, String paterno, String materno, String nombres, Parentesco parentesco,
			String celular, String correo) {
	}

	record AlumnoDemo(String dni, String paterno, String materno, String nombres, LocalDate nacimiento, Grado grado,
			String seccion) {
	}

	record FamiliaDemo(ApoderadoDemo apoderado, List<AlumnoDemo> alumnos) {
	}

	static final List<FamiliaDemo> FAMILIAS = List.of(
			new FamiliaDemo(new ApoderadoDemo("45678912", "Huamán", "Ccori", "Rosa", Parentesco.MADRE, "+51987654321",
					"rosa.huaman@example.com"), List.of(
					new AlumnoDemo("78451236", "Quispe", "Huamán", "Mateo", LocalDate.of(2015, 6, 14), Grado.PRIMARIA_5, "A"),
					new AlumnoDemo("80127745", "Quispe", "Huamán", "Valeria", LocalDate.of(2018, 9, 3), Grado.PRIMARIA_2, "B"))),
			new FamiliaDemo(new ApoderadoDemo("41235678", "Flores", "Díaz", "Pedro", Parentesco.PADRE, "+51912345678", null),
					List.of(new AlumnoDemo("75330981", "Flores", "Rojas", "Sebastián", LocalDate.of(2011, 8, 21),
									Grado.SECUNDARIA_3, "A"),
							new AlumnoDemo("82345671", "Flores", "Rojas", "Lucía", LocalDate.of(2021, 7, 10), Grado.INICIAL_4,
									"A"))),
			new FamiliaDemo(new ApoderadoDemo("43781209", "Paredes", "Soto", "Julia", Parentesco.MADRE, "+51934112908", null),
					List.of(new AlumnoDemo("81554203", "Mendoza", "Paredes", "Camila", LocalDate.of(2020, 11, 2),
							Grado.INICIAL_5, "A"))),
			new FamiliaDemo(new ApoderadoDemo("40917735", "Castillo", "Ruiz", "Martín", Parentesco.PADRE, "+51955201774",
					"martin.castillo@example.com"), List.of(
					new AlumnoDemo("76902114", "Castillo", "Vargas", "Diego", LocalDate.of(2013, 5, 30), Grado.SECUNDARIA_1, "B"),
					new AlumnoDemo("79876543", "Castillo", "Vargas", "Fernanda", LocalDate.of(2017, 10, 12), Grado.PRIMARIA_3,
							"A"))),
			new FamiliaDemo(new ApoderadoDemo("42567813", "Ramírez", "Vela", "Elena", Parentesco.MADRE, "+51948330615", null),
					List.of(new AlumnoDemo("79215560", "Chávez", "Ramírez", "Luciana", LocalDate.of(2016, 4, 18),
							Grado.PRIMARIA_4, "A"))),
			new FamiliaDemo(new ApoderadoDemo("40112233", "Rojas", "Medina", "Carlos", Parentesco.PADRE, "+51976540129", null),
					List.of(new AlumnoDemo("74018832", "Rojas", "Salazar", "Thiago", LocalDate.of(2009, 12, 5),
							Grado.SECUNDARIA_5, "A"))),
			new FamiliaDemo(new ApoderadoDemo("44556677", "León", "Aguirre", "Silvia", Parentesco.MADRE, "+51921677403", null),
					List.of(new AlumnoDemo("77640391", "Gutiérrez", "León", "Ariana", LocalDate.of(2014, 8, 9),
							Grado.PRIMARIA_6, "A"))));

	/** Colegio B: mismo DNI de alumno (y de apoderado) que en el colegio principal, otra persona. */
	static final FamiliaDemo FAMILIA_COLEGIO_B = new FamiliaDemo(
			new ApoderadoDemo("45678912", "Lima", "Paz", "Gloria", Parentesco.MADRE, "+51999888777", null),
			List.of(new AlumnoDemo("78451236", "Torres", "Lima", "Andrés", LocalDate.of(2015, 2, 1), Grado.PRIMARIA_5, "A")));

	private final ColegioRepository colegios;

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final RegistroAlumnos registro;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public DatosDemoColegioDev(ColegioRepository colegios, AnioEscolarRepository anios, SeccionRepository secciones,
			RegistroAlumnos registro, AuditoriaService auditoria, PlatformTransactionManager transacciones, Clock reloj,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.colegios = colegios;
		this.anios = anios;
		this.secciones = secciones;
		this.registro = registro;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si creó los datos */
	boolean crearSiCorresponde() {
		if (!habilitado) {
			LOG.info("Datos de demostración del colegio desactivados (cuentasclaras.demo.datos-colegio=false).");
			return false;
		}
		if (urlBaseDatos == null || !urlBaseDatos.startsWith("jdbc:h2:mem:")) {
			LOG.warn("No se crean datos de demostración del colegio: la base no es H2 en memoria.");
			return false;
		}
		if (ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL, () -> anios.count()) > 0) {
			LOG.info("El colegio ya tiene años escolares: no se crean los de demostración.");
			return false;
		}
		ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL, () -> transaccion.executeWithoutResult(estado -> {
			Map<Grado, Map<String, Seccion>> de2026 = crearAnio(2026, true, LocalDate.of(2026, 3, 2),
					LocalDate.of(2026, 12, 18), true);
			crearAnio(2027, false, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 17), true);
			FAMILIAS.forEach(f -> crearFamilia(f, de2026));
		}));
		colegios.findFirstByNombreOrderByIdAsc(DatosDemoDev.NOMBRE_COLEGIO_B).ifPresent(b ->
				ContextoColegio.en(b.getId(), () -> transaccion.executeWithoutResult(estado -> {
					Map<Grado, Map<String, Seccion>> deB = crearAnio(2026, true, LocalDate.of(2026, 3, 2),
							LocalDate.of(2026, 12, 18), false);
					crearFamilia(FAMILIA_COLEGIO_B, deB);
				})));
		LOG.info("Datos de demostración del colegio creados: años 2026 y 2027, {} familias.", FAMILIAS.size());
		return true;
	}

	/** @param todosLosGrados {@code true}: una sección A por grado (y B en algunos); {@code false}: solo 5.° Primaria A */
	private Map<Grado, Map<String, Seccion>> crearAnio(int numero, boolean enCurso, LocalDate inicio, LocalDate fin,
			boolean todosLosGrados) {
		AnioEscolar anio = anios.save(AnioEscolar.nuevo(numero, enCurso, inicio, fin));
		auditoria.registrar(AccionAuditoria.ANIO_ESCOLAR_CREADO, "anio_escolar", anio.getId().toString(), null,
				"año " + numero + "; clases del " + anio.rangoClases(), "Datos de demostración (solo desarrollo).");
		Map<Grado, Map<String, Seccion>> creadas = new EnumMap<>(Grado.class);
		for (Grado grado : todosLosGrados ? List.of(Grado.values()) : List.of(Grado.PRIMARIA_5)) {
			for (String nombre : CON_SECCION_B.contains(grado) ? List.of("A", "B") : List.of("A")) {
				Seccion seccion = secciones.save(Seccion.nueva(anio, grado, nombre));
				creadas.computeIfAbsent(grado, g -> new java.util.HashMap<>()).put(nombre, seccion);
			}
		}
		return creadas;
	}

	private void crearFamilia(FamiliaDemo demo, Map<Grado, Map<String, Seccion>> seccionesDelAnio) {
		AlumnoDemo primero = demo.alumnos().getFirst();
		Familia familia = registro.crearFamilia(Familia.nombrePorDefecto(primero.paterno(), primero.materno()));
		ApoderadoDemo a = demo.apoderado();
		Apoderado apoderado = registro.registrarApoderado(familia, new DatosApoderado(
				new DocumentoIdentidad(TipoDocumento.DNI, a.dni()), a.paterno(), a.materno(), a.nombres(), a.parentesco(),
				a.celular(), a.correo()));
		// Datos de demostración (solo dev, H2): los contactos de las familias demo nacen confirmados (S5-A1).
		apoderado.verificarContacto(true, apoderado.getTelefonoWhatsapp());
		apoderado.verificarContacto(false, apoderado.getCorreo());
		for (AlumnoDemo al : demo.alumnos()) {
			Alumno alumno = registro.registrarAlumno(new DatosAlumno(new DocumentoIdentidad(TipoDocumento.DNI, al.dni()),
					al.paterno(), al.materno(), al.nombres(), al.nacimiento()), apoderado);
			Seccion seccion = seccionesDelAnio.get(al.grado()).get(al.seccion());
			registro.matricular(alumno, seccion, seccion.getAnioEscolar().fechaMatriculaPorDefecto());
		}
	}
}
