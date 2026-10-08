package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;

/**
 * Una familia con deuda vencida, para la lista en pantalla de Promotoría, Dirección y Administración (no se exporta,
 * decisión 76). Sin contactos ni documentos: para llamar se entra a la ficha de la familia con sus permisos de siempre.
 */
public record FamiliaMorosa(Long familiaId, String familia, long alumnos, BigDecimal monto, long dias) {
}
