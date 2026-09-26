package py.edu.ucsa.jdbc.tx.inspector;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import py.edu.ucsa.jdbc.tx.inspector.model.ColumnInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.DatabaseInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.ForeignKeyInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.IndexInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.PrimaryKeyInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.QueryResult;
import py.edu.ucsa.jdbc.tx.inspector.model.TableRef;

/**
 * Inspecciona dinámicamente la estructura de la base usando sólo
 * {@link DatabaseMetaData} y {@link ResultSetMetaData}.
 * <p>
 * No hay nombres de tablas ni de columnas de negocio programados: todo se
 * descubre en tiempo de ejecución. Los únicos literales son los nombres de las
 * columnas que define la especificación JDBC para los ResultSet de metadatos
 * (TABLE_NAME, COLUMN_NAME, ...).
 * <p>
 * Cada método abre y cierra su propia conexión del pool, por lo que el servicio
 * es seguro para usarse desde varios hilos a la vez.
 */
@Service
public class DatabaseInspectorService {

	public static final int MAX_FILAS = 10;
	private static final int MAX_ANCHO_VALOR = 40;

	private final DataSource dataSource;
	private final String schema;

	public DatabaseInspectorService(DataSource dataSource,
			@Value("${inspector.schema:public}") String schema) {
		this.dataSource = dataSource;
		this.schema = schema == null || schema.isBlank() ? null : schema;
	}

	// Parte 1 - Información de la base de datos
	public DatabaseInfo obtenerInfoBase() throws SQLException {
		try (Connection conn = dataSource.getConnection()) {
			DatabaseMetaData md = conn.getMetaData();
			return new DatabaseInfo(
					md.getDatabaseProductName(),
					md.getDatabaseProductVersion(),
					md.getDriverName(),
					md.getDriverVersion(),
					md.getUserName(),
					md.getURL(),
					md.supportsTransactions(),
					md.supportsBatchUpdates(),
					md.isReadOnly());
		}
	}

	// Parte 2 - Descubrir las tablas
	public List<TableRef> listarTablas() throws SQLException {
		List<TableRef> tablas = new ArrayList<>();
		try (Connection conn = dataSource.getConnection();
				ResultSet rs = conn.getMetaData().getTables(null, schema, null, new String[] { "TABLE" })) {
			while (rs.next()) {
				tablas.add(new TableRef(
						rs.getString("TABLE_CAT"),
						rs.getString("TABLE_SCHEM"),
						rs.getString("TABLE_NAME"),
						rs.getString("REMARKS")));
			}
		}
		return tablas;
	}

	// Parte 3 - Columnas de la tabla elegida
	public List<ColumnInfo> listarColumnas(TableRef tabla) throws SQLException {
		List<ColumnInfo> columnas = new ArrayList<>();
		try (Connection conn = dataSource.getConnection();
				ResultSet rs = conn.getMetaData().getColumns(tabla.catalog(), tabla.schema(), tabla.name(), null)) {
			while (rs.next()) {
				columnas.add(new ColumnInfo(
						rs.getInt("ORDINAL_POSITION"),
						rs.getString("COLUMN_NAME"),
						rs.getString("TYPE_NAME"),
						rs.getInt("DATA_TYPE"),
						rs.getInt("COLUMN_SIZE"),
						rs.getInt("DECIMAL_DIGITS"),
						describirNullable(rs.getInt("NULLABLE")),
						describirSiNo(rs.getString("IS_AUTOINCREMENT")),
						rs.getString("COLUMN_DEF")));
			}
		}
		return columnas;
	}

	// Parte 4 - Clave primaria
	public PrimaryKeyInfo obtenerClavePrimaria(TableRef tabla) throws SQLException {
		Map<Short, String> columnasPorSecuencia = new TreeMap<>();
		String nombre = null;
		try (Connection conn = dataSource.getConnection();
				ResultSet rs = conn.getMetaData().getPrimaryKeys(tabla.catalog(), tabla.schema(), tabla.name())) {
			while (rs.next()) {
				nombre = rs.getString("PK_NAME");
				columnasPorSecuencia.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
			}
		}
		return new PrimaryKeyInfo(nombre, List.copyOf(columnasPorSecuencia.values()));
	}

	// Parte 5 - Claves foráneas
	public List<ForeignKeyInfo> listarClavesForaneas(TableRef tabla) throws SQLException {
		List<ForeignKeyInfo> fks = new ArrayList<>();
		try (Connection conn = dataSource.getConnection();
				ResultSet rs = conn.getMetaData().getImportedKeys(tabla.catalog(), tabla.schema(), tabla.name())) {
			while (rs.next()) {
				String pkSchema = rs.getString("PKTABLE_SCHEM");
				String pkTable = rs.getString("PKTABLE_NAME");
				fks.add(new ForeignKeyInfo(
						rs.getString("FK_NAME"),
						rs.getShort("KEY_SEQ"),
						rs.getString("FKCOLUMN_NAME"),
						pkSchema == null ? pkTable : pkSchema + "." + pkTable,
						rs.getString("PKCOLUMN_NAME"),
						describirRegla(rs.getShort("UPDATE_RULE")),
						describirRegla(rs.getShort("DELETE_RULE"))));
			}
		}
		return fks;
	}

