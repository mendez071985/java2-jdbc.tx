package py.edu.ucsa.jdbc.tx.inspector.model;

import java.util.List;

/** Clave primaria; las columnas vienen ordenadas por KEY_SEQ. */
public record PrimaryKeyInfo(String name, List<String> columns) {

	public boolean exists() {
		return !columns.isEmpty();
	}
}
