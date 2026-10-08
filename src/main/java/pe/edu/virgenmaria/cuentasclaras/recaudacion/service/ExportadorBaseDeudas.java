package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.BancoRecaudacion;

import java.util.List;

/**
 * Puerto: la base de deudas que el colegio entrega al banco (una línea por cuota por pagar, con el código del alumno y
 * el de la cuota). Uno por banco; el genérico es un CSV.
 */
public interface ExportadorBaseDeudas {

	BancoRecaudacion banco();

	/** Nombre del archivo (sin datos personales). */
	String nombreArchivo();

	byte[] exportar(List<Cuota> porPagar);
}
