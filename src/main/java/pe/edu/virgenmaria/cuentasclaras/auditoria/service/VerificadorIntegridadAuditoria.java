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
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Recorre toda la cadena de auditoría y detecta eventos editados, borrados o insertados directamente en la base:
 * <ul>
 *   <li>la secuencia debe ir de 1 a N sin huecos ni repeticiones;</li>
 *   <li>el hash de cada evento debe coincidir con el recalculado (HMAC con la clave secreta);</li>
 *   <li>no puede haber eventos más allá del eslabón, y el último debe coincidir con él;</li>
 *   <li>si Promotoría da una huella anotada, ese evento debe seguir en la cadena con el mismo código
 *       (detecta que alguien borró los últimos eventos y retrocedió el eslabón).</li>
 * </ul>
 * La cadena es global (todos los colegios); a cada colegio solo se le informa cuántos eventos SUYOS se
 * revisaron y la ubicación de un problema solo si es en un evento suyo.
 * <p>
 * El resultado se registra como {@link AccionAuditoria#INTEGRIDAD_VERIFICADA} en una transacción aparte; ese
 * evento es la huella nueva para anotar. Si alguien insertó un evento falso con la secuencia siguiente, ese
 * registro choca con la restricción única, pero el resultado igual se devuelve (sin huella).
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
		return ejecutar(null);
	}

	/** @param huellaAnotada huella que Promotoría anotó en una verificación anterior; {@code null} si no tiene */
	@PreAuthorize("hasRole('PROMOTOR')")
	public ResultadoVerificacion verificar(HuellaBitacora huellaAnotada) {
		return ejecutar(huellaAnotada);
	}

	/** Sprint 5: la reverificación diaria de {@code sistema.auditoria} (06:00), en el colegio del contexto. */
	@PreAuthorize("hasRole('SISTEMA_AUDITORIA')")
	public ResultadoVerificacion verificarComoSistema() {
		return ejecutar(null);
	}

	private ResultadoVerificacion ejecutar(HuellaBitacora huellaAnotada) {
		Long colegioActual = ContextoColegio.actual();
		Long visor = colegioActual != null && colegioActual > 0 ? colegioActual : null;
		ResultadoVerificacion resultado = transaccion.execute(estado -> revisarCadena(visor, huellaAnotada));
		if (!resultado.integra()) {
			LOG.error("ALERTA: la bitácora de auditoría fue alterada. {}", resultado.detalle());
		}
		try {
			EventoAuditoria registro = auditoria.registrar(AccionAuditoria.INTEGRIDAD_VERIFICADA, "evento_auditoria",
					null, null, resultado.integra() ? "INTEGRA" : "ALTERADA", resultado.detalle());
			return resultado.conHuella(HuellaBitacora.de(registro.getSecuencia(), registro.getHash(),
					registro.getOcurridoEn()));
		}
		catch (DataAccessException e) {
			LOG.error("ALERTA: no se pudo registrar la verificación de integridad en la bitácora", e);
			return resultado;
		}
	}

	/** @param visor colegio que verifica; {@code null} = proceso del sistema, ve todo */
	private ResultadoVerificacion revisarCadena(Long visor, HuellaBitacora huellaAnotada) {
		EslabonCadena eslabon = cadena.bloquear();
		String hashAnterior = HASH_INICIAL;
		long esperada = 1;
		long cursor = Long.MIN_VALUE;
		long delVisor = 0;
		boolean huellaEncontrada = false;
		List<EventoAuditoria> lote;
		do {
			lote = eventos.findBySecuenciaGreaterThanOrderBySecuenciaAsc(cursor, Limit.of(TAMANO_LOTE));
			for (EventoAuditoria evento : lote) {
				long secuencia = evento.getSecuencia();
				boolean visible = visor == null || Objects.equals(visor, evento.getColegioId());
				if (visible) {
					delVisor++;
				}
				if (secuencia != esperada) {
					return ResultadoVerificacion.alterada(delVisor, visor == null ? esperada : null,
							secuencia > esperada
									? "Falta " + (visor == null ? "el evento con secuencia " + esperada : "al menos un evento")
											+ ": se borró un evento de la bitácora."
									: "Hay un evento " + ubicacion(visible, secuencia) + "fuera de la cadena.");
				}
				if (secuencia > eslabon.getUltimaSecuencia()) {
					return ResultadoVerificacion.alterada(delVisor, visible ? secuencia : null,
							"El evento " + ubicacion(visible, secuencia) + "no fue registrado por la aplicación.");
				}
				if (!sellador.esValido(hashAnterior, evento)) {
					return ResultadoVerificacion.alterada(delVisor, visible ? secuencia : null,
							"El evento " + ubicacion(visible, secuencia) + "fue modificado o insertado fuera de la aplicación.");
				}
				if (huellaAnotada != null && huellaAnotada.secuencia() == secuencia) {
					huellaEncontrada = visible && huellaAnotada.coincideCon(evento.getHash());
				}
				hashAnterior = evento.getHash();
				esperada++;
				cursor = secuencia;
			}
		}
		while (lote.size() == TAMANO_LOTE);

		long ultima = esperada - 1;
		if (ultima != eslabon.getUltimaSecuencia()) {
			return ResultadoVerificacion.alterada(delVisor, visor == null ? ultima + 1 : null,
					"Faltan eventos al final de la bitácora: se borraron los últimos registros.");
		}
		if (!mismoHash(hashAnterior, eslabon.getUltimoHash())) {
			return ResultadoVerificacion.alterada(delVisor, visor == null ? ultima : null,
					"El último evento no coincide con el eslabón de la cadena.");
		}
		if (huellaAnotada != null && !huellaEncontrada) {
			return ResultadoVerificacion.alterada(delVisor, null, "La huella que anotaste (evento "
					+ huellaAnotada.secuencia() + ", código " + huellaAnotada.codigo() + ") ya no está en la bitácora: "
					+ "o se anotó mal, o la bitácora fue recortada. Revisa tu anotación; si es correcta, la bitácora fue alterada.");
		}
		return ResultadoVerificacion.integra(delVisor);
	}

	private static String ubicacion(boolean visible, long secuencia) {
		return visible ? "con secuencia " + secuencia + " " : "de otro colegio ";
	}

	private static boolean mismoHash(String a, String b) {
		return a != null && b != null
				&& MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
	}
}
