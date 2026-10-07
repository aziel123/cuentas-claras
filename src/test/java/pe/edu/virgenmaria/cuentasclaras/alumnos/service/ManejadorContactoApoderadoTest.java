package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5 (G5, hallazgo 4): al aprobarse el cambio de celular o correo de un apoderado, el apoderado guarda la
 * solicitud que lo aprobó ({@code contacto_solicitud_id}) y el contacto ANTERIOR recibe el aviso («dejó de recibir los
 * avisos del colegio»): si un empleado desvió los avisos a su número, la familia se entera.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ManejadorContactoApoderadoTest {

	@Autowired
	private ServicioFamilias familias;

	@Autowired
	private BandejaAprobaciones bandeja;

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
	void elCambioAvisaAlContactoAnterior() {
		como(ADMINISTRACION);
		familias.actualizarApoderado(f.rosa(), new ApoderadoRequest(TipoDocumento.DNI, EscenarioEscolar.DNI_ROSA, "Huamán",
				"Ccori", "Rosa", Parentesco.MADRE, "999 888 777", "rosa.nueva@gmail.com",
				"Perdió su celular; dio el número nuevo en persona"));
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE tipo = 'CAMBIO_CONTACTO_APODERADO'",
				Long.class);
		assertThat(contar(jdbc, "mensaje")).as("pedir no avisa: nada cambió todavía").isZero();

		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "apoderado",
				f.rosa());
		SecurityContextHolder.clearContext();

		assertThat(jdbc.queryForMap("SELECT telefono_whatsapp, correo, contacto_solicitud_id FROM apoderado WHERE id = ?",
				f.rosa())).containsEntry("telefono_whatsapp", "+51999888777").containsEntry("correo", "rosa.nueva@gmail.com")
				.containsEntry("contacto_solicitud_id", solicitud);
		List<Map<String, Object>> avisos = jdbc.queryForList("SELECT canal, destino, entidad, entidad_id, parametros "
				+ "FROM mensaje WHERE tipo = 'CONTACTO_CAMBIADO' ORDER BY canal");
		assertThat(avisos).extracting(m -> m.get("destino")).containsExactly(EscenarioEscolar.CORREO_ROSA,
				"+51" + EscenarioEscolar.CELULAR_ROSA);
		assertThat(avisos).allSatisfy(m -> assertThat(m).containsEntry("entidad", "solicitud_cambio")
				.containsEntry("entidad_id", solicitud));
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'CONTACTO_CAMBIADO_AVISADO'")).isEqualTo(2);
		// La bitácora guarda el contacto anterior enmascarado (Ley 29733).
		assertThat(contar(jdbc, "evento_auditoria WHERE valor_nuevo LIKE '%987654321%'")).isZero();
	}

	@Test
	void siSoloCambiaElCorreoElCelularNoRecibeAviso() {
		como(ADMINISTRACION);
		familias.actualizarApoderado(f.rosa(), new ApoderadoRequest(TipoDocumento.DNI, EscenarioEscolar.DNI_ROSA, "Huamán",
				"Ccori", "Rosa", Parentesco.MADRE, EscenarioEscolar.CELULAR_ROSA, "rosa.nueva@gmail.com",
				"Cambió de correo personal"));
		Long solicitud = jdbc.queryForObject("SELECT id FROM solicitud_cambio", Long.class);
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(DIRECCION, bandeja, jdbc, "apoderado",
				f.rosa());
		SecurityContextHolder.clearContext();

		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'CONTACTO_CAMBIADO'", String.class))
				.containsExactly(EscenarioEscolar.CORREO_ROSA);
	}
}
