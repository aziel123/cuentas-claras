package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Una celda de un reporte que escribe {@link EscritorXlsxSeguro}. Solo cuatro tipos: no hay fórmulas, hipervínculos ni
 * comentarios posibles. El texto pasa siempre por {@link TextoCelda} (sin caracteres de control y con comilla delante si
 * Excel lo tomaría por fórmula); el dinero se escribe como número con dos decimales, convertido en un solo lugar
 * ({@code CeldaDinero}).
 */
public sealed interface Celda {

	record Texto(String valor) implements Celda {
	}

	record Dinero(BigDecimal valor) implements Celda {

		public Dinero {
			Objects.requireNonNull(valor, "valor");
		}
	}

	record Fecha(LocalDate valor) implements Celda {
	}

	record Entero(long valor) implements Celda {
	}

	static Celda texto(String valor) {
		return new Texto(valor);
	}

	static Celda dinero(BigDecimal valor) {
		return new Dinero(valor);
	}

	static Celda fecha(LocalDate valor) {
		return new Fecha(valor);
	}

	static Celda entero(long valor) {
		return new Entero(valor);
	}
}
