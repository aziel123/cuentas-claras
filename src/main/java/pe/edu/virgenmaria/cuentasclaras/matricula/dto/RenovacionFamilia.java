package pe.edu.virgenmaria.cuentasclaras.matricula.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * La renovación de un hijo, como la ve su familia (pantalla 6): «Ana continúa en 2027 en 2.° de primaria, sección A.
 * Matrícula S/ 300.00, vence el 28/02/2027». Solo el nombre de pila del alumno.
 */
public record RenovacionFamilia(Long id, String alumno, int anio, String grado, String seccion,
		BigDecimal montoMatricula, LocalDate vencimientoMatricula, LocalDate respondeHasta, String estado,
		String variante, boolean porResponder, boolean continua) {
}
