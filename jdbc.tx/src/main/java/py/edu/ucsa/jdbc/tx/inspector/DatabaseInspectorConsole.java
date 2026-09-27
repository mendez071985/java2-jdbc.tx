package py.edu.ucsa.jdbc.tx.inspector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import py.edu.ucsa.jdbc.tx.inspector.model.ColumnInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.DatabaseInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.ForeignKeyInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.IndexInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.PrimaryKeyInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.QueryResult;
import py.edu.ucsa.jdbc.tx.inspector.model.TableRef;

/**
 * Interfaz de consola del Database Inspector. Recibe la entrada y la salida por
 * parámetro para poder probarla sin teclado.
 */
public class DatabaseInspectorConsole {

	private final DatabaseInspectorService inspector;
	private final BufferedReader in;
	private final PrintStream out;

	public DatabaseInspectorConsole(DatabaseInspectorService inspector, BufferedReader in, PrintStream out) {
		this.inspector = inspector;
		this.in = in;
		this.out = out;
	}

	public void ejecutar() throws SQLException, IOException {
		mostrarInfoBase(inspector.obtenerInfoBase());

		while (true) {
			List<TableRef> tablas = inspector.listarTablas();
			if (tablas.isEmpty()) {
				out.println("\nNo se encontraron tablas en el esquema configurado.");
				return;
			}
			mostrarTablas(tablas);

			TableRef elegida = leerSeleccion(tablas);
			if (elegida == null) {
				out.println("Fin del Database Inspector.");
				return;
			}
			inspeccionar(elegida);
		}
	}

	public void inspeccionar(TableRef tabla) throws SQLException {
		titulo("TABLA " + tabla.qualifiedName());
		mostrarColumnas(inspector.listarColumnas(tabla));
		mostrarClavePrimaria(inspector.obtenerClavePrimaria(tabla));
		mostrarClavesForaneas(inspector.listarClavesForaneas(tabla));
		mostrarIndices(inspector.listarIndices(tabla));
		mostrarConsulta(inspector.consultar(tabla));
	}

	/** Devuelve la tabla elegida, o null si el usuario ingresa 0 o termina la entrada. */
	private TableRef leerSeleccion(List<TableRef> tablas) throws IOException {
		while (true) {
			out.print("\nSeleccione una tabla (1-" + tablas.size() + ", 0 para salir): ");
			out.flush();
			String linea = in.readLine();
			if (linea == null) {
				out.println();
				return null;
			}
			try {
				int opcion = Integer.parseInt(linea.trim());
				if (opcion == 0) {
					return null;
				}
				if (opcion >= 1 && opcion <= tablas.size()) {
					return tablas.get(opcion - 1);
				}
			} catch (NumberFormatException e) {
				// se vuelve a pedir
			}
			out.println("Opción inválida: \"" + linea.trim() + "\"");
		}
	}

	private void mostrarInfoBase(DatabaseInfo info) {
		titulo("INFORMACIÓN DE LA BASE DE DATOS");
		out.println("Motor             : " + info.productName() + " " + info.productVersion());
		out.println("Driver JDBC       : " + info.driverName() + " " + info.driverVersion());
		out.println("Usuario           : " + info.userName());
		out.println("URL               : " + info.url());
		out.println("Transacciones     : " + siNo(info.supportsTransactions()));
		out.println("Operaciones batch : " + siNo(info.supportsBatchUpdates()));
		out.println("Sólo lectura      : " + siNo(info.readOnly()));
	}

	private void mostrarTablas(List<TableRef> tablas) {
		titulo("TABLAS DISPONIBLES");
		for (int i = 0; i < tablas.size(); i++) {
			out.printf("%3d. %s%n", i + 1, tablas.get(i).qualifiedName());
		}
	}

	private void mostrarColumnas(List<ColumnInfo> columnas) {
		subtitulo("Columnas");
		List<List<String>> filas = new ArrayList<>();
		for (ColumnInfo c : columnas) {
			String tamanio = c.decimalDigits() > 0 ? c.size() + "," + c.decimalDigits() : String.valueOf(c.size());
			filas.add(List.of(
					String.valueOf(c.position()),
					c.name(),
					c.sqlType() + " (" + DatabaseInspectorService.nombreTipoJdbc(c.jdbcType()) + ")",
					tamanio,
					c.nullable(),
					c.autoIncrement()));
		}
		TablaTexto.imprimir(out, List.of("#", "Nombre", "Tipo SQL", "Tamaño", "Permite NULL", "Autoincremental"), filas);
	}

	private void mostrarClavePrimaria(PrimaryKeyInfo pk) {
		subtitulo("Clave primaria");
		if (!pk.exists()) {
			out.println("(la tabla no tiene clave primaria)");
			return;
		}
		out.println(pk.name() + " -> (" + String.join(", ", pk.columns()) + ")");
	}

	private void mostrarClavesForaneas(List<ForeignKeyInfo> fks) {
		subtitulo("Claves foráneas");
		if (fks.isEmpty()) {
			out.println("(la tabla no tiene claves foráneas)");
			return;
		}
		List<List<String>> filas = new ArrayList<>();
		for (ForeignKeyInfo fk : fks) {
			filas.add(List.of(
					String.valueOf(fk.name()),
					fk.column(),
					fk.referencedTable() + "(" + fk.referencedColumn() + ")",
					fk.onUpdate(),
					fk.onDelete()));
		}
		TablaTexto.imprimir(out, List.of("Nombre", "Columna", "Referencia", "ON UPDATE", "ON DELETE"), filas);
	}

	private void mostrarIndices(List<IndexInfo> indices) {
		subtitulo("Índices");
		if (indices.isEmpty()) {
			out.println("(la tabla no tiene índices)");
			return;
		}
		List<List<String>> filas = new ArrayList<>();
		for (IndexInfo idx : indices) {
			filas.add(List.of(idx.name(), siNo(idx.unique()), idx.type(), String.join(", ", idx.columns())));
		}
		TablaTexto.imprimir(out, List.of("Nombre", "Único", "Tipo", "Columnas"), filas);
	}

	private void mostrarConsulta(QueryResult resultado) {
		subtitulo("Primeros " + resultado.maxRows() + " registros");
		out.println("SQL: " + resultado.sql());
		List<String> encabezados = new ArrayList<>();
		for (QueryResult.ResultColumn c : resultado.columns()) {
			encabezados.add(c.label() + " [" + c.typeName() + "]");
		}
		TablaTexto.imprimir(out, encabezados, resultado.rows());
		out.println(resultado.rows().size() + " registro(s) mostrado(s).");
	}

	private void titulo(String texto) {
		out.println();
		out.println("=".repeat(70));
		out.println(" " + texto);
		out.println("=".repeat(70));
	}

	private void subtitulo(String texto) {
		out.println();
		out.println("--- " + texto + " ---");
	}

	private static String siNo(boolean valor) {
		return valor ? "SI" : "NO";
	}
}
