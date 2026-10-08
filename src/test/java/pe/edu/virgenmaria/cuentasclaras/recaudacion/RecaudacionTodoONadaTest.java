package pe.edu.virgenmaria.cuentasclaras.recaudacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.Archivo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRecaudacion.archivo;

/** Registrar un archivo del banco es todo o nada: si algo falla al final, no queda ni el archivo, ni el lote, ni líneas. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RecaudacionTodoONadaTest {

	@MockitoSpyBean
	private AuditoriaService auditoria;

	@Autowired
	private ServicioRecaudacion servicio;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void registrarEsTodoONada() {
		Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		Archivo banco = archivo().pago(f.mateo(), null, "350.00", "BCP80001").pago(f.valeria(), null, "350.00", "BCP80002");
		como(ADMINISTRACION);
		VistaPreviaRecaudacion previa = servicio.previsualizar("banco.csv", banco.csv(), banco.csv().length);
		long eventos = contar(jdbc, "evento_auditoria");
		doThrow(new IllegalStateException("falla simulada al terminar")).when(auditoria)
				.registrar(eq(AccionAuditoria.RECAUDACION_CARGADA), any(), any(), any(), any(), any());

		assertThatThrownBy(() -> servicio.registrar(previa, previa.token())).isInstanceOf(IllegalStateException.class)
				.hasMessage("falla simulada al terminar");

		for (String tabla : new String[] { "archivo_cargado", "lote_recaudacion", "linea_recaudacion" }) {
			assertThat(contar(jdbc, tabla)).as(tabla).isZero();
		}
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventos);
	}
}
