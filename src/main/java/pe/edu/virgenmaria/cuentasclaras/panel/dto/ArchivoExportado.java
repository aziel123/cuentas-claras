package pe.edu.virgenmaria.cuentasclaras.panel.dto;

/**
 * Un Excel ya registrado en la bitácora: nombre fijo sin datos personales, el contenido y el código de exportación que
 * va impreso en su hoja «Control».
 */
public record ArchivoExportado(String nombre, byte[] contenido, String codigo) {
}
