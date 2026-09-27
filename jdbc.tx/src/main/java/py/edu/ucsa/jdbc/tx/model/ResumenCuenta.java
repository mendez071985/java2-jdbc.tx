package py.edu.ucsa.jdbc.tx.model;

import java.math.BigDecimal;

/** Resumen por funcionario: saldo y totales de movimientos (JOIN + GROUP BY). */
public record ResumenCuenta(
		long funcionarioId,
		String funcionario,
		BigDecimal saldo,
		long cantidadMovimientos,
		BigDecimal totalCreditos,
		BigDecimal totalDebitos
) {
}
