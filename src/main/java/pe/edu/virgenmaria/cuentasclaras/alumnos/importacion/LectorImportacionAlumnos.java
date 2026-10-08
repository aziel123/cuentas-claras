package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatoInvalidoException;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.ReglasDatosPersonales;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.CeldaXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.Columnas;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.FilaXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.HojaLeida;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.LectorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.PropiedadesExcel;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.TipoCelda;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValoresXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ColumnaImportacion.*;

/**
 * Lee la hoja «Alumnos» y valida cada fila con las MISMAS reglas de los formularios ({@link ReglasDatosPersonales}),
 * más las reglas entre filas. No consulta la base (eso lo hace {@link PlanificadorImportacion}).
 * <ul>
 *   <li>Los encabezados deben ser los de la plantilla; columnas extra se ignoran y se avisa.</li>
 *   <li>Una celda con fórmula, con un error de Excel o con texto que empieza con =, +, - o @ es error (salvo un
 *       celular +51…).</li>
 *   <li>Un DNI escrito como número que perdió el cero inicial se explica.</li>
 *   <li>Fechas como texto dd/mm/aaaa o como fecha de Excel (respetando el sistema 1904).</li>
 * </ul>
 */
@Component
public class LectorImportacionAlumnos {

	private static final int COLUMNAS = ColumnaImportacion.values().length;

	private static final Map<String, Parentesco> PARENTESCOS = parentescos();

	private final LectorXlsxSeguro lector;

	private final PropiedadesExcel propiedades;

	public LectorImportacionAlumnos(LectorXlsxSeguro lector, PropiedadesExcel propiedades) {
		this.lector = lector;
		this.propiedades = propiedades;
	}

	public LecturaImportacion leer(byte[] contenido, int anio, LocalDate hoy) {
		HojaLeida hoja = lector.leer(contenido, PlantillaImportacionAlumnos.HOJA, propiedades.maxFilas() + 1, COLUMNAS);
		if (hoja.filas().isEmpty()) {
			throw new ArchivoNoValidoException("La hoja «Alumnos» está vacía. Escribe un alumno por fila desde la fila 2.");
		}
		validarEncabezados(hoja.filas().getFirst());
		List<String> avisos = hoja.columnasIgnoradas().stream()
				.map(c -> "Se ignoró la columna " + Columnas.letra(c) + ": no es parte de la plantilla y el sistema no "
						+ "guarda esos datos.")
				.toList();
		if (hoja.filas().size() == 1) {
			throw new ArchivoNoValidoException("La hoja «Alumnos» no tiene alumnos: escribe uno por fila desde la fila 2.");
		}
		List<Fila> filas = hoja.filas().subList(1, hoja.filas().size()).stream()
				.map(f -> leerFila(f, hoja.fecha1904(), anio, hoy))
				.toList();
		validarEntreFilas(filas);
		return new LecturaImportacion(filas.stream().map(Fila::resultado).toList(), avisos);
	}

	private static void validarEncabezados(FilaXlsx encabezados) {
		if (encabezados.numero() != 1) {
			throw new ArchivoNoValidoException("La fila 1 debe tener los encabezados de la plantilla. Descarga la "
					+ "plantilla y copia tus datos en ella desde la fila 2.");
		}
		for (ColumnaImportacion columna : ColumnaImportacion.values()) {
			CeldaXlsx celda = encabezados.celda(columna.ordinal());
			String escrito = celda == null ? "" : celda.valorCrudo().strip();
			if (!Normalizador.paraBusqueda(columna.encabezado()).equals(Normalizador.paraBusqueda(escrito))) {
				throw new ArchivoNoValidoException("Los encabezados no son los de la plantilla: en la columna "
						+ columna.letra() + " esperábamos «" + columna.encabezado() + "» y dice «" + recortar(escrito)
						+ "». Descarga la plantilla y copia tus datos en ella.");
			}
		}
	}

	/** Una fila en construcción: sus errores se pueden completar con las reglas entre filas. */
	private static final class Fila {

		final int numero;

		final List<ErrorFila> errores = new ArrayList<>();

		final List<String> advertencias = new ArrayList<>();

		DatosAlumno alumno;

		DatosApoderado apoderado;

		Grado grado;

		String seccion;

		Fila(int numero) {
			this.numero = numero;
		}

		void error(ColumnaImportacion columna, String mensaje) {
			errores.add(new ErrorFila(numero, columna == null ? null : columna.letra(),
					columna == null ? null : columna.encabezado(), mensaje));
		}

