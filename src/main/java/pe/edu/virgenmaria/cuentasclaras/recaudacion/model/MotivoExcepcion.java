package pe.edu.virgenmaria.cuentasclaras.recaudacion.model;

/**
 * Por qué una línea del banco no se aplicó sola. Se guarda por nombre ({@code VARCHAR(30)}): nunca renombres un valor.
 * PAGO_PARCIAL solo aparece si {@code cuentasclaras.recaudacion.aceptar-parciales} es {@code false}.
 */
public enum MotivoExcepcion {

	CODIGO_INVALIDO("El código de pago no corresponde a ningún alumno (dígito verificador errado o alumno de otro "
			+ "colegio)", false),
	ALUMNO_SIN_DEUDA("El alumno no tiene cuotas por pagar", false),
	CUOTA_NO_COBRABLE("La cuota indicada por el banco no es de ese alumno o ya no está por pagar", false),
	EXCESO("El monto supera lo que se debe", false),
	OPERACION_DUPLICADA("El número de operación ya está registrado en otro pago (posible doble registro)", true),
	MONEDA("El pago llegó en una moneda que no es soles: no se convierte", true),
	PAGO_PARCIAL("El monto no cubre la cuota y los pagos parciales por banco están desactivados", false);

	private final String descripcion;

	private final boolean critico;

	MotivoExcepcion(String descripcion, boolean critico) {
		this.descripcion = descripcion;
		this.critico = critico;
	}

	public String descripcion() {
		return descripcion;
	}

	/** Las que sugieren un registro doble o un error grave del banco van en rojo. */
	public boolean critico() {
		return critico;
	}
}
