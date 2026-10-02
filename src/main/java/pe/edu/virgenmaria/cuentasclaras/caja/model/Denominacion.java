package pe.edu.virgenmaria.cuentasclaras.caja.model;

import java.math.BigDecimal;

/** Billetes y monedas en circulación (BCRP; la moneda de 5 céntimos ya no circula desde 2019). */
public enum Denominacion {

	B200("200.00", "Billetes de S/ 200"),
	B100("100.00", "Billetes de S/ 100"),
	B50("50.00", "Billetes de S/ 50"),
	B20("20.00", "Billetes de S/ 20"),
	B10("10.00", "Billetes de S/ 10"),
	M5("5.00", "Monedas de S/ 5"),
	M2("2.00", "Monedas de S/ 2"),
	M1("1.00", "Monedas de S/ 1"),
	M050("0.50", "Monedas de 50 céntimos"),
	M020("0.20", "Monedas de 20 céntimos"),
	M010("0.10", "Monedas de 10 céntimos");

	private final BigDecimal valor;

	private final String etiqueta;

	Denominacion(String valor, String etiqueta) {
		this.valor = new BigDecimal(valor);
		this.etiqueta = etiqueta;
	}

	public BigDecimal valor() {
		return valor;
	}

	public String etiqueta() {
		return etiqueta;
	}
}
