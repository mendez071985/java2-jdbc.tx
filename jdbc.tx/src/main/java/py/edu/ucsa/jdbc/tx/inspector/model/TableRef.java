package py.edu.ucsa.jdbc.tx.inspector.model;

/** Referencia a una tabla descubierta dinámicamente con DatabaseMetaData.getTables(). */
public record TableRef(String catalog, String schema, String name, String remarks) {

	public String qualifiedName() {
		return schema == null || schema.isBlank() ? name : schema + "." + name;
	}
}
