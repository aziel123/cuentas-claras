package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import java.util.List;

/**
 * Correcciones del sprint 11 (A1): de dónde salen las actas de supresión ejecutadas que viven FUERA de la base (AP2-3). En
 * producción el original está en el depósito con bloqueo de objetos (prefijo {@code actas/}); el despliegue lo sincroniza a
 * {@code CC_DIR_ACTAS} antes de arrancar ({@code rclone sync}, docs/operacion/fin-de-un-colegio.md) y esta interfaz lo
 * lee. Otra implementación puede leer el depósito directo cuando la VM tenga sus credenciales.
 */
public interface FuenteActasExternas {

	/** Un acta externa: el colegio y su id. */
	record ActaExterna(long colegioId, long actaId) {
	}

	/** Dónde las lee (para los mensajes); vacío si no está configurada. */
	String origen();

	List<ActaExterna> actas();
}
