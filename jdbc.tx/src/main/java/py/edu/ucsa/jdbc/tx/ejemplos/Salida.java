package py.edu.ucsa.jdbc.tx.ejemplos;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import py.edu.ucsa.jdbc.tx.model.Funcionario;

/** Utilidades para mostrar los ejemplos por consola. */
final class Salida {

	private static final DecimalFormat GUARANIES =
			new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.of("es", "PY")));

	private Salida() {
	}

	static void titulo(String texto) {
		System.out.println();
		System.out.println("=".repeat(78));
		System.out.println(" " + texto);
		System.out.println("=".repeat(78));
	}

	static void paso(String texto) {
		System.out.println();
		System.out.println("--- " + texto + " ---");
	}

	static void mostrar(Funcionario f) {
		System.out.printf(
				"  ID %-5d | %-10s %-15s | edad %-4s | nac. %-10s | ingreso %-10s | legajo %-5s | foto %s%n",
				f.id(),
				f.nombre(),
				f.apellido(),
				f.edad() == null ? "NULL" : f.edad(),
				f.fechaNacimiento(),
				f.fechaIngreso(),
				f.legajo() == null ? "NULL" : f.legajo(),
				f.foto() == null ? "NULL" : f.foto().length + " bytes");
	}

	/** Muestra hasta {@code maximo} funcionarios y cuántos más quedaron sin mostrar. */
	static void mostrar(List<Funcionario> funcionarios, int maximo) {
		if (funcionarios.isEmpty()) {
			System.out.println("  (ningún registro)");
			return;
		}
		funcionarios.stream().limit(maximo).forEach(Salida::mostrar);
		if (funcionarios.size() > maximo) {
			System.out.println("  ... y " + (funcionarios.size() - maximo) + " más");
		}
	}

	static String gs(BigDecimal monto) {
		return monto == null ? "NULL" : GUARANIES.format(monto) + " Gs.";
	}
}
