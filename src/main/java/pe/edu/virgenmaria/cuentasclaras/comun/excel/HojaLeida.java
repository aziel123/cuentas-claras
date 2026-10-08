package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import java.util.List;
import java.util.SortedSet;

/**
 * Hoja leída.
 *
 * @param fecha1904          el libro usa el sistema de fechas 1904 (Mac antiguo): cambia el significado de los seriales
 * @param filas              filas no vacías, en orden
 * @param columnasIgnoradas  columnas con datos más allá del máximo pedido (se avisan, no se leen)
 */
public record HojaLeida(boolean fecha1904, List<FilaXlsx> filas, SortedSet<Integer> columnasIgnoradas) {
}
