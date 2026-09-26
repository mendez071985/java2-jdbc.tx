package py.edu.ucsa.jdbc.tx.inspector.model;

public record ForeignKeyInfo(
		String name,
		int keySeq,
		String column,
		String referencedTable,
		String referencedColumn,
		String onUpdate,
		String onDelete
) {
}