		FilaImportacion resultado() {
			return new FilaImportacion(numero, alumno, apoderado, grado, seccion, List.copyOf(errores),
					List.copyOf(advertencias));
		}
	}

	private Fila leerFila(FilaXlsx celdas, boolean fecha1904, int anio, LocalDate hoy) {
		Fila fila = new Fila(celdas.numero());
		Lectura l = new Lectura(celdas, fila);

		DocumentoIdentidad documentoAlumno = l.documento(ALUMNO_TIPO_DOCUMENTO, ALUMNO_NUMERO_DOCUMENTO);
		String paterno = l.regla(ALUMNO_APELLIDO_PATERNO, ReglasDatosPersonales::nombre);
		String materno = l.reglaOpcional(ALUMNO_APELLIDO_MATERNO, ReglasDatosPersonales::nombreOpcional);
		String nombres = l.regla(ALUMNO_NOMBRES, ReglasDatosPersonales::nombre);
		LocalDate nacimiento = l.fecha(ALUMNO_FECHA_NACIMIENTO, fecha1904);
		if (nacimiento != null) {
			try {
				ReglasDatosPersonales.fechaNacimiento(nacimiento, anio, hoy, ALUMNO_FECHA_NACIMIENTO.letra());
			}
			catch (DatoInvalidoException e) {
				fila.error(ALUMNO_FECHA_NACIMIENTO, e.getMessage());
				nacimiento = null;
			}
		}
		fila.grado = l.grado();
		String seccion = l.texto(SECCION, true);
		if (seccion != null) {
			try {
				fila.seccion = Seccion.nombreValido(seccion);
			}
			catch (ReglaNegocioException e) {
				fila.error(SECCION, e.getMessage());
			}
		}

		DocumentoIdentidad documentoApoderado = l.documento(APODERADO_TIPO_DOCUMENTO, APODERADO_NUMERO_DOCUMENTO);
		String paternoApoderado = l.regla(APODERADO_APELLIDO_PATERNO, ReglasDatosPersonales::nombre);
		String maternoApoderado = l.reglaOpcional(APODERADO_APELLIDO_MATERNO, ReglasDatosPersonales::nombreOpcional);
		String nombresApoderado = l.regla(APODERADO_NOMBRES, ReglasDatosPersonales::nombre);
		Parentesco parentesco = l.parentesco();
		String celular = l.reglaOpcional(CELULAR, ReglasDatosPersonales::telefonoWhatsapp);
		String correo = l.reglaOpcional(CORREO, ReglasDatosPersonales::correo);
		if (celular == null && correo == null && !l.conError(CELULAR) && !l.conError(CORREO)) {
			fila.error(CELULAR, "Escribe el celular para WhatsApp o el correo del apoderado: lo necesitamos para "
					+ "avisarle de cada pago.");
		}

		if (fila.errores.isEmpty()) {
			fila.alumno = new DatosAlumno(documentoAlumno, paterno, materno, nombres, nacimiento);
			fila.apoderado = new DatosApoderado(documentoApoderado, paternoApoderado, maternoApoderado,
					nombresApoderado, parentesco, celular, correo);
			ReglasDatosPersonales.advertenciaEdad(nacimiento, fila.grado, anio).ifPresent(fila.advertencias::add);
		}
		return fila;
	}

	/** Lectura de las celdas de una fila; cada problema queda como error de la fila (no se lanza nada). */
	private static final class Lectura {

		private final FilaXlsx celdas;

		private final Fila fila;

		Lectura(FilaXlsx celdas, Fila fila) {
			this.celdas = celdas;
			this.fila = fila;
		}

		interface Regla {
			String aplicar(String valor, String campo);
		}

		boolean conError(ColumnaImportacion columna) {
			return fila.errores.stream().anyMatch(e -> columna.letra().equals(e.columna()));
		}

