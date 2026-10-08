package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Lo que ya salió por mensaje de la huella diaria (correcciones del sprint 5, S5-M4 y S5-B3). Lo implementa la mensajería
 * ({@code comunicacion}), así {@code auditoria} no depende de ella. Todo es del colegio actual.
 */
public interface EnviosHuella {

	/** Una huella que se envió (o se mandó enviar) a Promotoría. */
	record HuellaEnviada(LocalDate fecha, long secuencia, String codigo) {
	}

	/** La huella de MAYOR secuencia que figura en los mensajes HUELLA_BITACORA del colegio. */
	Optional<HuellaEnviada> mayorEnviada();

	/** Si el mensaje de esa huella ya salió (ENVIADO, ENTREGADO o LEIDO) a alguien de Promotoría. */
	boolean salio(Long huellaId);
}
