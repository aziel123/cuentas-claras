package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

/** Por qué un pago en línea confirmado no se aplicó solo y quedó por revisar (hubo dinero: no se pierde). */
public enum MotivoRevision {

	CUOTA_NO_COBRABLE("Alguna cuota ya no se podía cobrar (se pagó en caja, se anuló o tiene una anulación pendiente)", false),
	MONTO_CAMBIO("El saldo de alguna cuota cambió mientras se pagaba (por ejemplo, un descuento)", false),
	MONTO_DISTINTO("La pasarela confirmó un monto distinto del de la orden", true),
	MONEDA_DISTINTA("La pasarela confirmó otra moneda", true),
	OPERACION_DUPLICADA("El número de operación ya está registrado en otro pago", true),
	CONTRACARGO("El apoderado desconoció el cargo ante su banco", true);

	private final String descripcion;

	private final boolean critico;

	MotivoRevision(String descripcion, boolean critico) {
		this.descripcion = descripcion;
		this.critico = critico;
	}

	public String descripcion() {
		return descripcion;
	}

	/** Alerta CRÍTICA (monto, moneda u operación); ATENCIÓN para un doble pago (cuota ya pagada o saldo cambiado). */
	public boolean critico() {
		return critico;
	}
}
