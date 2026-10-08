package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.CeldaXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.FilaXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.HojaLeida;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.LectorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.TipoCelda;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValidadorArchivoXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValoresXlsx;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracto en el formato genérico en Excel ({@code .xlsx}), en la primera hoja. Se lee con el lector endurecido del
 * proyecto (streaming, sin fórmulas, contra zip bombs y XXE). Una fecha escrita como fecha de Excel se convierte a la
 * fecha del libro; una fórmula nunca se usa (el dato debe estar escrito tal cual vino del banco).
 */
@Component
public class FormatoExtractoXlsx extends FormatoExtractoGenerico {

	public static final String FORMATO = "GENERICO_XLSX";

	private static final int COLUMNAS = 12;

	private final ValidadorArchivoXlsx validador;

	private final LectorXlsxSeguro lector;

	private final PropiedadesConciliacion propiedades;

	public FormatoExtractoXlsx(ValidadorArchivoXlsx validador, LectorXlsxSeguro lector,
			PropiedadesConciliacion propiedades) {
		this.validador = validador;
		this.lector = lector;
		this.propiedades = propiedades;
	}

	@Override
	public boolean acepta(String nombreArchivo) {
		return nombreArchivo != null && nombreArchivo.strip().toLowerCase(Locale.ROOT).endsWith(".xlsx");
	}

	@Override
	public LecturaExtracto leer(String nombreArchivo, byte[] archivo) {
		validador.validar(nombreArchivo, archivo);
		HojaLeida hoja = lector.leerPrimeraHoja(archivo, propiedades.maximoLineas() + 12, COLUMNAS);
		List<FilaCruda> filas = new ArrayList<>();
		List<String> cabecera = null;
		for (FilaXlsx fila : hoja.filas()) {
			List<String> celdas = new ArrayList<>();
			for (int columna = 0; columna < COLUMNAS; columna++) {
				String nombre = cabecera == null || columna >= cabecera.size() ? "" : normalizar(cabecera.get(columna));
				celdas.add(texto(fila.celda(columna), nombre, hoja.fecha1904()));
			}
			List<String> nombres = celdas.stream().map(FormatoExtractoGenerico::normalizar).toList();
			if (cabecera == null && nombres.contains(FECHA) && nombres.contains(SALDO)) {
				cabecera = celdas;
			}
			filas.add(new FilaCruda(fila.numero(), celdas));
		}
		return convertir(FORMATO, filas);
	}

	private static String texto(CeldaXlsx celda, String columna, boolean fecha1904) {
		if (celda == null || celda.valorCrudo() == null) {
			return "";
		}
		if (celda.formula()) {
			return "=fórmula";
		}
		if (celda.tipo() != TipoCelda.NUMERO && celda.tipo() != TipoCelda.FECHA_ISO) {
			return celda.valorCrudo().strip();
		}
		if (FECHA.equals(columna)) {
			return ValoresXlsx.fecha(celda, fecha1904).map(LocalDate::toString).orElse(celda.valorCrudo().strip());
		}
		if (NUMERO_OPERACION.equals(columna) || CUENTA.equals(columna)) {
			return ValoresXlsx.entero(celda).orElse(celda.valorCrudo().strip());
		}
		return celda.valorCrudo().strip();
	}
}
