package py.edu.ucsa.jdbc.tx.verificacion;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.sql.DataSource;

import py.edu.ucsa.jdbc.tx.Database;
import py.edu.ucsa.jdbc.tx.inspector.DatabaseInspectorConsole;
import py.edu.ucsa.jdbc.tx.inspector.DatabaseInspectorService;
import py.edu.ucsa.jdbc.tx.inspector.model.ColumnInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.DatabaseInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.ForeignKeyInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.IndexInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.PrimaryKeyInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.QueryResult;
import py.edu.ucsa.jdbc.tx.inspector.model.TableRef;

/**
 * Verifica paso a paso, contra la base real, cada requisito del trabajo práctico
 * "Database Inspector" e imprime [OK] / [FALLA] / [AVISO] con la evidencia.
 * <p>
 * No tiene nombres de tablas ni columnas: recorre todas las tablas que descubre.
 * Ejecutar: en STS, clic derecho → Run As → Java Application.
 */
public final class VerificarTP {

	private final DataSource dataSource;
	private final DatabaseInspectorService inspector;
	private final PrintStream out;
	private int ok;
	private int fallas;
	private int avisos;

	public VerificarTP(DataSource dataSource, String schema, PrintStream out) {
		this.dataSource = dataSource;
		this.inspector = new DatabaseInspectorService(dataSource, schema);
		this.out = out;
	}

	public static void main(String[] args) throws Exception {
		boolean todoOk;
		try {
			todoOk = new VerificarTP(Database.getDataSource(), args.length > 0 ? args[0] : "public", System.out).verificar();
		} finally {
			Database.cerrarPool();
		}
		System.exit(todoOk ? 0 : 1);
	}

