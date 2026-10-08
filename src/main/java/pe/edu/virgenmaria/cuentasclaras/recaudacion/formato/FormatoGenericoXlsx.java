package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.CeldaXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.FilaXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.HojaLeida;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.LectorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.TipoCelda;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValidadorArchivoXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValoresXlsx;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formato genérico en Excel ({@code .xlsx}): la misma cabecera en la fila 1 de la primera hoja. Se lee con el lector
 * endurecido del proyecto (streaming, sin fórmulas, contra zip bombs y XXE). Los números de Excel se convierten sin
 * pasar por {@code double}: un código de alumno escrito como número recupera sus ceros a la izquierda y una fecha
 * escrita como fecha de Excel se convierte a la fecha del libro.
 */
@Component
public class FormatoGenericoXlsx extends FormatoGenerico {

	public static final String FORMATO = "GENERICO_XLSX";

	private static final int COLUMNAS = 12;

	private final ValidadorArchivoXlsx validador;

	private final LectorXlsxSeguro lector;

	private final PropiedadesRecaudacion propiedades;

	public FormatoGenericoXlsx(ValidadorArchivoXlsx validador, LectorXlsxSeguro lector,
			PropiedadesRecaudacion propiedades) {
		this.validador = validador;
		this.lector = lector;
		this.propiedades = propiedades;
	}

	@Override
	public boolean acepta(String nombreArchivo) {
		return nombreArchivo != null && nombreArchivo.strip().toLowerCase(Locale.ROOT).endsWith(".xlsx");
	}

	@Override
	public LecturaRecaudacion leer(String nombreArchivo, byte[] archivo, LocalDate hoy) {
		validador.validar(nombreArchivo, archivo);
		HojaLeida hoja = lector.leerPrimeraHoja(archivo, propiedades.maximoLineas() + 2, COLUMNAS);
		List<FilaCruda> filas = new ArrayList<>();
		List<String> cabecera = null;
		for (FilaXlsx fila : hoja.filas()) {
			List<String> celdas = new ArrayList<>();
			boolean pie = fila.celda(0) != null && "TOTAL".equalsIgnoreCase(fila.celda(0).valorCrudo().strip());
			for (int columna = 0; columna < COLUMNAS; columna++) {
				// En el pie (TOTAL;monto;cantidad) las columnas no son las de la cabecera.
				String nombre = pie || cabecera == null || columna >= cabecera.size() ? ""
						: normalizar(cabecera.get(columna));
				celdas.add(texto(fila.celda(columna), nombre, hoja.fecha1904()));
			}
			if (cabecera == null) {
				cabecera = celdas;
			}
			filas.add(new FilaCruda(fila.numero(), celdas));
		}
		return convertir(FORMATO, filas, hoy);
	}

	/** El texto de la celda según su columna (sin pasar por {@code double}). */
	private static String texto(CeldaXlsx celda, String columna, boolean fecha1904) {
		if (celda == null || celda.valorCrudo() == null) {
			return "";
		}
		if (celda.formula()) {
			// Nunca se usa el valor de una fórmula: el dato debe estar escrito tal cual vino del banco.
			return "=fórmula";
		}
		if (celda.tipo() != TipoCelda.NUMERO && celda.tipo() != TipoCelda.FECHA_ISO) {
			return celda.valorCrudo().strip();
		}
		return switch (columna) {
			case FECHA_PAGO -> ValoresXlsx.fecha(celda, fecha1904).map(LocalDate::toString)
					.orElse(celda.valorCrudo().strip());
			case CODIGO_ALUMNO -> ValoresXlsx.entero(celda).map(n -> ceros(n, 8)).orElse(celda.valorCrudo().strip());
			case REFERENCIA_DEUDA -> ValoresXlsx.entero(celda).map(n -> ceros(n, 9)).orElse(celda.valorCrudo().strip());
			case NUMERO_OPERACION -> ValoresXlsx.entero(celda).orElse(celda.valorCrudo().strip());
			default -> ValoresXlsx.entero(celda).orElse(celda.valorCrudo().strip());
		};
	}

	private static String ceros(String numero, int digitos) {
		return numero.length() >= digitos ? numero : "0".repeat(digitos - numero.length()) + numero;
	}
}
