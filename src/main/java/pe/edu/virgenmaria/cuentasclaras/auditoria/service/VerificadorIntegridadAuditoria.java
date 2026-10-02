package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EslabonCadena;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EslabonCadenaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Recorre toda la cadena de auditoría y detecta eventos editados, borrados o insertados
 * directamente en la base:
 * <ul>
 *   <li>la secuencia debe ir de 1 a N sin huecos ni repeticiones;</li>
 *   <li>el hash de cada evento debe coincidir con el recalculado (HMAC con la clave secreta);</li>
 *   <li>no puede haber eventos más allá del eslabón, y el último debe coincidir con él.</li>
 * </ul>
 * La revisión bloquea el eslabón para trabajar sobre una foto consistente (ningún registro
 * legítimo ocurre mientras tanto), así que un evento posterior al eslabón es ajeno a la aplicación.
 * <p>
 * El resultado se registra como {@link AccionAuditoria#INTEGRIDAD_VERIFICADA} en una transacción
 * aparte: si alguien insertó un evento falso con la secuencia siguiente, ese registro choca con la
 * restricción única, pero el resultado de la verificación igual se devuelve.
 */
@Service
public class VerificadorIntegridadAuditoria {

	/** Hash con el que empieza la cadena (migración V3). */
	public static final String HASH_INICIAL = "0".repeat(64);

	static final int TAMANO_LOTE = 500;

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorIntegridadAuditoria.class);

	private final EventoAuditoriaRepository eventos;

	private final EslabonCadenaRepository cadena;

	private final SelladorAuditoria sellador;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	public VerificadorIntegridadAuditoria(EventoAuditoriaRepository eventos, EslabonCadenaRepository cadena,
			SelladorAuditoria sellador, AuditoriaService auditoria, PlatformTransactionManager transacciones) {
		this.eventos = eventos;
		this.cadena = cadena;
		this.sellador = sellador;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	@PreAuthorize("hasRole('PROMOTOR')")
	public ResultadoVerificacion verificar() {
		ResultadoVerificacion resultado = transaccion.execute(estado -> revisarCadena());
		if (!resultado.integra()) {
			LOG.error("ALERTA: la bitácora de auditoría fue alterada. {}", resultado.detalle());
		}
		try {
			auditoria.registrar(AccionAuditoria.INTEGRIDAD_VERIFICADA, "evento_auditoria", null, null,
					resultado.integra() ? "INTEGRA" : "ALTERADA", resultado.detalle());
		}
		catch (DataAccessException e) {
			LOG.error("ALERTA: no se pudo registrar la verificación de integridad en la bitácora", e);
		}
		return resultado;
	}

	private ResultadoVerificacion revisarCadena() {
		EslabonCadena eslabon = cadena.bloquear();
		String hashAnterior = HASH_INICIAL;
		long esperada = 1;
		long cursor = Long.MIN_VALUE;
		long revisados = 0;
		List<EventoAuditoria> lote;
		do {
			lote = eventos.findBySecuenciaGreaterThanOrderBySecuenciaAsc(cursor, Limit.of(TAMANO_LOTE));
			for (EventoAuditoria evento : lote) {
				revisados++;
				long secuencia = evento.getSecuencia();
				if (secuencia != esperada) {
					return ResultadoVerificacion.alterada(revisados, esperada, secuencia > esperada
							? "Falta el evento con secuencia " + esperada + ": se borró un evento de la bitácora."
							: "Hay un evento con secuencia " + secuencia + " fuera de la cadena.");
				}
				if (secuencia > eslabon.getUltimaSecuencia()) {
					return ResultadoVerificacion.alterada(revisados, secuencia,
							"El evento con secuencia " + secuencia + " no fue registrado por la aplicación.");
				}
				if (!sellador.esValido(hashAnterior, evento)) {
					return ResultadoVerificacion.alterada(revisados, secuencia, "El evento con secuencia "
							+ secuencia + " fue modificado o insertado fuera de la aplicación.");
				}
				hashAnterior = evento.getHash();
				esperada++;
				cursor = secuencia;
			}
		}
		while (lote.size() == TAMANO_LOTE);

		long ultima = esperada - 1;
		if (ultima != eslabon.getUltimaSecuencia()) {
			return ResultadoVerificacion.alterada(revisados, ultima + 1, "Faltan eventos al final de la bitácora: "
					+ "la cadena llega a " + eslabon.getUltimaSecuencia() + " y se encontraron " + ultima + ".");
		}
		if (!mismoHash(hashAnterior, eslabon.getUltimoHash())) {
			return ResultadoVerificacion.alterada(revisados, ultima,
					"El último evento no coincide con el eslabón de la cadena.");
		}
		return ResultadoVerificacion.integra(revisados);
	}

	private static boolean mismoHash(String a, String b) {
		return a != null && b != null
				&& MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
	}
}
