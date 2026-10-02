package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Objetos de dominio en memoria (sin base) para las pruebas puras de cobranza. */
final class CuotasDePrueba {

	static final AnioEscolar ANIO_2027 = AnioEscolar.nuevo(2027, false, LocalDate.of(2027, 3, 1),
			LocalDate.of(2027, 12, 17));

	private CuotasDePrueba() {
	}

	static PlanPension planAprobado(String pension, String matricula) {
		PlanPension plan = PlanPension.borrador(ANIO_2027, Nivel.PRIMARIA, ConfiguracionPlan
				.porDefecto(2027, new BigDecimal(matricula), new BigDecimal(pension)).validar(ANIO_2027), "administracion");
		plan.enviar("administracion", LocalDateTime.of(2026, 10, 2, 7, 0));
		plan.aprobar("director", LocalDateTime.of(2026, 10, 2, 8, 0));
		return plan;
	}

	static Matricula matricula(LocalDate fecha) {
		Familia familia = Familia.nueva("Familia Quispe Huamán");
		Apoderado rosa = Apoderado.nuevo(familia, new DatosApoderado(new DocumentoIdentidad(TipoDocumento.DNI, "45678912"),
				"Huamán", "Ccori", "Rosa", Parentesco.MADRE, "+51987654321", null));
		Alumno mateo = Alumno.nuevo(new DatosAlumno(new DocumentoIdentidad(TipoDocumento.DNI, "78451236"), "Quispe",
				"Huamán", "Mateo", LocalDate.of(2015, 6, 14)), rosa);
		return Matricula.nueva(mateo, Seccion.nueva(ANIO_2027, Grado.PRIMARIA_6, "A"), fecha);
	}

	/** Pensión de setiembre 2027 (vence el 30/09/2027) por {@code monto}. */
	static Cuota pensionSetiembre(String monto) {
		PlanPension plan = planAprobado(monto, "0");
		CuotaPlanificada setiembre = CalculadoraCronograma.calcular(plan, 1L, LocalDate.of(2027, 3, 1)).stream()
				.filter(c -> Integer.valueOf(9).equals(c.numero())).findFirst().orElseThrow();
		return Cuota.generada(matricula(LocalDate.of(2027, 3, 1)), plan, setiembre);
	}
}