		/** Texto de la celda, o null (vacía o con error). Rechaza fórmulas, errores de Excel y texto tipo fórmula. */
		String texto(ColumnaImportacion columna, boolean obligatorio) {
			CeldaXlsx celda = celdas.celda(columna.ordinal());
			if (celda == null) {
				if (obligatorio) {
					fila.error(columna, "Este dato es obligatorio.");
				}
				return null;
			}
			if (celda.formula()) {
				fila.error(columna, "Tiene una fórmula: escribe el valor (o usa Pegado especial → Valores).");
				return null;
			}
			if (celda.tipo() == TipoCelda.ERROR) {
				fila.error(columna, "Tiene un error de Excel (" + recortar(celda.valorCrudo()) + "): escribe el valor.");
				return null;
			}
			if (celda.tipo() == TipoCelda.BOOLEANO) {
				fila.error(columna, "Tiene VERDADERO o FALSO: escribe el valor.");
				return null;
			}
			if (celda.tipo() == TipoCelda.NUMERO) {
				Optional<String> entero = ValoresXlsx.entero(celda);
				if (entero.isEmpty()) {
					fila.error(columna, "Escribe el valor sin decimales; escribiste «" + recortar(celda.valorCrudo()) + "».");
					return null;
				}
				return entero.get();
			}
			String valor = Normalizador.limpiar(celda.valorCrudo());
			if (valor == null) {
				if (obligatorio) {
					fila.error(columna, "Este dato es obligatorio.");
				}
				return null;
			}
			boolean celularInternacional = columna == CELULAR && valor.startsWith("+");
			if (!celularInternacional && "=+-@".indexOf(valor.charAt(0)) >= 0) {
				fila.error(columna, "No puede empezar con =, +, - ni @.");
				return null;
			}
			return valor;
		}

		String regla(ColumnaImportacion columna, Regla regla) {
			String valor = texto(columna, true);
			return valor == null ? null : aplicar(columna, valor, regla);
		}

		String reglaOpcional(ColumnaImportacion columna, Regla regla) {
			String valor = texto(columna, false);
			return valor == null ? null : aplicar(columna, valor, regla);
		}

		private String aplicar(ColumnaImportacion columna, String valor, Regla regla) {
			try {
				return regla.aplicar(valor, columna.letra());
			}
			catch (DatoInvalidoException e) {
				fila.error(columna, e.getMessage());
				return null;
			}
		}

		DocumentoIdentidad documento(ColumnaImportacion columnaTipo, ColumnaImportacion columnaNumero) {
			String tipoTexto = texto(columnaTipo, true);
			TipoDocumento tipo = null;
			if (tipoTexto != null) {
				try {
					tipo = ReglasDatosPersonales.tipoDocumento(tipoTexto, columnaTipo.letra());
				}
				catch (DatoInvalidoException e) {
					fila.error(columnaTipo, e.getMessage());
				}
			}
			CeldaXlsx celda = celdas.celda(columnaNumero.ordinal());
			boolean comoNumero = celda != null && !celda.formula() && celda.tipo() == TipoCelda.NUMERO;
			String numero = texto(columnaNumero, true);
			if (numero == null || tipo == null) {
				return null;
			}
			if (comoNumero && tipo == TipoDocumento.DNI && numero.length() < 8) {
				fila.error(columnaNumero, "El DNI quedó guardado como número y perdió el cero inicial («" + numero
						+ "»). Si el DNI empieza con 0, formatea la columna como Texto y escríbelo con sus 8 dígitos.");
				return null;
			}
			try {
				return ReglasDatosPersonales.documento(tipo, numero, columnaNumero.letra());
			}
			catch (DatoInvalidoException e) {
				fila.error(columnaNumero, e.getMessage());
				return null;
			}
		}

		LocalDate fecha(ColumnaImportacion columna, boolean fecha1904) {
			CeldaXlsx celda = celdas.celda(columna.ordinal());
			if (celda != null && !celda.formula()
					&& (celda.tipo() == TipoCelda.NUMERO || celda.tipo() == TipoCelda.FECHA_ISO)) {
				Optional<LocalDate> fecha = ValoresXlsx.fecha(celda, fecha1904);
				if (fecha.isEmpty()) {
					fila.error(columna, "No es una fecha válida; escríbela como dd/mm/aaaa, por ejemplo 05/03/2015.");
				}
				return fecha.orElse(null);
			}
			String texto = texto(columna, true);
			if (texto == null) {
				return null;
			}
			try {
				return Calendario.parsearFecha(texto);
			}
			catch (ReglaNegocioException e) {
				fila.error(columna, e.getMessage());
				return null;
			}
		}

