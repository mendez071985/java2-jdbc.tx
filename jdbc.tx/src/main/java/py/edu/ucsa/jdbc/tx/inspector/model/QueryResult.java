package py.edu.ucsa.jdbc.tx.inspector.model;

import java.util.List;

/**
 * Resultado de una consulta cuya estructura se descubrió con ResultSetMetaData.
 * {@code columns} y los valores de cada fila van en el mismo orden.
 */
public record QueryResult(String sql, List<ResultColumn> columns, List<List<String>> rows, int maxRows) {

	public record ResultColumn(String label, String typeName, String javaClass) {
	}
}
