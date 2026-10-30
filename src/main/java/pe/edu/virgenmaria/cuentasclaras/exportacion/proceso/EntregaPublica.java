package pe.edu.virgenmaria.cuentasclaras.exportacion.proceso;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.EntregaExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.Exportador;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.LectorTablas;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Sprint 11, tanda 2 (AP2-4): la entrega PÚBLICA de una exportación ({@code /exportacion/{token}}, sin sesión). El colegio
 * sale de la huella del enlace; todo lo demás corre como {@code sistema.exportacion} con ese colegio en contexto.
 */
@Component
public class EntregaPublica {

	private static final String NO_SIRVE = "Este enlace no existe o ya no sirve";

	private final LectorTablas lector;

	private final EntregaExportacion entrega;

	private final Exportador exportador;

	private final ColegioRepository colegios;

	private final TransactionTemplate lectura;

	public EntregaPublica(LectorTablas lector, EntregaExportacion entrega, Exportador exportador,
			ColegioRepository colegios, PlatformTransactionManager transacciones) {
		this.lector = lector;
		this.entrega = entrega;
		this.exportador = exportador;
		this.colegios = colegios;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	public EntregaExportacion.VistaEntrega ver(String token) {
		return enSuColegio(token, () -> entrega.ver(token));
	}

	public void confirmarDni(String token, String dni) {
		enSuColegio(token, () -> {
			entrega.confirmarDni(token, dni);
			return null;
		});
	}

	/** Devuelve la clave del ZIP (se muestra una sola vez). */
	public String confirmarCodigo(String token, String codigo) {
		return enSuColegio(token, () -> entrega.confirmarCodigo(token, codigo));
	}

	public Path descargar(String token, String codigo, String ip) {
		return enSuColegio(token, () -> entrega.descargar(token, codigo, ip, exportador.directorio()));
	}

	private <T> T enSuColegio(String token, Supplier<T> tarea) {
		if (token == null || token.length() < 20 || token.length() > 100) {
			throw new RecursoNoEncontradoException(NO_SIRVE);
		}
		Long plataforma = lectura.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA).map(Colegio::getId)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_SIRVE)));
		Long colegio = EjecucionComoSistema.como(ActorSistema.EXPORTACION, plataforma,
				() -> lector.exportacionPorToken(Sha256.hex(token))
						.map(f -> ((Number) f.get("colegio_id")).longValue())
						.orElseThrow(() -> new RecursoNoEncontradoException(NO_SIRVE)));
		return EjecucionComoSistema.como(ActorSistema.EXPORTACION, colegio, tarea);
	}
}
