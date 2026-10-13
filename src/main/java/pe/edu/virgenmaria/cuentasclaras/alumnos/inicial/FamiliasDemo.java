package pe.edu.virgenmaria.cuentasclaras.alumnos.inicial;

import org.springframework.context.annotation.Profile;
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
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 8, tanda 3 (sección 8.2): el año escolar, las secciones y las 40 familias FICTICIAS del colegio de un visitante de
 * la demo. Los nombres son combinaciones de apellidos y nombres comunes, los DNI son de un rango ficticio (alumnos
 * 7000xxxx, apoderados 4000xxxx), los celulares del rango +51 900 100 xxx y los correos de {@code example.com}. Usa
 * {@link RegistroAlumnos}, con sus mismas reglas y su auditoría. Solo en el perfil {@code demo}.
 * <p>
 * El año es el de hoy (en curso): clases del primer lunes de marzo al tercer viernes de diciembre; una sección A por
 * grado (y B en 2.° de primaria y 1.° de secundaria).
 */
@Component
@Profile("demo")
public class FamiliasDemo {

	public static final int FAMILIAS = 40;

	static final Set<Grado> CON_SECCION_B = Set.of(Grado.PRIMARIA_2, Grado.SECUNDARIA_1);

	static final String[] APELLIDOS = { "Quispe", "Flores", "Sánchez", "Rojas", "Huamán", "Mendoza", "García", "Castillo",
			"Chávez", "Vargas", "Torres", "Ramírez", "Díaz", "Gutiérrez", "Mamani", "Paredes", "Salazar", "Cruz",
			"Espinoza", "Vega", "León", "Aguirre", "Campos", "Ríos", "Medina", "Navarro", "Cárdenas", "Herrera", "Soto",
			"Palacios" };

	static final String[] MADRES = { "Rosa", "Julia", "Elena", "Silvia", "Patricia", "Carmen", "Gloria", "Ana María",
			"Milagros", "Verónica", "Liliana", "Roxana", "Diana", "Karina", "Maribel" };

	static final String[] PADRES = { "Pedro", "Martín", "Carlos", "Jorge", "Luis", "Juan Carlos", "Miguel", "Raúl",
			"César", "Víctor", "Eduardo", "Óscar", "Ricardo", "Manuel", "Javier" };

	static final String[] HIJOS = { "Mateo", "Valeria", "Sebastián", "Camila", "Thiago", "Luciana", "Diego", "Ariana",
			"Santiago", "Fernanda", "Gael", "Mía", "Leonardo", "Isabella", "Adrián", "Daniela", "Joaquín", "Alessia",
			"Bruno", "Valentina", "Emilio", "Antonella", "Rodrigo", "Ximena" };

	/** Un alumno de la demo: su DNI ficticio, su nombre y su grado. */
	public record AlumnoDemo(String dni, String nombres, String paterno, Grado grado) {
	}

	/** Una familia de la demo: el DNI de su apoderado, su nombre completo y sus hijos. */
	public record FamiliaDemo(String dniApoderado, String nombreApoderado, List<AlumnoDemo> alumnos) {
	}

	/** Lo que quedó creado. {@code familias.get(0)} es la del portal de las familias (la cuenta del apoderado). */
	public record FamiliasCreadas(Long anioId, int anio, LocalDate inicioClases, List<FamiliaDemo> familias) {
	}

	private final AnioEscolarRepository anios;

	private final SeccionRepository secciones;

