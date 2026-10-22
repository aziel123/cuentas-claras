package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import pe.edu.virgenmaria.cuentasclaras.comunicados.analizador.AnalizadorPdf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Sprint 9, tanda 3 (B5): un proceso hijo de PRUEBA que se lanza con el MISMO comando que el analizador (los mismos
 * límites, el entorno vacío) y la MISMA política ({@link AnalizadorPdf#instalarPolitica}), y prueba lo que el analizador
 * no debe poder hacer. Escribe lo que pasó en {@code {dir}/sonda.txt}.
 * <ul>
 *   <li>{@code red {puerto}}: abrir una conexión a un puerto local que el padre escucha.</li>
 *   <li>{@code entorno}: las variables de entorno que ve.</li>
 *   <li>{@code fuera {ruta}}: escribir fuera de su directorio.</li>
 *   <li>{@code dormir}: no terminar nunca (el padre lo mata por su PID).</li>
 *   <li>{@code memoria}: pedir memoria hasta que se acaba (sale con 3).</li>
 * </ul>
 */
public final class SondaHijo {

	private SondaHijo() {
	}

	public static void main(String[] argumentos) throws Exception {
		Path directorio = Path.of(argumentos[0]);
		String modo = argumentos[1];
		AnalizadorPdf.instalarPolitica(directorio);
		String resultado = switch (modo) {
			case "red" -> red(Integer.parseInt(argumentos[2]));
			case "entorno" -> String.join(",", new TreeSet<>(System.getenv().keySet()));
			case "fuera" -> fuera(Path.of(argumentos[2]));
			case "dormir" -> {
				Thread.sleep(120_000);
				yield "desperto";
			}
			case "memoria" -> {
				List<byte[]> bloques = new ArrayList<>();
				while (true) {
					bloques.add(new byte[8 * 1024 * 1024]);
				}
			}
			default -> "modo desconocido";
		};
		Files.writeString(directorio.resolve("sonda.txt"), resultado, StandardCharsets.UTF_8);
		System.exit(0);
	}

	private static String red(int puerto) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", puerto), 2000);
			return "red=abierta";
		}
		catch (SecurityException e) {
			return "red=bloqueada";
		}
		catch (IOException e) {
			return "red=error " + e.getClass().getSimpleName();
		}
	}

	private static String fuera(Path ruta) {
		try {
			Files.writeString(ruta, "x");
			return "fuera=escrito";
		}
		catch (SecurityException e) {
			return "fuera=bloqueado";
		}
		catch (IOException e) {
			return "fuera=error " + e.getClass().getSimpleName();
		}
	}
}