	/** Devuelve true si no hubo ninguna FALLA. */
	public boolean verificar() throws Exception {
		titulo("VERIFICACIÓN DEL TRABAJO PRÁCTICO: DATABASE INSPECTOR");

		// 1. Conexión
		try (Connection conn = dataSource.getConnection()) {
			resultado(1, "Conectarse a la base de datos utilizando JDBC", conn.isValid(5),
					conn.getMetaData().getURL());
		}

		// 2-6. DatabaseMetaData
		DatabaseInfo info = inspector.obtenerInfoBase();
		resultado(2, "Obtener información del motor con DatabaseMetaData", info != null, "conn.getMetaData()");
		resultado(3, "Mostrar nombre y versión del motor", noVacio(info.productName()) && noVacio(info.productVersion()),
				info.productName() + " " + info.productVersion());
		resultado(4, "Mostrar nombre y versión del driver JDBC", noVacio(info.driverName()) && noVacio(info.driverVersion()),
				info.driverName() + " " + info.driverVersion());
		resultado(5, "Indicar si soporta transacciones", true, siNo(info.supportsTransactions()));
		resultado(6, "Indicar si soporta operaciones batch", true, siNo(info.supportsBatchUpdates()));
		resultado(0, "Extras de los TIPS: usuario, URL, sólo lectura", noVacio(info.userName()) && noVacio(info.url()),
				"usuario=" + info.userName() + ", sólo lectura=" + siNo(info.readOnly()));

		// 7. Tablas
		List<TableRef> tablas = inspector.listarTablas();
		resultado(7, "Obtener dinámicamente las tablas existentes (getTables)", !tablas.isEmpty(),
				tablas.size() + " tablas encontradas");
		if (tablas.isEmpty()) {
			return resumen();
		}

		// 8. Numeración y selección: se usa la consola real con una entrada simulada
		String salidaConsola = simularConsola("abc\n1\n0\n");
		resultado(8, "Numerar las tablas y permitir que el usuario seleccione una",
				salidaConsola.contains("  1. ") && salidaConsola.contains("TABLA " + tablas.get(0).qualifiedName())
						&& salidaConsola.contains("Opción inválida"),
				"se eligió la 1 (" + tablas.get(0).qualifiedName() + "); una opción inválida se vuelve a pedir");

		// Recorrido de todas las tablas
		Map<String, Integer> contadores = new LinkedHashMap<>();
		List<String> problemas = new ArrayList<>();
		String ejemploAutoinc = null, ejemploPkCompuesta = null, ejemploFkCompuesta = null, ejemploMas10 = null;
		int columnasTotales = 0;

		for (TableRef t : tablas) {
			List<ColumnInfo> columnas = inspector.listarColumnas(t);
			PrimaryKeyInfo pk = inspector.obtenerClavePrimaria(t);
			List<ForeignKeyInfo> fks = inspector.listarClavesForaneas(t);
			List<IndexInfo> indices = inspector.listarIndices(t);
			QueryResult consulta = inspector.consultar(t);

			columnasTotales += columnas.size();
			if (columnas.isEmpty()) {
				problemas.add(t.name() + ": sin columnas");
			}
			for (ColumnInfo c : columnas) {
				if (!noVacio(c.name()) || !noVacio(c.sqlType()) || c.nullable() == null || c.autoIncrement() == null) {
					problemas.add(t.name() + "." + c.name() + ": faltan datos de la columna");
				}
				sumar(contadores, "NULL " + c.nullable());
				sumar(contadores, "AUTOINC " + c.autoIncrement());
				if ("SI".equals(c.autoIncrement()) && ejemploAutoinc == null) {
					ejemploAutoinc = t.name() + "." + c.name();
				}
			}
			if (pk.exists()) {
				sumar(contadores, "PK");
				if (pk.columns().size() > 1 && ejemploPkCompuesta == null) {
					ejemploPkCompuesta = t.name() + " " + pk.columns();
				}
			}
			if (!fks.isEmpty()) {
				sumar(contadores, "FK");
				fks.stream().filter(fk -> fk.keySeq() > 1).findFirst()
						.ifPresent(fk -> sumar(contadores, "FK compuesta"));
				if (ejemploFkCompuesta == null) {
					ejemploFkCompuesta = fks.stream().filter(fk -> fk.keySeq() > 1)
							.map(fk -> t.name() + "." + fk.name()).findFirst().orElse(null);
				}
			}
			if (!indices.isEmpty()) {
				sumar(contadores, "IDX");
				indices.forEach(i -> sumar(contadores, i.unique() ? "IDX unico" : "IDX no unico"));
			}

			// 14-16: consulta, ResultSetMetaData y límite de 10
			List<String> etiquetas = consulta.columns().stream().map(QueryResult.ResultColumn::label).toList();
			List<String> nombres = columnas.stream().map(ColumnInfo::name).toList();
			if (!etiquetas.equals(nombres)) {
				problemas.add(t.name() + ": las columnas de ResultSetMetaData no coinciden con getColumns");
			}
			long total = contarFilas(t);
			int mostradas = consulta.rows().size();
			if (mostradas != Math.min(total, DatabaseInspectorService.MAX_FILAS)) {
				problemas.add(t.name() + ": se mostraron " + mostradas + " filas de " + total);
			}
			if (total > DatabaseInspectorService.MAX_FILAS && ejemploMas10 == null) {
				ejemploMas10 = t.name() + " (tiene " + total + ", se muestran " + mostradas + ")";
			}
		}

		resultado(9, "Mostrar las columnas de la tabla seleccionada (getColumns)",
				problemas.stream().noneMatch(p -> p.contains("sin columnas")),
				columnasTotales + " columnas en " + tablas.size() + " tablas");
		resultado(10, "Por cada columna: nombre, tipo SQL, tamaño, NULL, autoincremental",
				problemas.stream().noneMatch(p -> p.contains("faltan datos")),
				"permiten NULL: " + contadores.getOrDefault("NULL SI", 0) + ", NOT NULL: "
						+ contadores.getOrDefault("NULL NO", 0) + ", autoincrementales: "
						+ contadores.getOrDefault("AUTOINC SI", 0));
		aviso(ejemploAutoinc != null, "hay columnas autoincrementales, p. ej. " + ejemploAutoinc,
				"ninguna columna autoincremental");

		resultado(11, "Mostrar la clave primaria (getPrimaryKeys)", true,
				contadores.getOrDefault("PK", 0) + " de " + tablas.size() + " tablas tienen PK");
		aviso(ejemploPkCompuesta != null, "PK compuesta: " + ejemploPkCompuesta, "ninguna tabla con PK compuesta");

		resultado(12, "Mostrar las claves foráneas (getImportedKeys)", true,
				contadores.getOrDefault("FK", 0) + " tablas tienen FK");
		aviso(contadores.getOrDefault("FK", 0) > 0, "hay tablas con FK", "ninguna tabla con FK");
		aviso(ejemploFkCompuesta != null, "FK compuesta: " + ejemploFkCompuesta, "ninguna FK compuesta");

		resultado(13, "Mostrar los índices (getIndexInfo)", true,
				contadores.getOrDefault("IDX", 0) + " tablas con índices ("
						+ contadores.getOrDefault("IDX unico", 0) + " únicos, "
						+ contadores.getOrDefault("IDX no unico", 0) + " no únicos)");
		aviso(contadores.getOrDefault("IDX no unico", 0) > 0, "hay índices no únicos", "ningún índice no único");

		resultado(14, "Ejecutar una consulta sobre la tabla seleccionada", true,
				"SELECT * ejecutado sobre las " + tablas.size() + " tablas");
		resultado(15, "Descubrir las columnas del resultado con ResultSetMetaData",
				problemas.stream().noneMatch(p -> p.contains("ResultSetMetaData")),
				"getColumnCount / getColumnLabel / getColumnTypeName");
		resultado(16, "Mostrar como máximo los primeros 10 registros",
				problemas.stream().noneMatch(p -> p.contains("filas de")),
				ejemploMas10 != null ? ejemploMas10 : "ninguna tabla tiene más de 10 registros");
		aviso(ejemploMas10 != null, "se probó con una tabla de más de 10 registros",
				"ninguna tabla con más de 10 registros");

		verificarRestriccion(tablas);

		problemas.forEach(p -> out.println("      detalle: " + p));
		if (avisos > 0) {
			out.println();
			out.println("Los [AVISO] no son errores: indican casos que su base no tiene para mostrar.");
			out.println("Para tenerlos todos, ejecute py.edu.ucsa.jdbc.tx.demo.PrepararDatos.");
		}
		return resumen();
	}

