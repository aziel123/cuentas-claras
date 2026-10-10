package pe.edu.virgenmaria.cuentasclaras.operacion.service;

import java.util.List;

/**
 * Lo que ve Promotoría en {@code /panel/sistema} (sprint 7, sección 10.1), ya en texto: sin datos personales ni mensajes
 * de error (solo huellas y conteos).
 */
public record VistaSistema(String respaldo, String respaldoDetalle, boolean respaldoAlDia, boolean faltanFilas,
		boolean respaldoExigido, boolean alertasEncendidas, boolean tareasActivas, List<Proceso> procesos,
		List<ErrorDeHoy> errores, boolean bitacoraAlDia, String disco, boolean discoBajo, boolean baseResponde,
		boolean poolAgotado, String version) {

	public record Proceso(String nombre, String programacion, String ultimoExito, boolean atrasado, boolean critico) {
	}

	public record ErrorDeHoy(String huella, long cantidad, String ultimo, String idPeticion) {
	}

	public long procesosAtrasados() {
		return procesos.stream().filter(Proceso::atrasado).count();
	}
}
