package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.util.List;

/**
 * Correcciones del sprint 8 (QA-S8-8, decisión 138): el tamaño de una instalación compartida, para el tope de
 * {@code cuentasclaras.plataforma.max-alumnos}. Solo cuenta (nunca devuelve datos de un alumno): recorre los colegios con
 * el colegio de cada uno en contexto, sin transacción abierta (Hibernate fija el colegio al abrirla).
 */
@Service
public class TamanoInstalacion {

	private final ColegioRepository colegios;

	private final AlumnoRepository alumnos;

	private final TransactionTemplate lectura;

	public TamanoInstalacion(ColegioRepository colegios, AlumnoRepository alumnos, PlatformTransactionManager transacciones) {
		this.colegios = colegios;
		this.alumnos = alumnos;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	/** Cuántos alumnos tiene la instalación (todos los colegios de tipo COLEGIO). */
	public long alumnos() {
		List<Colegio> lista = lectura.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO));
		long total = 0;
		for (Colegio colegio : lista) {
			Long delColegio = ContextoColegio.en(colegio.getId(), () -> lectura.execute(t -> alumnos.count()));
			total += delColegio == null ? 0 : delColegio;
		}
		return total;
	}
}
