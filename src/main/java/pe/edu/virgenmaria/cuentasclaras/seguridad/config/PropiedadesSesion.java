package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Sesiones de la base ({@code cuentasclaras.sesion.*}; sprint 7, tanda 2).
 *
 * @param vigenciaMaxima cuánto sirve el secreto con el que se firman las aprobaciones, aunque haya actividad (decisión
 *                       83). En MySQL, trg_sesion_usuario_nace acepta 12 horas como máximo.
 */
@ConfigurationProperties("cuentasclaras.sesion")
public record PropiedadesSesion(@DefaultValue("10h") Duration vigenciaMaxima) {

	public PropiedadesSesion {
		if (vigenciaMaxima == null || vigenciaMaxima.isNegative() || vigenciaMaxima.isZero()
				|| vigenciaMaxima.compareTo(Duration.ofHours(12)) > 0) {
			throw new IllegalArgumentException("cuentasclaras.sesion.vigencia-maxima debe ser positiva y de 12 h como máximo");
		}
	}
}
