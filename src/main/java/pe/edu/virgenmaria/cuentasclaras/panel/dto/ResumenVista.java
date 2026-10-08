package pe.edu.virgenmaria.cuentasclaras.panel.dto;

/**
 * Un resumen diario enviado, para el panel y la lista de resúmenes (sprint 6, tanda 2). Las cifras son las de la FOTO
 * (lo que recibió Promotoría); {@code cambio} dice si los libros de hoy ya no coinciden y por qué.
 *
 * @param envio            «Enviado 19:30 · Entregado», «Pendiente de envío», «No salió a todos»
 * @param salioATodos      si salió a todas las personas de Promotoría activas
 * @param cambio           {@code null} si los libros siguen diciendo lo mismo; si no, la explicación
 * @param cambioSinExplicar si el cambio no lo explica ninguna anulación ni pago tardío (CRÍTICA)
 */
public record ResumenVista(Long id, String fecha, String cobrado, long pagos, String efectivo, String cobradoMes,
		String deuda, long familiasMorosas, long alertasCriticas, String huella, String envio, boolean salioATodos,
		String cambio, boolean cambioSinExplicar) {
}
