package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5, G2 (outbox en la MISMA transacción): si el aviso a la familia no se puede crear, el pago NO se registra:
 * ni el pago, ni su boleta, ni su aplicación a la cuota, ni el número de la serie.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class SinMensajeNoHayPagoTest {

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void sinMensajeNoHayPago() {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		// Un celular que la base no admite como destino de un mensaje (CHECK ck_mensaje_destino): el aviso no se crea.
		String celular = jdbc.queryForObject("SELECT telefono_whatsapp FROM apoderado WHERE id = ?", String.class, f.rosa());
		jdbc.update("UPDATE apoderado SET telefono_whatsapp = '12-34', telefono_verificado = '12-34' WHERE id = ?",
				f.rosa());
		como(EscenarioCaja.CAJA);

		assertThatThrownBy(() -> cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00")))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

		assertThat(contar(jdbc, "pago")).isZero();
		assertThat(contar(jdbc, "comprobante")).isZero();
		assertThat(contar(jdbc, "aplicacion_pago")).isZero();
		assertThat(contar(jdbc, "mensaje")).isZero();
		assertThat(EscenarioCaja.estado(jdbc, marzo)).isEqualTo("PENDIENTE");

		// Con el aviso funcionando, el mismo cobro sí se registra con su mensaje.
		jdbc.update("UPDATE apoderado SET telefono_whatsapp = ?, telefono_verificado = ? WHERE id = ?", celular, celular,
				f.rosa());
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(marzo), "450.00", "450.00"));
		assertThat(contar(jdbc, "mensaje WHERE entidad = 'pago' AND entidad_id = " + pago)).isEqualTo(1);
	}
}
