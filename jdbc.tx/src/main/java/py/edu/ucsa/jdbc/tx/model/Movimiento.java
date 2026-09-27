package py.edu.ucsa.jdbc.tx.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un movimiento de cuenta junto con el nombre del funcionario (resultado de un JOIN). */
public record Movimiento(
		long id,
		long funcionarioId,
		String funcionario,
		String tipo,
		BigDecimal monto,
		LocalDateTime fechaHora,
		String descripcion
) {
}
