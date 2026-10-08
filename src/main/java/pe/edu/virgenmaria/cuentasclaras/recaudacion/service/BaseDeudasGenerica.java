package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.BancoRecaudacion;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Base de deudas en el formato genérico (CSV {@code ;}, UTF-8): código del alumno, código de la cuota (referencia de
 * deuda), nombre del alumno, concepto, vencimiento, saldo y moneda. Lleva el nombre del alumno: el contrato con el
 * banco debe tratarlo como encargado (Ley 29733, decisión 18).
 */
@Component
public class BaseDeudasGenerica implements ExportadorBaseDeudas {

	@Override
	public BancoRecaudacion banco() {
		return BancoRecaudacion.GENERICO;
	}

	@Override
	public String nombreArchivo() {
		return "base-de-deudas.csv";
	}

	@Override
	public byte[] exportar(List<Cuota> porPagar) {
		StringBuilder csv = new StringBuilder("codigo_alumno;referencia_deuda;alumno;concepto;vencimiento;saldo;moneda\n");
		for (Cuota cuota : porPagar) {
			csv.append(CodigoPago.deAlumno(cuota.getAlumno().getId())).append(';')
					.append(CodigoPago.deCuota(cuota.getId())).append(';')
					.append(limpio(cuota.getAlumno().nombreCompleto())).append(';')
					.append(limpio(cuota.getDescripcion())).append(';')
					.append(cuota.getFechaVencimiento()).append(';')
					.append(cuota.saldo().toPlainString()).append(";PEN\n");
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Sin separadores ni comillas que rompan el CSV ni fórmulas que Excel ejecute al abrirlo. */
	static String limpio(String texto) {
		String sinSeparadores = texto == null ? "" : texto.replaceAll("[;\"\\r\\n]", " ").strip();
		return sinSeparadores.matches("^[=+\\-@].*") ? "'" + sinSeparadores : sinSeparadores;
	}
}
