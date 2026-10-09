package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7, tanda 2 (sección 3.8, H6): lo que {@code SHOW GRANTS} muestra para cc_app y cc_sistema debe estar en
 * 02-permisos-tablas.sql. Un privilegio de más (dado a mano, de un sprint anterior o un rol extra) no deja arrancar.
 */
class PermisosEsperadosTest {

	private static final String APP = "`cc_app`@`%`";

	private static final String SISTEMA = "`cc_sistema`@`%`";

	@Test
	void loQueDa02NoSobra() {
		assertThat(PermisosEsperados.de("cc_app").deMas(List.of("GRANT USAGE ON *.* TO " + APP,
				"GRANT SELECT ON `cuentasclaras`.* TO " + APP,
				"GRANT EXECUTE ON FUNCTION `cuentasclaras`.`triggers_instalados` TO " + APP,
				"GRANT INSERT ON `cuentasclaras`.`firma_operacion` TO " + APP,
				"GRANT INSERT, UPDATE (`estado`, `monto_pagado`, `actualizado_en`, `version`) ON `cuentasclaras`.`cuota` TO "
						+ APP,
				"GRANT `cc_negocio`@`%` TO " + APP))).isEmpty();

		assertThat(PermisosEsperados.de(PermisosEsperados.SISTEMA).deMas(List.of("GRANT USAGE ON *.* TO " + SISTEMA,
				"GRANT INSERT, UPDATE ON `cuentasclaras`.`usuario` TO " + SISTEMA,
				"GRANT INSERT, DELETE ON `cuentasclaras`.`usuario_rol` TO " + SISTEMA,
				"GRANT INSERT, UPDATE (`cerrada_en`, `motivo_cierre`) ON `cuentasclaras`.`sesion_usuario` TO " + SISTEMA,
				"GRANT INSERT ON `cuentasclaras`.`semilla_muestreo` TO " + SISTEMA,
				"GRANT EXECUTE ON FUNCTION `cuentasclaras`.`huellas_objetos` TO " + SISTEMA,
				"GRANT `cc_negocio`@`%` TO " + SISTEMA))).isEmpty();
	}

	@Test
	void loQue02NoDaSobra() {
		List<String> deMas = List.of("GRANT DELETE ON `cuentasclaras`.`pago` TO " + APP,
				"GRANT TRIGGER ON `cuentasclaras`.* TO " + APP,
				"GRANT UPDATE (`monto`) ON `cuentasclaras`.`cuota` TO " + APP,
				// La identidad es de cc_sistema: cc_app ya no la escribe.
				"GRANT INSERT ON `cuentasclaras`.`usuario` TO " + APP,
				"GRANT INSERT ON `cuentasclaras`.`sesion_usuario` TO " + APP,
				"GRANT INSERT ON `cuentasclaras`.`semilla_muestreo` TO " + APP,
				"GRANT UPDATE ON `cuentasclaras`.`comprobante` TO " + APP,
				"GRANT SUPER ON *.* TO " + APP, "GRANT PROXY ON ``@`` TO " + APP,
				"GRANT SELECT ON `mysql`.* TO " + APP,
				"GRANT EXECUTE ON FUNCTION `cuentasclaras`.`cc_es_sistema` TO " + APP,
				"GRANT `cc_negocio`@`%`,`dba`@`%` TO " + APP);

		assertThat(PermisosEsperados.de("cc_app").deMas(deMas)).containsExactlyElementsOf(deMas);
		// Ni cc_sistema edita la bitácora.
		assertThat(PermisosEsperados.de(PermisosEsperados.SISTEMA).deMas(List.of(
				"GRANT UPDATE ON `cuentasclaras`.`evento_auditoria` TO " + SISTEMA,
				"GRANT DELETE ON `cuentasclaras`.`sesion_usuario` TO " + SISTEMA))).hasSize(2);
	}
}
