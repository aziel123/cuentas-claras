package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;

import java.time.LocalTime;

/** Sprint 9, tanda 2 (A6): lo que Dirección elige. Los rangos los vuelven a validar el servicio y la base. */
public record ConfiguracionRequest(
		@NotNull(message = "Elige la hora de la alerta.") @DateTimeFormat(pattern = "HH:mm") LocalTime horaAlerta,
		@NotNull(message = "Elige la hora del aviso.") @DateTimeFormat(pattern = "HH:mm") LocalTime horaAviso,
		@NotNull(message = "Elige la hora del segundo aviso.") @DateTimeFormat(pattern = "HH:mm") LocalTime horaSegundoAviso,
		@NotNull(message = "Elige el plazo.") @Min(value = 1, message = "De 1 a 10 días hábiles.")
		@Max(value = 10, message = "De 1 a 10 días hábiles.") Integer diasJustificacion,
		@NotNull(message = "Elige el plazo.") @Min(value = 1, message = "De 1 a 10 días hábiles.")
		@Max(value = 10, message = "De 1 a 10 días hábiles.") Integer diasRegistroTardio,
		@NotNull(message = "Elige la hora del resumen.") @DateTimeFormat(pattern = "HH:mm") LocalTime horaResumenComunicados,
		@NotNull(message = "Elige el tope.") @Min(value = 0, message = "De 0 a 20 al mes.")
		@Max(value = 20, message = "De 0 a 20 al mes.") Integer topeResumenesMes) {

	public static ConfiguracionRequest de(ConfiguracionVista v) {
		return new ConfiguracionRequest(v.horaAlerta(), v.horaAviso(), v.horaSegundoAviso(), v.diasJustificacion(),
				v.diasRegistroTardio(), v.horaResumenComunicados(), v.topeResumenesMes());
	}

	public ConfiguracionAcademica.Valores valores() {
		return new ConfiguracionAcademica.Valores(horaAlerta, horaAviso, horaSegundoAviso, diasJustificacion,
				diasRegistroTardio, horaResumenComunicados, topeResumenesMes);
	}
}
