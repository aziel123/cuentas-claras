package pe.edu.virgenmaria.cuentasclaras.privacidad.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.privacidad.config.PropiedadesPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.DatoVencido;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reporte «Datos con plazo vencido» para Promotoría (sprint 7, tanda 3; sección 8.4, decisión 98): familias que dejaron el
 * colegio (todos sus hijos retirados o egresados) SIN deuda hace más del plazo (12 meses por defecto, a confirmar por el
 * asesor legal) y que todavía tienen su celular o correo guardados. La anonimización todavía no es automática: este
 * reporte dice a quién le toca. Los datos financieros y la bitácora no se borran (obligación tributaria y evidencia).
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
@Transactional(readOnly = true)
public class DatosConPlazoVencido {

	private final FamiliaRepository familias;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final MatriculaRepository matriculas;

	private final CuotaRepository cuotas;

	private final PropiedadesPrivacidad propiedades;

	private final Clock reloj;

	public DatosConPlazoVencido(FamiliaRepository familias, AlumnoRepository alumnos, ApoderadoRepository apoderados,
			MatriculaRepository matriculas, CuotaRepository cuotas, PropiedadesPrivacidad propiedades, Clock reloj) {
		this.familias = familias;
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.matriculas = matriculas;
		this.cuotas = cuotas;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	public List<DatoVencido> familias() {
		LocalDate limite = LocalDate.now(reloj).minusMonths(propiedades.contactosMeses());
		List<DatoVencido> vencidos = new ArrayList<>();
		for (Familia familia : familias.findAll()) {
			List<Alumno> hijos = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(familia.getId());
			if (hijos.isEmpty() || hijos.stream().anyMatch(h -> h.getEstado() == EstadoAlumno.ACTIVO)) {
				continue;
			}
			Optional<LocalDate> salida = hijos.stream().map(this::salida).filter(Objects::nonNull)
					.max(Comparator.naturalOrder());
			if (salida.isEmpty() || !salida.get().isBefore(limite)) {
				continue;
			}
			long contactos = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familia.getId()).stream()
					.mapToLong(DatosConPlazoVencido::contactosGuardados).sum();
			if (contactos == 0 || !cuotas.porPagarDeFamilia(familia.getId()).isEmpty()) {
				continue;
			}
			vencidos.add(new DatoVencido(familia.getId(), familia.getNombre(), salida.get(), contactos));
		}
		vencidos.sort(Comparator.comparing(DatoVencido::salida));
		return vencidos;
	}

	/** Retiro: su fecha. Egreso: el fin de clases de su última matrícula. */
	private LocalDate salida(Alumno alumno) {
		if (alumno.getEstado() == EstadoAlumno.RETIRADO && alumno.getRetiradoEn() != null) {
			return alumno.getRetiradoEn();
		}
		return matriculas.findByAlumnoIdOrderByAnioEscolarAnioDesc(alumno.getId()).stream().findFirst()
				.map(m -> m.getAnioEscolar().getFinClases()).orElse(alumno.getRetiradoEn());
	}

	private static long contactosGuardados(Apoderado apoderado) {
		return (apoderado.getTelefonoWhatsapp() == null ? 0 : 1) + (apoderado.getCorreo() == null ? 0 : 1);
	}
}
