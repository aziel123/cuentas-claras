package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7, tanda 3 (A03 de OWASP): el archivo original del banco se descarga con un nombre fijo, nunca con el que escribió
 * quien lo subió (podría traer «../», comillas, saltos de línea o un nombre engañoso).
 */
class NombreDescargaTest {

	@Test
	void elNombreDeLaDescargaEsFijoYSoloConservaUnaExtensionPermitida() {
		assertThat(ConsultaRecaudacion.nombreDescarga(7L, "../../etc/passwd.csv")).isEqualTo("recaudacion-lote-7.csv");
		assertThat(ConsultaRecaudacion.nombreDescarga(7L, "banco \"oficial\"\r\nX-Inyeccion: 1.XLSX"))
				.isEqualTo("recaudacion-lote-7.xlsx");
		assertThat(ConsultaRecaudacion.nombreDescarga(8L, "recaudación.txt")).isEqualTo("recaudacion-lote-8.txt");
		assertThat(ConsultaRecaudacion.nombreDescarga(9L, "archivo.exe")).isEqualTo("recaudacion-lote-9.csv");
		assertThat(ConsultaRecaudacion.nombreDescarga(9L, null)).isEqualTo("recaudacion-lote-9.csv");
	}
}
