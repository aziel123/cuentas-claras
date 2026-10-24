package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.FotoLibreta;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 10, tanda 3 (E27, secciones 3.8 y 3.13 C1; INDECOPI y Ley 29571): LA LIBRETA NUNCA SE RETIENE POR DEUDA. Tres
 * familias de la misma sección: la de Rosa (Mateo y Valeria) con cuotas VENCIDAS, la de Pedro (Lucas) al día y la de Carmen
 * (Sofía) con la matrícula RESERVADA (impaga). Se cierra el periodo: las tres tienen su libreta, reciben el mismo aviso y la
 * descargan con 200; el texto del PDF no habla de pagos y la huella depende solo de las notas (pagar la deuda no la cambia).
 * La misma prueba corre en MySQL real con cc_app y cc_sistema ({@code AuditoriaSprint10LibretasMySqlTest}).
 */
class LibretaConDeudaTest extends PruebaLibretas {

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private LibretaEmitidaRepository libretas;

	@Autowired
	private org.springframework.transaction.PlatformTransactionManager transacciones;

	@Test
	void laFamiliaQueDebeLaQueEstaAlDiaYLaReservadaRecibenYDescarganSuLibreta() throws Exception {
		EscenarioCobranza.planAprobado(planes, escuela.anio2026(), 2026, Nivel.PRIMARIA, "350.00", "300.00",
				LocalDate.of(2026, 3, 1));
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long sofia = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234569", "Vega", "Ruiz", "Sofía",
				LocalDate.of(2015, 3, 1), "41234569", "Vega", "Soto", "Carmen", "987333444", "carmen.vega@gmail.com",
				seccion)).alumnoId();
		SecurityContextHolder.clearContext();
		EscenarioEscolar.contactosConfirmados(jdbc);
		Long matriculaSofia = matricula(sofia);
		// C1: la matrícula de Sofía queda RESERVADA (no pagó la matrícula). En H2 no hay triggers: se marca directo.
		jdbc.update("UPDATE matricula SET estado = 'RESERVADA' WHERE id = ?", matriculaSofia);
		Long carmen = jdbc.queryForObject("SELECT id FROM apoderado WHERE numero_documento = '41234569'", Long.class);
		// La de Pedro está al día; la de Rosa debe (cuotas vencidas a mayo).
		jdbc.update("UPDATE cuota SET estado = 'PAGADA', monto_pagado = monto WHERE alumno_id = ?", alumnoDe(lucas));
		assertThat(contar("SELECT COUNT(*) FROM cuota WHERE alumno_id = ? AND estado <> 'PAGADA' AND fecha_vencimiento < ?",
				alumnoDe(mateo), LocalDate.of(2026, 5, 4))).as("Mateo debe cuotas vencidas al cierre").isPositive();
		assertThat(contar("SELECT COUNT(*) FROM cuota WHERE alumno_id = ? AND estado <> 'PAGADA'", alumnoDe(lucas)))
				.as("Lucas está al día").isZero();
		// Ana pone A en Matemática a los cuatro (también a la reservada, C1) y Dirección cierra.
		guardar(ana, "MAT", hoja("MAT", List.of(mateo, valeria, lucas, matriculaSofia), "A", null));
		cerrarPeriodo();
		assertThat(emitir().alCierre()).isEqualTo(4);
		// Las tres familias tienen su libreta.
		for (Long m : List.of(mateo, valeria, lucas, matriculaSofia)) {
			assertThat(contar("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id = ? AND periodo_id = ?", m, periodo))
					.as("matrícula " + m).isEqualTo(1);
		}
		// El mismo aviso (salvo los nombres): sin montos ni palabras de cobranza.
		assertThat(avisar()).isEqualTo(3);
		Map<Long, String> porApoderado = Map.of(rosa, "Mateo y Valeria", pedro, "Lucas", carmen, "Sofía");
		String molde = null;
		for (Map.Entry<Long, String> e : porApoderado.entrySet()) {
			String parametros = jdbc.queryForObject("SELECT parametros FROM mensaje WHERE tipo = 'LIBRETA_DISPONIBLE' AND "
					+ "apoderado_id = ?", String.class, e.getKey());
			String sinNombre = parametros.replace(e.getValue(), "X");
			assertThat(sinNombre).doesNotContain("S/").doesNotContainIgnoringCase("deuda")
					.doesNotContainIgnoringCase("pensi").doesNotContainIgnoringCase("pago");
			if (molde == null) {
				molde = sinNombre;
			}
			assertThat(sinNombre).isEqualTo(molde);
		}
		// Las tres descargan con 200, con el PDF de sus notas.
		UsuarioAutenticado carmenEnLinea = new UsuarioAutenticado(42L, 1L, "carmen.familia", "Carmen Vega", null, true,
				false, false, EnumSet.of(Rol.APODERADO), carmen);
		Map<Long, UsuarioAutenticado> quien = Map.of(mateo, rosaEnLinea(), lucas, pedroEnLinea(), matriculaSofia,
				carmenEnLinea);
		for (Map.Entry<Long, UsuarioAutenticado> e : quien.entrySet()) {
			MockHttpServletResponse r = mvc.perform(get("/familia/libretas/" + libreta(e.getKey()) + ".pdf")
					.with(UsuariosDePrueba.como(e.getValue()))).andReturn().getResponse();
			assertThat(r.getStatus()).as("descarga de " + e.getValue().getUsername()).isEqualTo(200);
			String texto = texto(r.getContentAsByteArray());
			assertThat(texto).contains("Matemática").contains("Resuelve problemas de cantidad").doesNotContain("S/")
					.doesNotContainIgnoringCase("deuda").doesNotContainIgnoringCase("pensión");
			assertThat(pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza.hablaDeCobranza(texto))
					.as("el texto de la libreta no habla de cobranza").isFalse();
		}
		assertThat(contar("SELECT COUNT(*) FROM entrega_libreta WHERE canal = 'PORTAL'")).isEqualTo(3);
		// La huella depende solo de las notas: pagar toda la deuda no cambia la libreta de Mateo.
		String antes = huellaRegenerada(libreta(mateo));
		jdbc.update("UPDATE cuota SET estado = 'PAGADA', monto_pagado = monto WHERE alumno_id IN (?, ?)", alumnoDe(mateo),
				alumnoDe(valeria));
		assertThat(huellaRegenerada(libreta(mateo))).isEqualTo(antes)
				.isEqualTo(jdbc.queryForObject("SELECT huella_contenido FROM libreta_emitida WHERE id = ?", String.class,
						libreta(mateo)));
	}

	private String huellaRegenerada(Long libretaId) {
		return ContextoColegio.en(1L, () -> new org.springframework.transaction.support.TransactionTemplate(transacciones)
				.execute(t -> FotoLibreta.huella(foto.armar(libretas.findById(libretaId).orElseThrow()))));
	}

	private Long alumnoDe(Long matricula) {
		return jdbc.queryForObject("SELECT alumno_id FROM matricula WHERE id = ?", Long.class, matricula);
	}
}
