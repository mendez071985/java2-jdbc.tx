package py.edu.ucsa.jdbc.tx.inspector;

import java.io.PrintStream;
import java.util.List;

/** Imprime una tabla de texto cuyo ancho de columnas se calcula según el contenido. */
final class TablaTexto {

	private TablaTexto() {
	}

	static void imprimir(PrintStream out, List<String> encabezados, List<List<String>> filas) {
		int[] anchos = new int[encabezados.size()];
		for (int i = 0; i < anchos.length; i++) {
			anchos[i] = encabezados.get(i).length();
		}
		for (List<String> fila : filas) {
			for (int i = 0; i < anchos.length; i++) {
				anchos[i] = Math.max(anchos[i], fila.get(i).length());
			}
		}

		String separador = separador(anchos);
		out.println(separador);
		out.println(fila(encabezados, anchos));
		out.println(separador);
		for (List<String> fila : filas) {
			out.println(fila(fila, anchos));
		}
		out.println(separador);
	}

	private static String separador(int[] anchos) {
		StringBuilder sb = new StringBuilder("+");
		for (int ancho : anchos) {
			sb.append("-".repeat(ancho + 2)).append('+');
		}
		return sb.toString();
	}

	private static String fila(List<String> valores, int[] anchos) {
		StringBuilder sb = new StringBuilder("|");
		for (int i = 0; i < anchos.length; i++) {
			sb.append(' ').append(String.format("%-" + anchos[i] + "s", valores.get(i))).append(" |");
		}
		return sb.toString();
	}
}
