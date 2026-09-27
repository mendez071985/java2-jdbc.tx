package py.edu.ucsa.jdbc.tx.inspector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Simula a un usuario usando la consola (entrada y salida en memoria). */
@SpringBootTest
class DatabaseInspectorConsoleTest {

	@Autowired
	DatabaseInspectorService inspector;

	private String ejecutar(String entrada) throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
		new DatabaseInspectorConsole(inspector, new BufferedReader(new StringReader(entrada)), out).ejecutar();
		return buffer.toString(StandardCharsets.UTF_8);
	}

	private int numeroDeTabla(String nombre) throws Exception {
		var tablas = inspector.listarTablas();
		for (int i = 0; i < tablas.size(); i++) {
			if (tablas.get(i).name().equals(nombre)) {
				return i + 1;
			}
		}
		throw new AssertionError(nombre);
	}

	@Test
	void flujoCompleto_seleccionaUnaTablaYMuestraTodo() throws Exception {
		String salida = ejecutar("abc\n999\n" + numeroDeTabla("funcionarios") + "\n0\n");

		assertThat(salida)
				.contains("INFORMACIÓN DE LA BASE DE DATOS", "Motor", "Driver JDBC",
						"Transacciones     : SI", "Operaciones batch : SI")
				.contains("TABLAS DISPONIBLES", ". public.funcionarios")
				.contains("Opción inválida: \"abc\"", "Opción inválida: \"999\"")
				.contains("TABLA public.funcionarios")
				.contains("--- Columnas ---", "Tipo SQL", "Tamaño", "Permite NULL", "Autoincremental")
				.contains("--- Clave primaria ---", "(id)")
				.contains("--- Claves foráneas ---", "public.departamento(id)")
				.contains("--- Índices ---", "apellido, nombre")
				.contains("--- Primeros 10 registros ---", "10 registro(s) mostrado(s).")
				.contains("Fin del Database Inspector.");
	}

	@Test
	void tablaSinClaves_muestraMensajes() throws Exception {
		String salida = ejecutar(numeroDeTabla("Log Eventos") + "\n0\n");

		assertThat(salida)
				.contains("(la tabla no tiene clave primaria)")
				.contains("(la tabla no tiene claves foráneas)")
				.contains("(la tabla no tiene índices)")
				.contains("1 registro(s) mostrado(s).");
	}

	@Test
	void finDeEntrada_terminaSinErrores() throws Exception {
		assertThat(ejecutar("")).contains("Fin del Database Inspector.");
	}
}