	private final RegistroAlumnos registro;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	public FamiliasDemo(AnioEscolarRepository anios, SeccionRepository secciones, RegistroAlumnos registro,
			AuditoriaService auditoria, PlatformTransactionManager transacciones) {
		this.anios = anios;
		this.secciones = secciones;
		this.registro = registro;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	/** El año escolar de la demo para hoy (en curso): marzo a diciembre del año de hoy. */
	public static AnioEscolar anioPara(LocalDate hoy) {
		int numero = hoy.getYear();
		LocalDate inicio = LocalDate.of(numero, 3, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
		LocalDate fin = LocalDate.of(numero, 12, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.FRIDAY));
		return AnioEscolar.nuevo(numero, true, inicio, fin);
	}

	/** Las 40 familias ficticias (siempre las mismas, en cada demo). */
	public static List<FamiliaDemo> familias() {
		List<FamiliaDemo> lista = new ArrayList<>();
		for (int i = 0; i < FAMILIAS; i++) {
			boolean madre = i % 2 == 0;
			String paterno = APELLIDOS[i % APELLIDOS.length];
			String materno = APELLIDOS[(i * 7 + 3) % APELLIDOS.length];
			String nombre = madre ? MADRES[(i / 2) % MADRES.length] : PADRES[(i / 2) % PADRES.length];
			// Los hijos llevan el apellido del padre y, si el apoderado es la madre, el de ella en segundo lugar.
			String paternoHijos = madre ? APELLIDOS[(i * 3 + 11) % APELLIDOS.length] : paterno;
			int hijos = i % 3 == 0 ? 2 : 1;
			List<AlumnoDemo> alumnos = new ArrayList<>();
			for (int j = 0; j < hijos; j++) {
				Grado grado = Grado.values()[(i * 5 + j * 6) % Grado.values().length];
				alumnos.add(new AlumnoDemo(String.format("7000%04d", i * 10 + j), HIJOS[(i + j * 7) % HIJOS.length],
						paternoHijos, grado));
			}
			lista.add(new FamiliaDemo(String.format("4000%04d", i), nombre + " " + paterno + " " + materno,
					List.copyOf(alumnos)));
		}
		return List.copyOf(lista);
	}

	/** Crea el año, las secciones y las familias en ese colegio (sin transacción abierta: abre la suya). */
	public FamiliasCreadas cargar(long colegioId, LocalDate hoy) {
		List<FamiliaDemo> familias = familias();
		return ContextoColegio.en(colegioId, () -> transaccion.execute(estado -> {
			AnioEscolar plantilla = anioPara(hoy);
			AnioEscolar anio = anios.save(AnioEscolar.nuevo(plantilla.getAnio(), true, plantilla.getInicioClases(),
					plantilla.getFinClases()));
			auditoria.registrar(AccionAuditoria.ANIO_ESCOLAR_CREADO, "anio_escolar", anio.getId().toString(), null,
					"año " + anio.getAnio() + "; clases del " + anio.rangoClases(), "Demo (datos ficticios).");
			Map<Grado, Map<String, Seccion>> porGrado = new EnumMap<>(Grado.class);
			for (Grado grado : Grado.values()) {
				for (String nombre : CON_SECCION_B.contains(grado) ? List.of("A", "B") : List.of("A")) {
					porGrado.computeIfAbsent(grado, g -> new HashMap<>()).put(nombre,
							secciones.save(Seccion.nueva(anio, grado, nombre)));
				}
			}
			for (int i = 0; i < familias.size(); i++) {
				crearFamilia(i, familias.get(i), anio, porGrado);
			}
			return new FamiliasCreadas(anio.getId(), anio.getAnio(), anio.getInicioClases(), familias);
		}));
	}

	private void crearFamilia(int i, FamiliaDemo demo, AnioEscolar anio, Map<Grado, Map<String, Seccion>> porGrado) {
		String[] partes = demo.nombreApoderado().split(" ");
		String materno = partes[partes.length - 1];
		String paterno = partes[partes.length - 2];
		String nombres = String.join(" ", java.util.Arrays.copyOf(partes, partes.length - 2));
		AlumnoDemo primero = demo.alumnos().getFirst();
		Familia familia = registro.crearFamilia(Familia.nombrePorDefecto(primero.paterno(), paterno));
		String correo = i % 4 == 0 ? sinTildes(nombres.split(" ")[0] + "." + paterno) + "@example.com" : null;
		Apoderado apoderado = registro.registrarApoderado(familia, new DatosApoderado(
				new DocumentoIdentidad(TipoDocumento.DNI, demo.dniApoderado()), paterno, materno, nombres,
				i % 2 == 0 ? Parentesco.MADRE : Parentesco.PADRE, String.format("+51900100%03d", i), correo));
		// Datos ficticios: los contactos nacen confirmados (como en dev).
		apoderado.verificarContacto(true, apoderado.getTelefonoWhatsapp());
		if (correo != null) {
			apoderado.verificarContacto(false, apoderado.getCorreo());
		}
		for (int j = 0; j < demo.alumnos().size(); j++) {
			AlumnoDemo al = demo.alumnos().get(j);
			// Nacido en junio: el 31 de marzo del año escolar tiene la edad normativa de su grado.
			LocalDate nacimiento = LocalDate.of(anio.getAnio() - al.grado().edadNormativa() - 1, 6, 1 + (i + j) % 28);
			Alumno alumno = registro.registrarAlumno(new DatosAlumno(new DocumentoIdentidad(TipoDocumento.DNI, al.dni()),
					al.paterno(), paterno, al.nombres(), nacimiento), apoderado);
			Map<String, Seccion> delGrado = porGrado.get(al.grado());
			Seccion seccion = delGrado.size() > 1 && (i + j) % 2 == 1 ? delGrado.get("B") : delGrado.get("A");
			registro.matricular(alumno, seccion, null);
		}
	}

	static String sinTildes(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
				.replace(' ', '.');
	}
}
