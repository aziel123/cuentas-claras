package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

/**
 * Pasarela de pagos en línea. SIMULADA solo existe en dev, test y piloto (y la base de prod la rechaza); las reales se
 * activan con su adaptador, sus llaves y su contrato.
 */
public enum ProveedorPasarela {
	SIMULADA, CULQI, IZIPAY, NIUBIZ
}
