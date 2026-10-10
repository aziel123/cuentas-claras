package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EslabonCadena;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EslabonCadenaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;

/**
 * Sprint 7 (monitoreo): ¿el eslabón de la cadena apunta al último evento de la bitácora? Es una comprobación barata, sin
 * la clave HMAC (la verificación completa sigue siendo la de las 06:00 y la de Promotoría). Solo devuelve sí o no: no
 * expone eventos ni datos de ningún colegio.
 */
@Service
public class EstadoCadena {

	private final EslabonCadenaRepository cadena;

	private final EventoAuditoriaRepository eventos;

	public EstadoCadena(EslabonCadenaRepository cadena, EventoAuditoriaRepository eventos) {
		this.cadena = cadena;
		this.eventos = eventos;
	}

	@Transactional(readOnly = true)
	public boolean alDia() {
		EslabonCadena eslabon = cadena.leer();
		if (eslabon == null) {
			return false;
		}
		long ultima = eslabon.getUltimaSecuencia();
		if (eventos.findBySecuencia(ultima + 1).isPresent()) {
			return false;
		}
		if (ultima == 0) {
			return VerificadorIntegridadAuditoria.HASH_INICIAL.equals(eslabon.getUltimoHash());
		}
		return eventos.findBySecuencia(ultima).map(e -> e.getHash().equals(eslabon.getUltimoHash())).orElse(false);
	}
}
