package py.edu.ucsa.jdbc.tx.inspector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import py.edu.ucsa.jdbc.tx.verificacion.VerificarTP;

/** El verificador paso a paso no encuentra fallas en la base de prueba. */
@SpringBootTest
class VerificarTPTest {

	@Autowired
	DataSource dataSource;

	@Test
	void todosLosRequisitosSeCumplen() throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		boolean ok = new VerificarTP(dataSource, "public", new PrintStream(buffer, true, StandardCharsets.UTF_8)).verificar();
		String salida = buffer.toString(StandardCharsets.UTF_8);

		assertThat(ok).as(salida).isTrue();
		assertThat(salida).contains("0 FALLAS", "17. Restricción").doesNotContain("[FALLA]");
		for (int paso = 1; paso <= 16; paso++) {
			assertThat(salida).contains(String.format("%2d. ", paso));
		}
	}
}