	// Parte 6 - Índices
	public List<IndexInfo> listarIndices(TableRef tabla) throws SQLException {
		// índice -> columnas ordenadas por ORDINAL_POSITION
		Map<String, TreeMap<Short, String>> columnas = new LinkedHashMap<>();
		Map<String, Boolean> unicos = new LinkedHashMap<>();
		Map<String, String> tipos = new LinkedHashMap<>();
		try (Connection conn = dataSource.getConnection();
				ResultSet rs = conn.getMetaData().getIndexInfo(tabla.catalog(), tabla.schema(), tabla.name(), false, true)) {
			while (rs.next()) {
				short tipo = rs.getShort("TYPE");
				String indice = rs.getString("INDEX_NAME");
				if (tipo == DatabaseMetaData.tableIndexStatistic || indice == null) {
					continue;
				}
				String columna = rs.getString("COLUMN_NAME");
				columnas.computeIfAbsent(indice, k -> new TreeMap<>())
						.put(rs.getShort("ORDINAL_POSITION"), columna == null ? "(expresión)" : columna);
				unicos.put(indice, !rs.getBoolean("NON_UNIQUE"));
				tipos.put(indice, describirTipoIndice(tipo));
			}
		}
		List<IndexInfo> indices = new ArrayList<>();
		columnas.forEach((indice, cols) -> indices.add(
				new IndexInfo(indice, unicos.get(indice), tipos.get(indice), List.copyOf(cols.values()))));
		return indices;
	}

	// Parte 7 - Consulta sobre la tabla elegida + ResultSetMetaData
	public QueryResult consultar(TableRef tabla) throws SQLException {
		PrimaryKeyInfo pk = obtenerClavePrimaria(tabla);
		try (Connection conn = dataSource.getConnection()) {
			DatabaseMetaData md = conn.getMetaData();
			String sql = "SELECT * FROM " + nombreCalificado(md, tabla);
			if (pk.exists()) {
				// Ordenar por la PK (descubierta, no programada) para que "los primeros 10" sean deterministas
				String q = comillas(md);
				StringBuilder orden = new StringBuilder();
				for (String columna : pk.columns()) {
					orden.append(orden.isEmpty() ? "" : ", ").append(citar(columna, q));
				}
				sql += " ORDER BY " + orden;
			}
			try (Statement st = conn.createStatement()) {
				st.setMaxRows(MAX_FILAS);
				try (ResultSet rs = st.executeQuery(sql)) {
					ResultSetMetaData rsmd = rs.getMetaData();
					int cantidad = rsmd.getColumnCount();

					List<QueryResult.ResultColumn> columnas = new ArrayList<>(cantidad);
					for (int i = 1; i <= cantidad; i++) {
						columnas.add(new QueryResult.ResultColumn(
								rsmd.getColumnLabel(i),
								rsmd.getColumnTypeName(i),
								rsmd.getColumnClassName(i)));
					}

					List<List<String>> filas = new ArrayList<>();
					while (rs.next() && filas.size() < MAX_FILAS) {
						List<String> fila = new ArrayList<>(cantidad);
						for (int i = 1; i <= cantidad; i++) {
							fila.add(formatearValor(rs.getObject(i)));
						}
						filas.add(fila);
					}
					return new QueryResult(sql, columnas, filas, MAX_FILAS);
				}
			}
		}
	}

	/**
	 * Arma "esquema"."tabla" usando el carácter de comillas que informa el motor,
	 * para soportar mayúsculas, espacios o palabras reservadas en los nombres.
	 */
	static String nombreCalificado(DatabaseMetaData md, TableRef tabla) throws SQLException {
		String q = comillas(md);
		String nombre = citar(tabla.name(), q);
		if (tabla.schema() != null && !tabla.schema().isBlank()) {
			nombre = citar(tabla.schema(), q) + "." + nombre;
		}
		return nombre;
	}

	private static String comillas(DatabaseMetaData md) throws SQLException {
		String q = md.getIdentifierQuoteString();
		return q == null || q.isBlank() ? "" : q;
	}

	private static String citar(String identificador, String q) {
		if (q.isEmpty()) {
			return identificador;
		}
		return q + identificador.replace(q, q + q) + q;
	}

	static String formatearValor(Object valor) {
		if (valor == null) {
			return "NULL";
		}
		String texto = valor instanceof byte[] bytes ? "[" + bytes.length + " bytes]" : valor.toString();
		texto = texto.replace('\n', ' ').replace('\r', ' ');
		return texto.length() > MAX_ANCHO_VALOR ? texto.substring(0, MAX_ANCHO_VALOR - 3) + "..." : texto;
	}

	static String nombreTipoJdbc(int dataType) {
		try {
			return JDBCType.valueOf(dataType).getName();
		} catch (IllegalArgumentException e) {
			return String.valueOf(dataType);
		}
	}

	private static String describirNullable(int nullable) {
		return switch (nullable) {
		case DatabaseMetaData.columnNoNulls -> "NO";
		case DatabaseMetaData.columnNullable -> "SI";
		default -> "DESCONOCIDO";
		};
	}

	private static String describirSiNo(String valor) {
		if ("YES".equalsIgnoreCase(valor)) {
			return "SI";
		}
		if ("NO".equalsIgnoreCase(valor)) {
			return "NO";
		}
		return "DESCONOCIDO";
	}

	private static String describirRegla(short regla) {
		return switch (regla) {
		case DatabaseMetaData.importedKeyCascade -> "CASCADE";
		case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
		case DatabaseMetaData.importedKeySetNull -> "SET NULL";
		case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
		default -> "NO ACTION";
		};
	}

	private static String describirTipoIndice(short tipo) {
		return switch (tipo) {
		case DatabaseMetaData.tableIndexClustered -> "CLUSTERED";
		case DatabaseMetaData.tableIndexHashed -> "HASHED";
		default -> "OTHER";
		};
	}
}
