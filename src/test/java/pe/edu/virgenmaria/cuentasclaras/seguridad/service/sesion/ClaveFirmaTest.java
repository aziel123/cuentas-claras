package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 7, tanda 2 (sección 3.4): la clave que firma la aplicación es la que arma el trigger con CONCAT. Si una de las
 * dos cambia, en MySQL ninguna aprobación de esa tabla pasaría: esta prueba lo detecta sin MySQL.
 */
class ClaveFirmaTest {

	/** El primer literal de cada CONCAT que 03-triggers.sql pasa a cc_firma_valida (también dentro de un CASE). */
	private static final Pattern PREFIJO = Pattern.compile("CONCAT\\('([a-z_]+:(?:[A-Z]+:)?)'");

	private static Set<String> prefijosDeLosTriggers() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/03-triggers.sql"), StandardCharsets.UTF_8);
		Set<String> prefijos = new TreeSet<>();
		int desde = 0;
		while ((desde = script.indexOf("cc_firma_valida(", desde)) >= 0) {
			int fin = script.indexOf("SIGNAL", desde);
			Matcher m = PREFIJO.matcher(script.substring(desde, fin < 0 ? script.length() : fin));
			while (m.find()) {
				prefijos.add(m.group(1));
			}
			desde++;
		}
		return prefijos;
	}

	@Test
	void lasClavesDeLaAplicacionSonLasDeLosTriggers() throws IOException {
		LocalDateTime momento = LocalDateTime.of(2026, 10, 5, 9, 30, 15, 123_456_789);
		Set<String> deLaAplicacion = new TreeSet<>();
		Stream.of(ClaveFirma.solicitud(1L, EstadoSolicitud.APROBADA), ClaveFirma.descuento(1L, EstadoSolicitud.APROBADA),
				ClaveFirma.cierreCaja(1L, EstadoSolicitud.APROBADA), ClaveFirma.planPension(1L),
				ClaveFirma.loteSaldoInicialConfirmado(1L), ClaveFirma.loteSaldoInicialDevuelto(1L, momento),
				ClaveFirma.extracto(1L, EstadoSolicitud.APROBADA), ClaveFirma.loteRecaudacion(1L, EstadoSolicitud.APROBADA),
				ClaveFirma.partida(1L, EstadoSolicitud.APROBADA), ClaveFirma.cierreMensual(1L, 1), ClaveFirma.feriado(1L),
				ClaveFirma.avisoFamilia(1L), ClaveFirma.renovacion(1L, EstadoSolicitud.APROBADA),
				ClaveFirma.verificacionPago(1L, 1), ClaveFirma.verificacionDeposito(1L, 1),
				ClaveFirma.llamadaControl(LocalDate.of(2026, 10, 5), 1L, 1), ClaveFirma.delegacionLlamada(LocalDate.of(2026, 10, 5)),
				ClaveFirma.respaldoResuelto(1L, 1L), ClaveFirma.primeraPromotoria(1L, 1), ClaveFirma.colegioProduccion(1L),
				ClaveFirma.configuracionColegio(1L, "huella_correo_externo", 0), ClaveFirma.cambioContactoTitularPropuesto(1L, 1L, 1),
				// Sprint 8, tanda 2 (V29): la propuesta y el rechazo de un conector.
				ClaveFirma.conectorPropuesto("0f0e0d0c-0b0a-4908-8706-050403020100"), ClaveFirma.conectorRechazado(1L),
				// Correcciones del sprint 8 (V31): el contacto del titular, la ficha y el correo de control.
				ClaveFirma.cambioContactoTitular(1L, EstadoSolicitud.APROBADA), ClaveFirma.fichaColegio(1L, 0),
				ClaveFirma.cambioCorreoControlPropuesto(1L, 1), ClaveFirma.cambioCorreoControl(1L, EstadoSolicitud.APROBADA),
				// Sprint 9, tanda 1 (V32): terminar una asignación docente.
				ClaveFirma.asignacionTerminada(1L),
				// Sprint 9, tanda 2 (V33): registrar, corregir, justificar y revertir la asistencia; la configuración.
				ClaveFirma.asistenciaRegistrada(1L, LocalDate.of(2026, 10, 5)), ClaveFirma.marcaCorregida(1L),
				ClaveFirma.marcaJustificada(1L), ClaveFirma.marcaRevertida(1L), ClaveFirma.configuracionAcademica(1L, 0),
				// Sprint 9, tanda 3 (V34): enviar un comunicado (por la huella de su contenido) y retirarlo.
				ClaveFirma.comunicadoEnviar("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
				ClaveFirma.comunicadoRetirado(1L),
				// Sprint 10, tanda 1 (V35): la evaluación del año y los periodos (programar, fechas, abrir y cerrar).
				ClaveFirma.configuracionEvaluacion(1L, 0), ClaveFirma.periodoProgramado(1L, 1),
				ClaveFirma.periodoFechas(1L, 1), ClaveFirma.periodoAbierto(1L), ClaveFirma.periodoCerrado(1L),
				// Sprint 10, tanda 2 (V36): el lote de notas y la exoneración.
				ClaveFirma.loteCalificacion(1L, 2L, 3L, 1), ClaveFirma.exoneracionArea(1L, 2L),
				// Sprint 10, tanda 3 (V37, M12): la delegación de la exportación para el SIAGIE.
				ClaveFirma.delegacionSiagie(1L, LocalDate.of(2026, 10, 5)),
				// Sprint 11, tanda 1 (V39): el cambio de estado del colegio, el año escolar y la facultad del titular.
				ClaveFirma.cambioEstadoPropuesto(1L, 1), ClaveFirma.cambioEstado(1L, "APROBADO"),
				ClaveFirma.anioEscolarCreado(2027), ClaveFirma.anioEscolarCambiado(1L, 0), ClaveFirma.facultadTitular(1L, 1),
				// Sprint 11, tanda 2 (V41): la exportación a pedido, la conservación, la retención legal y el acta.
				ClaveFirma.exportacionPedida(1L, 1), ClaveFirma.conservacion(1L, 1), ClaveFirma.conservacionRetirada(1L, 1),
				ClaveFirma.conservacionFrenada(1L, 1), ClaveFirma.retencionLegal(1L, 1),
				ClaveFirma.retencionLegalRetirada(1L, 1), ClaveFirma.actaSupresion(1L),
				ClaveFirma.actaSupresionAprobada(1L))
				.forEach(clave -> {
					// El CHECK de V25 (firma_operacion.clave) y el largo de la columna.
					assertThat(clave).matches("^[a-z_]+:[A-Za-z0-9:_-]+$").hasSizeLessThanOrEqualTo(120);
					Matcher m = Pattern.compile("^([a-z_]+:(?:PAGO:|DEPOSITO:)?)").matcher(clave);
					assertThat(m.find()).isTrue();
					deLaAplicacion.add(m.group(1));
				});

		assertThat(deLaAplicacion).isEqualTo(prefijosDeLosTriggers());
	}

	/** DATE_FORMAT(devuelto_en, '%Y%m%d%H%i%s%f'): al microsegundo, como DATETIME(6). */
	@Test
	void laDevolucionLlevaElMomentoAlMicrosegundo() {
		assertThat(ClaveFirma.loteSaldoInicialDevuelto(7L, LocalDateTime.of(2026, 10, 5, 9, 3, 5, 120_456_789)))
				.isEqualTo("lote_saldo_inicial:7:DEVUELTO:20261005090305120456");
		assertThat(ClaveFirma.llamadaControl(LocalDate.of(2026, 10, 5), 3L, 2)).isEqualTo("llamada_control:2026-10-05:3:2");
	}

	@Test
	void sinIdNoHayClave() {
		assertThatThrownBy(() -> ClaveFirma.planPension(null)).isInstanceOf(NullPointerException.class);
	}
}
