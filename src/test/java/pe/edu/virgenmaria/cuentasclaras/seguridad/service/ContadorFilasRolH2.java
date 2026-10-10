package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Trigger de H2 SOLO para pruebas (QA del sprint 7, H2): anota cada fila que se inserta o se borra de {@code usuario_rol}
 * ({@code "ALTA DIRECTOR"}, {@code "BAJA DOCENTE"}). Sirve para comprobar que un cambio de roles toca solo la diferencia:
 * en MySQL, borrar y volver a insertar PROMOTOR o DIRECTOR lo rechazan trg_usuario_rol_baja y trg_usuario_rol_alta.
 */
public class ContadorFilasRolH2 implements Trigger {

	/** Lo que pasó desde el último {@link #reiniciar()}, en orden. */
	public static final List<String> FILAS = new CopyOnWriteArrayList<>();

	private int tipo;

	@Override
	public void init(Connection conexion, String esquema, String trigger, String tabla, boolean antes, int tipo) {
		this.tipo = tipo;
	}

	@Override
	public void fire(Connection conexion, Object[] anterior, Object[] nueva) {
		if (tipo == INSERT && nueva != null) {
			FILAS.add("ALTA " + nueva[1]);
		}
		else if (tipo == DELETE && anterior != null) {
			FILAS.add("BAJA " + anterior[1]);
		}
	}

	public static void reiniciar() {
		FILAS.clear();
	}
}
