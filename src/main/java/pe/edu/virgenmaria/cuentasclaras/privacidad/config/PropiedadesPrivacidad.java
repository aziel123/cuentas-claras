package pe.edu.virgenmaria.cuentasclaras.privacidad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ley 29733 ({@code cuentasclaras.privacidad.*}; sprint 7, tanda 3, sección 11). Los plazos de los pedidos y el de los
 * contactos de las familias que se fueron salen de fuentes secundarias: los confirma el asesor legal (decisiones 97 a 99).
 *
 * @param versionAviso           versión vigente del aviso de privacidad (la que acepta la familia al activar su cuenta)
 * @param accesosUmbralDiario    fichas que una persona puede ver en un día antes de la alerta de ATENCIÓN (decisión 96)
 * @param plazoAccesoDiasHabiles plazo para responder un pedido de ACCESO (decisión 97)
 * @param plazoOtrosDiasHabiles  plazo para rectificación, cancelación y oposición (decisión 97)
 * @param avisoDiasHabiles       días hábiles sin atender tras los que el pedido es ATENCIÓN
 * @param contactosMeses         meses tras la salida sin deuda después de los cuales los contactos de una familia pasan
 *                               al reporte «Datos con plazo vencido» (decisión 98)
 */
@ConfigurationProperties("cuentasclaras.privacidad")
public record PropiedadesPrivacidad(@DefaultValue("2027-01") String versionAviso,
		@DefaultValue("50") int accesosUmbralDiario, @DefaultValue("20") int plazoAccesoDiasHabiles,
		@DefaultValue("10") int plazoOtrosDiasHabiles, @DefaultValue("7") int avisoDiasHabiles,
		@DefaultValue("12") int contactosMeses) {

	public PropiedadesPrivacidad {
		if (versionAviso == null || versionAviso.isBlank() || accesosUmbralDiario < 1 || plazoAccesoDiasHabiles < 1
				|| plazoOtrosDiasHabiles < 1 || avisoDiasHabiles < 1 || contactosMeses < 1) {
			throw new IllegalArgumentException("Revisa cuentasclaras.privacidad.*: los plazos y el umbral son positivos y "
					+ "la versión del aviso no está vacía");
		}
	}
}
