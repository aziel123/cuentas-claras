package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

/** La orden ya existe en la pasarela: su id allí y el enlace a su página de pago (alojada por la pasarela). */
public record OrdenCreada(String proveedorOrdenId, String urlPago) {
}
