package pe.edu.virgenmaria.cuentasclaras.conectores;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.OseDelColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CuentaRequest;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.BancoCuenta;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.RecaudacionEnConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.Pasarelas;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.BancoRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.ModalidadRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.RecaudacionDelColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8, tanda 2 (sección 3.6, H4): los módulos que mueven el dinero o emiten usan el conector VIGENTE de SU colegio y,
 * sin conector, el entorno (como hasta el sprint 7): la caja deposita en la cuenta bancaria del conector (y la conciliación
 * no registra otra), el OSE emite con las series del conector, la recaudación lee el formato y la modalidad del conector,
 * y la pasarela del colegio y su monto máximo salen del conector.
 */
@PruebaIntegracion
class ConectoresEnLosModulosTest {

	@Autowired
	private ServicioCierreCaja cierres;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private OseDelColegio ose;

	@Autowired
	private RecaudacionDelColegio recaudacion;

	@Autowired
	private RecaudacionEnConciliacion enConciliacion;

	@Autowired
	private Pasarelas pasarelas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void vigente(String tipo, String proveedor, String modo, String configuracion) {
		LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Lima"));
		jdbc.update("INSERT INTO conector_colegio (colegio_id, tipo, proveedor, modo, configuracion, clave_publica, estado, "
				+ "vigente, propuesto_por, resuelto_por, resuelto_en, prueba_ok, creado_en, creado_por, actualizado_en) VALUES "
				+ "(1, ?, ?, ?, ?, ?, 'VIGENTE', TRUE, 'a.prueba', 'b.prueba', ?, TRUE, ?, 'a.prueba', ?)", tipo, proveedor, modo,
				configuracion, UUID.randomUUID().toString(), ahora, ahora, ahora);
	}

	@Test
	void laCajaDepositaEnLaCuentaDelConectorYLaConciliacionNoRegistraOtra() {
		Usuario caja = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.modulos", UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.modulos", UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(caja);
		// Sin conector: la cuenta del entorno (cuentasclaras.caja.cuentas-deposito).
		assertThat(cierres.estado().cuentas()).containsExactly("BCP Soles 191-XXXXXXX-0-XX");

		EscenarioPuestaEnMarcha.cuentaBancariaVigente(jdbc, 1L, "promotora.modulos", "directora.modulos");
		assertThat(cierres.estado().cuentas()).containsExactly("BCP 1912345678901");

		UsuariosDePrueba.iniciarSesion(promotora);
		assertThatThrownBy(() -> cuentas.registrar(new CuentaRequest(BancoCuenta.BBVA, "0011-0123-0100012345", "Otra")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Conectores");
		assertThat(cuentas.registrar(new CuentaRequest(BancoCuenta.BCP, "1912345678901", "Cuenta del colegio"))).isNotNull();
	}

	@Test
	void elOseEmiteConLasSeriesYElProveedorDeSuConector() {
		ContextoColegio.en(1L, () -> {
			assertThat(ose.actual().serieDe(TipoComprobante.BOLETA)).isEqualTo("B001");
			vigente("OSE", "SIMULADO", "SIMULADO", "{\"ruta\":null,\"rucEmisor\":\"20600000013\",\"serieBoleta\":\"B004\","
					+ "\"serieFactura\":\"F004\",\"serieNotaBoleta\":\"BC04\",\"serieNotaFactura\":\"FC04\","
					+ "\"afectacionIgv\":\"EXONERADO\"}");
			assertThat(ose.actual().serieDe(TipoComprobante.BOLETA)).isEqualTo("B004");
			assertThat(ose.actual().proveedor()).isEqualTo(ProveedorComprobantes.SIMULADO);
			assertThat(ose.seriesConocidas()).contains("B001", "B004", "FC04");
			return null;
		});
	}

	@Test
	void laRecaudacionYLaConciliacionLeenSuConector() {
		ContextoColegio.en(1L, () -> {
			assertThat(recaudacion.banco()).isEqualTo(BancoRecaudacion.GENERICO);
			assertThat(recaudacion.modalidad()).isEqualTo(ModalidadRecaudacion.CON_BASE_DE_DEUDAS);
			vigente("RECAUDACION", "GENERICO", "REAL", "{\"modalidad\":\"SIN_BASE\",\"patronAbono\":\"RECAUDACION CC\","
					+ "\"abono\":\"POR_PAGO\"}");
			assertThat(recaudacion.modalidad()).isEqualTo(ModalidadRecaudacion.SIN_BASE);
			assertThat(enConciliacion.patronAbono()).isEqualTo("RECAUDACION CC");
			assertThat(enConciliacion.abono()).isEqualTo(PropiedadesConciliacion.AbonoRecaudacion.POR_PAGO);
			return null;
		});
	}

	@Test
	void laPasarelaYSuMontoMaximoSalenDelConector() {
		ContextoColegio.en(1L, () -> {
			assertThat(pasarelas.montoMaximo()).isEqualByComparingTo("5000.00");
			assertThat(pasarelas.configurada()).isPresent();
			vigente("PASARELA", "CULQI", "PRUEBAS", "{\"llavePublica\":\"pk_test_123\",\"medios\":[\"YAPE\"],"
					+ "\"montoMaximo\":1200.00}");
			// Culqi aún no tiene adaptador: el colegio con ese conector no tiene pago en línea (no se usa la del entorno).
			assertThat(pasarelas.configurada()).isEmpty();
			assertThat(pasarelas.montoMaximo()).isEqualByComparingTo(new BigDecimal("1200.00"));
			return null;
		});
	}
}
