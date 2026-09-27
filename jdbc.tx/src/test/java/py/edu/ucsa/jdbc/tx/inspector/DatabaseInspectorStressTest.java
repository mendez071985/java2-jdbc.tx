package py.edu.ucsa.jdbc.tx.inspector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.zaxxer.hikari.HikariDataSource;

import py.edu.ucsa.jdbc.tx.verificacion.PruebaEstres;

/**
 * Prueba de estrés paso a paso (la lógica está en {@link PruebaEstres}, que
 * también se puede ejecutar como programa desde STS).
 * <p>
 * Parámetros: {@code -Dstress.stages=1,5,10,25,50} y {@code -Dstress.iterations=20}.
 * Uso: {@code mvn test -Pstress} (H2) o {@code mvn test -Pstress -Dspring.profiles.active=pg}.
 */
@Tag("stress")
@SpringBootTest
class DatabaseInspectorStressTest {

	@Autowired
	DatabaseInspectorService inspector;

	@Autowired
	DataSource dataSource;

	@Test
	void estresPasoAPaso() throws Exception {
		int[] etapas = PruebaEstres.parsearEtapas(System.getProperty("stress.stages", "1,5,10,25,50"));
		int iteraciones = Integer.getInteger("stress.iterations", 20);

		List<PruebaEstres.Etapa> resultados =
				PruebaEstres.ejecutar(inspector, (HikariDataSource) dataSource, etapas, iteraciones, System.out);

		assertThat(resultados).hasSize(etapas.length).allSatisfy(e -> {
			assertThat(e.errores()).as("errores con %d hilos", e.hilos()).isZero();
			assertThat(e.operaciones()).isEqualTo(e.hilos() * iteraciones);
			assertThat(e.activasAlFinal()).as("conexiones sin devolver al pool (fuga)").isZero();
		});
	}
}
