package py.edu.ucsa.jdbc.tx.inspector.model;

public record DatabaseInfo(
		String productName,
		String productVersion,
		String driverName,
		String driverVersion,
		String userName,
		String url,
		boolean supportsTransactions,
		boolean supportsBatchUpdates,
		boolean readOnly
) {
}
