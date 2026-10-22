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
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.RegistroAlumnos;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de demostración (solo perfil {@code dev}, H2 en memoria): 40 familias más del Colegio Virgen María, para que los
 * tableros y reportes tengan forma desde el primer ingreso (en total 47 familias y 58 alumnos, de Inicial a 5.° de
 * Secundaria). Corre después de {@code DatosDemoColegioDev} y ANTES de los planes de pensiones: al aprobarse el plan, el
 * sistema genera su cronograma igual que a las demás. Los nombres son ficticios; los DNI, celulares y correos no se
 * repiten entre familias (AlertasContactos) y los contactos nacen confirmados, como en las otras familias de la demo.
 * Las 7 familias de las historias (Quispe Huamán, Flores Rojas, etc.) no cambian.
 */
@Component
@Profile("dev")
@Order(3)
public class FamiliasAmpliadasDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(FamiliasAmpliadasDev.class);

	/** Un alumno de las familias ampliadas: su DNI, nombres, apellidos y grado (la sección es la A). */
	public record AlumnoAmpliado(String dni, String paterno, String materno, String nombres, Grado grado,
			LocalDate nacimiento) {
	}

	/** Una familia ampliada: el apoderado (DNI, nombres, celular) y sus hijos. */
	public record FamiliaAmpliada(String dniApoderado, String paterno, String materno, String nombres,
			Parentesco parentesco, String celular, List<AlumnoAmpliado> alumnos) {

		public String nombre() {
			return alumnos.getFirst().paterno() + " " + alumnos.getFirst().materno();
		}
	}

	private static final String[] PATERNOS = { "Mamani", "Condori", "Huamaní", "Ccahuana", "Torres", "Vargas", "Rojas",
		"Chávez", "Ramos", "Mendoza", "Salazar", "Paredes", "Gonzales", "Sánchez", "Cárdenas", "Huanca", "Apaza", "Quispe",
		"Tito", "Ticona", "Espinoza", "Alarcón", "Villanueva", "Cruz", "Llerena", "Zegarra", "Choque", "Rivera", "Benavides",
		"Huarcaya", "Palomino", "Cáceres", "Medina", "Navarro", "Pinto", "Delgado", "Ayala", "Bustamante", "Valdivia",
		"Ccori" };

	private static final String[] MATERNOS = { "Flores", "Yupanqui", "Pari", "Aguilar", "Castro", "Ríos", "Ortiz",
		"Huamán", "Lázaro", "Cusi", "Fernández", "Gutiérrez", "Inga", "Laura", "Morales", "Núñez", "Pacheco", "Quiroz",
		"Ruiz", "Soto" };

	private static final String[] NOMBRES_NINO = { "Santiago", "Matías", "Sebastián", "Alessandro", "Thiago", "Gael",
		"Rodrigo", "Adrián", "Fabricio", "Leonardo", "Joaquín", "Benjamín", "Dylan", "Mateo", "Ian", "Emiliano", "Bruno",
		"Gabriel", "Nicolás", "Samuel" };

	private static final String[] NOMBRES_NINA = { "Valentina", "Camila", "Luana", "Mía", "Isabella", "Kiara",
		"Alessia", "Ximena", "Antonella", "Fernanda", "Zoe", "Renata", "Abigail", "Daniela", "Romina", "Mariana", "Ariana",
		"Nicole", "Danna", "Brianna" };

	private static final String[] NOMBRES_MADRE = { "Rosa", "Carmen", "Gladys", "Yolanda", "Milagros", "Marleni",
		"Patricia", "Elizabeth", "Sonia", "Jackeline", "Maribel", "Nelly", "Roxana", "Liliana", "Edith", "Karina", "Silvia",
		"Lucía", "Verónica", "Janet" };

	private static final String[] NOMBRES_PADRE = { "Juan", "Luis", "Jorge", "Carlos", "Víctor", "Wilber", "Edwin",
		"Percy", "Raúl", "Miguel", "Freddy", "César", "Henry", "Elmer", "Alberto", "Javier", "Walter", "Óscar", "Hugo",
		"Fredy" };

	/** Las 40 familias, siempre las mismas (deterministas): cada cuarta familia tiene dos hijos. */
	public static final List<FamiliaAmpliada> FAMILIAS = crear();

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final AlumnoRepository alumnos;

	private final RegistroAlumnos registro;

	private final TransactionTemplate transaccion;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public FamiliasAmpliadasDev(AnioEscolarRepository anios, SeccionRepository secciones, AlumnoRepository alumnos,
			RegistroAlumnos registro, PlatformTransactionManager transacciones,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.anios = anios;
		this.secciones = secciones;
		this.alumnos = alumnos;
		this.registro = registro;
		this.transaccion = new TransactionTemplate(transacciones);
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	private static List<FamiliaAmpliada> crear() {
		Grado[] grados = Grado.values();
		List<FamiliaAmpliada> familias = new ArrayList<>();
		int alumno = 0;
		for (int i = 0; i < 40; i++) {
			String paterno = PATERNOS[i % PATERNOS.length];
			String materno = MATERNOS[(i * 7 + 3) % MATERNOS.length];
			boolean madre = i % 3 != 2;
			String nombreApoderado = madre ? NOMBRES_MADRE[i % NOMBRES_MADRE.length]
					: NOMBRES_PADRE[i % NOMBRES_PADRE.length];
			// El apoderado lleva su propio apellido materno (la madre, el apellido de la familia de ella).
			String apPaterno = madre ? materno : paterno;
			String apMaterno = madre ? MATERNOS[(i * 3 + 11) % MATERNOS.length] : PATERNOS[(i * 5 + 7) % PATERNOS.length];
			List<AlumnoAmpliado> hijos = new ArrayList<>();
			int cuantos = i % 4 == 0 ? 2 : 1;
			for (int h = 0; h < cuantos; h++) {
				Grado grado = grados[(i * 5 + h * 4) % grados.length];
				boolean nina = (alumno % 2) == 0;
				String nombre = nina ? NOMBRES_NINA[alumno % NOMBRES_NINA.length]
						: NOMBRES_NINO[alumno % NOMBRES_NINO.length];
				int edad = edad(grado);
				hijos.add(new AlumnoAmpliado(String.valueOf(61_000_000 + alumno * 4_711), paterno, materno, nombre, grado,
						LocalDate.of(2026 - edad - 1, 1 + (alumno % 12), 1 + (alumno * 3) % 27)));
				alumno++;
			}
			familias.add(new FamiliaAmpliada(String.valueOf(46_100_000 + i * 5_309), apPaterno, apMaterno, nombreApoderado,
					madre ? Parentesco.MADRE : Parentesco.PADRE, "+5196" + String.format("%07d", 1_000_000 + i * 24_137),
					List.copyOf(hijos)));
		}
		return List.copyOf(familias);
	}

	/** Edad al 31 de marzo según el grado (Inicial 3 años a 5.° de Secundaria). */
	private static int edad(Grado grado) {
		return 3 + grado.ordinal();
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		if (!habilitado || urlBaseDatos == null || !urlBaseDatos.startsWith("jdbc:h2:mem:")) {
			return;
		}
		try {
			Integer creadas = ContextoColegio.en(DatosDemoDev.COLEGIO_PRINCIPAL, () -> transaccion.execute(e -> crearTodas()));
			if (creadas != null && creadas > 0) {
				LOG.info("Familias ampliadas de demostración: {} familias más ({} alumnos) en 2026.", creadas,
						FAMILIAS.stream().mapToInt(f -> f.alumnos().size()).sum());
			}
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudieron crear las familias ampliadas de demostración: {}", e.getMessage());
		}
	}

	private int crearTodas() {
		AnioEscolar anio = anios.findByAnio(2026).orElse(null);
		if (anio == null || alumnos.findByDocumentoTipoAndDocumentoNumero(TipoDocumento.DNI,
				FAMILIAS.getFirst().alumnos().getFirst().dni()).isPresent()) {
			return 0;
		}
		for (FamiliaAmpliada f : FAMILIAS) {
			Familia familia = registro.crearFamilia(Familia.nombrePorDefecto(f.alumnos().getFirst().paterno(),
					f.alumnos().getFirst().materno()));
			Apoderado apoderado = registro.registrarApoderado(familia, new DatosApoderado(
					new DocumentoIdentidad(TipoDocumento.DNI, f.dniApoderado()), f.paterno(), f.materno(), f.nombres(),
					f.parentesco(), f.celular(), null));
			apoderado.verificarContacto(true, apoderado.getTelefonoWhatsapp());
			for (AlumnoAmpliado a : f.alumnos()) {
				Alumno alumno = registro.registrarAlumno(new DatosAlumno(new DocumentoIdentidad(TipoDocumento.DNI, a.dni()),
						a.paterno(), a.materno(), a.nombres(), a.nacimiento()), apoderado);
				registro.matricular(alumno, secciones.findByAnioEscolarIdAndGradoAndNombre(anio.getId(), a.grado(), "A")
						.orElseThrow(), anio.fechaMatriculaPorDefecto());
			}
		}
		return FAMILIAS.size();
	}
}
