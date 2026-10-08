package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

/** Un aviso autenticado de la pasarela: qué evento es y a qué orden se refiere (nunca dice si hubo dinero). */
public record AvisoPasarela(String eventoId, String tipo, String referenciaOrden, String proveedorOrdenId) {
}
