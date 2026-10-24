package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.EsperaVista;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.EsperaConectores;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11, tanda 1c (decisiones 218 y 223, P6): el operador de la plataforma FRENA, con su firma y un motivo, lo que una
 * Promotoría aplicó directo y espera 24 horas: una cuenta bancaria, una pasarela o una recaudación nuevas; dar o quitar
 * Promotoría; desactivar a una Promotoría. Nunca aplica nada (el operador interviene en el dinero del colegio solo para
 * frenar). Se avisa a todas las Promotorías y al correo de control. La firma va en el colegio de plataforma; el freno, con
 * cc_sistema y el colegio en contexto ({@link EjecucionPlataforma}); en MySQL lo exigen trg_conector_colegio_estado y
 * trg_solicitud_cambio_resuelta.
 */
@Service
@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
public class FrenosOperador {

	/** Una espera de un colegio, para la pantalla. */
	public record EsperaDeColegio(Long colegioId, String colegio, EsperaVista espera) {
	}

	private final ColegioRepository colegios;

	private final EsperaConectores conectores;

	private final BandejaAprobaciones bandeja;

	private final EjecucionPlataforma plataforma;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	private final TransactionTemplate lectura;

	public FrenosOperador(ColegioRepository colegios, EsperaConectores conectores, BandejaAprobaciones bandeja,
			EjecucionPlataforma plataforma, FirmaSesion firma, AuditoriaService auditoria,
			PlatformTransactionManager transacciones) {
		this.colegios = colegios;
		this.conectores = conectores;
		this.bandeja = bandeja;
		this.plataforma = plataforma;
		this.firma = firma;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	/** Todo lo que espera, de todos los colegios que operan. */
	public List<EsperaDeColegio> esperas() {
		List<EsperaDeColegio> todas = new ArrayList<>();
		for (Colegio c : lectura.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO))) {
			if (!c.getEstado().opera()) {
				continue;
			}
			List<EsperaVista> delColegio = ContextoColegio.en(c.getId(), () -> plataforma.como(() -> {
				List<EsperaVista> lista = new ArrayList<>(conectores.enEsperaParaOperador());
				lista.addAll(bandeja.enEsperaParaOperador());
				return lista;
			}));
			delColegio.forEach(e -> todas.add(new EsperaDeColegio(c.getId(), c.getNombre(), e)));
		}
		return todas;
	}

	/** Frena un conector en espera de ese colegio. */
	public void frenarConector(long colegioId, long conectorId, String motivo) {
		Colegio colegio = colegio(colegioId);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.conectorFrenado(conectorId)));
		String que = ContextoColegio.en(colegioId, () -> plataforma.como(() -> conectores.frenarComoOperador(conectorId,
				motivo)));
		enLaPlataforma(colegio, "el cambio de " + que, motivo);
	}

	/** Frena una directa que espera (dar o quitar Promotoría, desactivar a una Promotoría) de ese colegio. */
	public void frenarSolicitud(long colegioId, long solicitudId, String motivo) {
		Colegio colegio = colegio(colegioId);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.solicitudFrenada(solicitudId)));
		String que = ContextoColegio.en(colegioId, () -> plataforma.como(() -> bandeja.frenarComoOperador(solicitudId,
				motivo)));
		enLaPlataforma(colegio, que, motivo);
	}

	private void enLaPlataforma(Colegio colegio, String que, String motivo) {
		String operador = operador().getUsername();
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.DIRECTA_FRENADA, "colegio",
				colegio.getId().toString(), "en espera", "frenada", "El operador " + operador + " frenó en el colegio "
						+ colegio.getNombre() + ": " + que + ". Motivo: " + motivo));
	}

	private Colegio colegio(long colegioId) {
		return lectura.execute(t -> colegios.findById(colegioId).filter(c -> !c.esPlataforma() && c.getEstado().opera()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
	}

	private static UsuarioAutenticado operador() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
			throw new AccessDeniedException("Se requiere un operador en sesión");
		}
		return usuario;
	}
}
