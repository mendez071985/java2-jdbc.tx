package py.edu.ucsa.jdbc.tx.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Funcionario(
		Long id,
		String nombre,
		String apellido,
		Integer edad,
		LocalDate fechaNacimiento,
		LocalDate fechaIngreso,
		byte[] foto,
		LocalDateTime fechaUltModif,
		Integer legajo
) {
}
