package pe.edu.virgenmaria.cuentasclaras.panel.dto;

/**
 * Una familia morosa en la lista del celular: apellido de la familia, cuántos hijos con deuda, monto vencido, días de la
 * cuota más antigua y el último aviso entregado. Sin celulares ni documentos (para llamar se entra a la ficha).
 */
public record FamiliaMorosaVista(Long familiaId, String familia, long alumnos, String monto, long dias,
		String ultimoAviso) {
}