		Grado grado() {
			String nivelTexto = texto(NIVEL, true);
			Nivel nivel = null;
			if (nivelTexto != null) {
				String clave = Normalizador.paraBusqueda(nivelTexto);
				nivel = Arrays.stream(Nivel.values()).filter(n -> n.name().equals(clave)).findFirst().orElse(null);
				if (nivel == null) {
					fila.error(NIVEL, "Escribe Inicial, Primaria o Secundaria; escribiste «" + recortar(nivelTexto) + "».");
				}
			}
			String gradoTexto = texto(GRADO, true);
			if (gradoTexto == null || nivel == null) {
				return null;
			}
			String digitos = gradoTexto.replaceAll("[^0-9]", "");
			Optional<Grado> grado = digitos.isEmpty() || digitos.length() > 2 ? Optional.empty()
					: Grado.de(nivel, Integer.parseInt(digitos));
			if (grado.isEmpty()) {
				fila.error(GRADO, nivel.etiqueta() + " no tiene el grado «" + recortar(gradoTexto) + "». "
						+ (nivel == Nivel.INICIAL ? "En Inicial escribe la edad: 3, 4 o 5."
								: "Escribe un número de 1 a " + (nivel == Nivel.PRIMARIA ? 6 : 5) + "."));
				return null;
			}
			return grado.get();
		}

		Parentesco parentesco() {
			String texto = texto(PARENTESCO, true);
			if (texto == null) {
				return null;
			}
			Parentesco parentesco = PARENTESCOS.get(Normalizador.paraBusqueda(texto));
			if (parentesco == null) {
				fila.error(PARENTESCO, "Elige un parentesco de la lista (Madre, Padre, Abuelo o abuela, Tío o tía, "
						+ "Hermano o hermana, Tutor legal u Otro); escribiste «" + recortar(texto) + "».");
			}
			return parentesco;
		}
	}

	/** Alumno repetido en el archivo: error en todas sus filas. Apoderado con datos distintos: error en sus filas. */
	private static void validarEntreFilas(List<Fila> filas) {
		Map<String, List<Fila>> porAlumno = new LinkedHashMap<>();
		Map<String, List<Fila>> porApoderado = new LinkedHashMap<>();
		for (Fila fila : filas) {
			if (fila.alumno != null) {
				porAlumno.computeIfAbsent(clave(fila.alumno.documento()), k -> new ArrayList<>()).add(fila);
				porApoderado.computeIfAbsent(clave(fila.apoderado.documento()), k -> new ArrayList<>()).add(fila);
			}
		}
		porAlumno.values().stream().filter(g -> g.size() > 1).forEach(grupo -> grupo.forEach(fila ->
				fila.error(ALUMNO_NUMERO_DOCUMENTO, "El alumno con " + fila.alumno.documento().texto()
						+ " está repetido en las filas " + numeros(grupo) + ". Déjalo en una sola fila.")));
		porApoderado.values().stream()
				.filter(g -> g.stream().map(f -> f.apoderado).distinct().count() > 1)
				.forEach(grupo -> grupo.forEach(fila ->
						fila.error(APODERADO_NUMERO_DOCUMENTO, "El apoderado con " + fila.apoderado.documento().texto()
								+ " tiene datos distintos en las filas " + numeros(grupo) + " (nombre, parentesco, celular "
								+ "o correo). Escríbelos igual en todas sus filas.")));
	}

	private static String clave(DocumentoIdentidad documento) {
		return documento.tipo() + ":" + documento.numero();
	}

	private static String numeros(List<Fila> grupo) {
		List<String> numeros = grupo.stream().map(f -> String.valueOf(f.numero)).toList();
		return numeros.size() == 2 ? numeros.get(0) + " y " + numeros.get(1)
				: String.join(", ", numeros.subList(0, numeros.size() - 1)) + " y " + numeros.getLast();
	}

	private static String recortar(String texto) {
		if (texto == null) {
			return "";
		}
		return texto.length() <= 30 ? texto : texto.substring(0, 30) + "…";
	}

	private static Map<String, Parentesco> parentescos() {
		Map<String, Parentesco> mapa = new LinkedHashMap<>();
		for (Parentesco p : Parentesco.values()) {
			mapa.put(p.name().replace('_', ' '), p);
			mapa.put(Normalizador.paraBusqueda(p.etiqueta()), p);
		}
		mapa.put("MAMA", Parentesco.MADRE);
		mapa.put("PAPA", Parentesco.PADRE);
		mapa.put("ABUELA", Parentesco.ABUELO);
		mapa.put("TIA", Parentesco.TIO);
		mapa.put("HERMANA", Parentesco.HERMANO);
		mapa.put("TUTOR", Parentesco.TUTOR_LEGAL);
		mapa.put("TUTORA", Parentesco.TUTOR_LEGAL);
		mapa.put("TUTORA LEGAL", Parentesco.TUTOR_LEGAL);
		return Map.copyOf(mapa);
	}
}
