package pe.edu.virgenmaria.cuentasclaras.operacion.model;

/** Resultado de comparar un respaldo con el manifiesto del anterior (scripts/respaldo/respaldar.sh). */
public enum ComparacionRespaldo {

	/** No había un manifiesto anterior en el destino. */
	PRIMERO,

	/** Están todas las filas del respaldo anterior y su ancla de la bitácora con el mismo hash. */
	IGUAL,

	/** Faltan filas que existían en el respaldo anterior, o su ancla de la bitácora ya no está o cambió. */
	FALTAN_FILAS
}
