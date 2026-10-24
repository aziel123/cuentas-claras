package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.proceso.RevisionCatalogo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.AlertasCatalogo;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 1 (M10): la revisión diaria del catálogo (05:00). Con el catálogo como el del jar no deja nada; con
 * diferencias, {@code sistema.academico} las deja en la bitácora de cada colegio (una vez por día) y Promotoría recibe la
 * alerta CRÍTICA {@code INTEGRIDAD_CATALOGO}, que también sale al celular.
 */
@PruebaIntegracion
class RevisionCatalogoTest {

	@Autowired
	private RevisionCatalogo revision;

	@Autowired
	private AlertasCatalogo alertas;

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

	@Test
	void conElCatalogoIntactoNoAvisa() {
		assertThat(revision.revisar()).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = ?", Long.class,
				AccionAuditoria.CATALOGO_NO_COINCIDE.name())).isZero();
		EscenarioCobranza.como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).isEmpty();
	}

	@Test
	void conDiferenciasAlertaAPromotoriaUnaVezPorDia() {
		List<String> diferencias = List.of("El catálogo CNEB-2016.1 no coincide con el del jar.");
		assertThat(revision.avisar(diferencias)).isPositive();
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, AccionAuditoria.CATALOGO_NO_COINCIDE.name());
		assertThat(evento.get("NOMBRE_USUARIO")).isEqualTo("sistema.academico");
		assertThat(evento.get("DETALLE").toString()).contains("CNEB-2016.1 no coincide");
		assertThat(AccionAuditoria.CATALOGO_NO_COINCIDE.requiereAtencion()).isTrue();
		// El mismo día no se repite.
		assertThat(revision.avisar(diferencias)).isZero();

		EscenarioCobranza.como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.INTEGRIDAD_CATALOGO);
			assertThat(a.difundible()).isTrue();
			assertThat(a.texto()).contains("catálogo del currículo");
		});
	}
}
