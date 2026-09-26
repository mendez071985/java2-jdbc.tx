package py.edu.ucsa.jdbc.tx.inspector.model;

public record ColumnInfo(
		int position,
		String name,
		String sqlType,
		int jdbcType,
		int size,
		int decimalDigits,
		String nullable,
		String autoIncrement,
		String defaultValue
) {
}
