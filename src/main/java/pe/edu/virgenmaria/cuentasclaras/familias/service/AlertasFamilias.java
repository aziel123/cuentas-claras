package pe.edu.virgenmaria.cuentasclaras.familias.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.familias.model.AvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.model.EstadoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.repository.AvisoFamiliaRepository;

import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alertas de los avisos de las familias para «Para revisar» de Promotoría (sprint 5, sección 13): «Pagué y no aparece» o
 * «No reconozco…» abiertos son CRÍTICOS (G1: es el fraude original); «Otro», ATENCIÓN.
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
@Transactional(readOnly = true)
public class AlertasFamilias implements AlertasRevision {

	static final String MODULO = "Familias";

	private final AvisoFamiliaRepository avisos;

	private final UsuarioRepository usuarios;

	private final Clock reloj;

	public AlertasFamilias(AvisoFamiliaRepository avisos, UsuarioRepository usuarios, Clock reloj) {
		this.avisos = avisos;
		this.usuarios = usuarios;
		this.reloj = reloj;
	}

	private boolean esPromotoria(String usuario) {
		return usuario != null && usuarios.findByNombreUsuario(usuario).map(u -> u.getRoles().contains(Rol.PROMOTOR))
				.orElse(false);
	}

	@Override
	public List<AlertaRevision> alertas() {
		List<AvisoFamilia> abiertos = avisos.findByEstadoOrderByIdAsc(EstadoAvisoFamilia.ABIERTO);
		long criticos = abiertos.stream().filter(a -> a.getTipo().critico()).count();
		long otros = abiertos.size() - criticos;
		List<AlertaRevision> alertas = new ArrayList<>();
		if (criticos > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, criticos + " familia(s) avisan que pagaron y no "
					+ "aparece, o que no reconocen un pago, una anulación o un descuento. Revísalo hoy.", "/avisos-familias"));
		}
		// S5-M2: un aviso crítico que no cerró Promotoría sigue visible para Promotoría durante 7 días.
		long cerradosPorOtros = avisos.findByEstadoAndAtendidoEnGreaterThanEqualOrderByIdAsc(EstadoAvisoFamilia.ATENDIDO,
				LocalDateTime.now(reloj).minusDays(7)).stream().filter(a -> a.getTipo().critico())
				.filter(a -> !esPromotoria(a.getAtendidoPor())).count();
		if (cerradosPorOtros > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, cerradosPorOtros + " aviso(s) graves de familias los "
					+ "cerró alguien que no es Promotoría en los últimos 7 días. Revisa la respuesta y llama a la familia.",
					"/avisos-familias"));
		}
		if (otros > 0) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, otros + " aviso(s) de familias por responder.",
					"/avisos-familias"));
		}
		return alertas;
	}
}