	private void verificarRestriccion(List<TableRef> tablas) throws SQLException, IOException {
		Path dir = Path.of("src/main/java/py/edu/ucsa/jdbc/tx/inspector");
		if (!Files.isDirectory(dir)) {
			out.printf("[INFO ] 17. Restricción: sin nombres de tablas/columnas programados -> "
					+ "no se encontró el código fuente; lo verifica el test restriccion_... (mvn test)%n");
			return;
		}
		StringBuilder codigo = new StringBuilder();
		try (Stream<Path> archivos = Files.walk(dir)) {
			for (Path p : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
				codigo.append(Files.readString(p));
			}
		}
		List<String> encontrados = new ArrayList<>();
		for (TableRef t : tablas) {
			if (codigo.indexOf("\"" + t.name() + "\"") >= 0) {
				encontrados.add(t.name());
			}
			for (ColumnInfo c : inspector.listarColumnas(t)) {
				if (codigo.indexOf("\"" + c.name() + "\"") >= 0) {
					encontrados.add(t.name() + "." + c.name());
				}
			}
		}
		resultado(17, "Restricción: no programar nombres de tablas ni columnas", encontrados.isEmpty(),
				encontrados.isEmpty() ? "ningún nombre de la base aparece en el código del inspector"
						: "aparecen en el código: " + encontrados);
	}

	private String simularConsola(String entrada) throws SQLException, IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream salida = new PrintStream(buffer, true, StandardCharsets.UTF_8);
		new DatabaseInspectorConsole(inspector, new BufferedReader(new StringReader(entrada)), salida).ejecutar();
		return buffer.toString(StandardCharsets.UTF_8);
	}

	private long contarFilas(TableRef t) throws SQLException {
		try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM "
						+ DatabaseInspectorService.nombreCalificado(conn.getMetaData(), t))) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private void resultado(int paso, String requisito, boolean cumple, String evidencia) {
		if (cumple) {
			ok++;
		} else {
			fallas++;
		}
		String numero = paso > 0 ? String.format("%2d.", paso) : "  +";
		out.printf("[%s] %s %s%n        -> %s%n", cumple ? " OK  " : "FALLA", numero, requisito, evidencia);
	}

	private void aviso(boolean cumple, String siCumple, String siNo) {
		if (cumple) {
			out.printf("        -> %s%n", siCumple);
		} else {
			avisos++;
			out.printf("[AVISO]     %s%n", siNo);
		}
	}

	private boolean resumen() {
		out.println();
		out.println("=".repeat(70));
		out.printf(" RESULTADO: %d OK, %d FALLAS, %d AVISOS -> %s%n", ok, fallas, avisos,
				fallas == 0 ? "EL TRABAJO PRÁCTICO FUNCIONA" : "HAY REQUISITOS QUE FALLAN");
		out.println("=".repeat(70));
		return fallas == 0;
	}

	private void titulo(String texto) {
		out.println("=".repeat(70));
		out.println(" " + texto);
		out.println("=".repeat(70));
	}

	private static void sumar(Map<String, Integer> contadores, String clave) {
		contadores.merge(clave, 1, Integer::sum);
	}

	private static boolean noVacio(String s) {
		return s != null && !s.isBlank();
	}

	private static String siNo(boolean valor) {
		return valor ? "SI" : "NO";
	}
}
