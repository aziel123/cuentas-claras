package pe.edu.virgenmaria.cuentasclaras.recaudacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.BancoRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.ModalidadRecaudacion;

/**
 * {@code cuentasclaras.recaudacion}: el banco cuyo formato se lee (GENERICO hasta tener el adaptador del banco del
 * colegio), la modalidad, cuántos totales a ciegas distintos rechazan un lote, si se aceptan pagos parciales, cuántas
 * líneas se aplican por transacción, el máximo de líneas de un archivo y cuántas líneas al azar ve quien confirma. Un
 * valor imposible impide arrancar.
 */
@ConfigurationProperties("cuentasclaras.recaudacion")
public record PropiedadesRecaudacion(
		@DefaultValue("GENERICO") BancoRecaudacion banco,
		@DefaultValue("CON_BASE_DE_DEUDAS") ModalidadRecaudacion modalidad,
		@DefaultValue("2") int intentosConfirmacion,
		@DefaultValue("true") boolean aceptarParciales,
		@DefaultValue("50") int lineasPorTransaccion,
		@DefaultValue("2000") int maximoLineas,
		@DefaultValue("3") int muestreo) {

	public PropiedadesRecaudacion {
		if (banco == null) {
			banco = BancoRecaudacion.GENERICO;
		}
		if (modalidad == null) {
			modalidad = ModalidadRecaudacion.CON_BASE_DE_DEUDAS;
		}
		if (intentosConfirmacion < 1 || intentosConfirmacion > 5) {
			throw new IllegalArgumentException("cuentasclaras.recaudacion.intentos-confirmacion debe estar entre 1 y 5");
		}
		if (lineasPorTransaccion < 1 || lineasPorTransaccion > 500) {
			throw new IllegalArgumentException("cuentasclaras.recaudacion.lineas-por-transaccion debe estar entre 1 y 500");
		}
		if (maximoLineas < 1 || maximoLineas > 20000) {
			throw new IllegalArgumentException("cuentasclaras.recaudacion.maximo-lineas debe estar entre 1 y 20000");
		}
		if (muestreo < 1 || muestreo > 10) {
			throw new IllegalArgumentException("cuentasclaras.recaudacion.muestreo debe estar entre 1 y 10");
		}
	}

	public static PropiedadesRecaudacion porDefecto() {
		return new PropiedadesRecaudacion(BancoRecaudacion.GENERICO, ModalidadRecaudacion.CON_BASE_DE_DEUDAS, 2, true, 50,
				2000, 3);
	}
}
