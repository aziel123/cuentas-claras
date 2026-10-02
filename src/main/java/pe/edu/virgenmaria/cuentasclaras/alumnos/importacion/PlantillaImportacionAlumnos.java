package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.PlantillaXlsx;

import java.util.Arrays;
import java.util.List;

/** Plantilla oficial de importación: vacía, sin datos personales. El ejemplo (ficticio) está en «Instrucciones». */
@Component
public class PlantillaImportacionAlumnos {

	public static final String HOJA = "Alumnos";

	public static final String NOMBRE_ARCHIVO = "plantilla-alumnos.xlsx";

	static final List<String> INSTRUCCIONES = List.of(
			"1. Escribe un alumno por fila en la hoja «Alumnos», desde la fila 2. No cambies los encabezados ni el nombre de la hoja.",
			"2. Los hermanos llevan el MISMO apoderado (mismo documento): así quedan en la misma familia.",
			"3. El apoderado es el responsable de pago: a él le llegarán los avisos de cada pago. Celular o correo: al menos uno.",
			"4. Escribe valores, no fórmulas. Si copias de otro archivo, usa Pegado especial → Valores.",
			"5. Las secciones deben existir antes en Colegio para el año que eliges al subir el archivo.",
			"6. Hasta 2,000 alumnos y 2 MB por archivo. Si tienes más, haz un archivo por nivel.",
			"7. Al subirlo verás una revisión: nada se guarda hasta que confirmes. Puedes subir el mismo archivo otra vez: "
					+ "lo que ya está registrado aparece como «sin cambios».",
			"No escribas dirección, sexo, salud ni otros datos: el sistema no los necesita y se ignoran.");

	static final List<String> EJEMPLO = List.of("DNI", "71234567", "Ramos", "Torres", "Lucía", "05/03/2015", "Primaria",
			"5", "A", "DNI", "41234567", "Torres", "Vega", "Carmen", "Madre", "987654321", "carmen.torres@example.com");

	private final PlantillaXlsx plantilla;

	public PlantillaImportacionAlumnos(PlantillaXlsx plantilla) {
		this.plantilla = plantilla;
	}

	public byte[] generar() {
		return plantilla.crear(HOJA, Arrays.stream(ColumnaImportacion.values()).map(ColumnaImportacion::paraPlantilla)
				.toList(), INSTRUCCIONES, EJEMPLO);
	}
}
