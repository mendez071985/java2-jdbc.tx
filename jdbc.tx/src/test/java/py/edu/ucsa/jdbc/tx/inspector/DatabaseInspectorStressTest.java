package py.edu.ucsa.jdbc.tx.inspector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.zaxxer.hikari.HikariDataSource;

import py.edu.ucsa.jdbc.tx.inspector.model.TableRef;

/**
 * Prueba de estrés paso a paso: ejecuta la inspección completa de las tablas
 * (metadatos + consulta) con cantidades crecientes de hilos concurrentes y
 * verifica que no haya errores ni conexiones perdidas en el pool.
 * <p>
 * Parámetros (system properties):
 * <ul>
 * <li>{@code stress.stages} hilos por etapa, por defecto {@code 1,5,10,25,50}</li>
 * <li>{@code stress.iterations} inspecciones completas por hilo, por defecto {@code 20}</li>
 * </ul>
 * Uso: {@code mvn test -Pstress} (H2) o
 * {@code mvn test -Pstress -Dspring.profiles.active=pg} (PostgreSQL).
 */
@Tag("stress")
@SpringBootTest
class DatabaseInspectorStressTest {

	@Autowired
	DatabaseInspectorService inspector;

	@Autowired
	DataSource dataSource;

	record Etapa(int hilos, int operaciones, int errores, long duracionMs, double porSegundo,
			double p50, double p95, double p99, double max, int activasAlFinal) {
	}

	@Test
	void estresPasoAPaso() throws Exception {
		int[] etapas = Arrays.stream(System.getProperty("stress.stages", "1,5,10,25,50").split(","))
				.map(String::trim).mapToInt(Integer::parseInt).toArray();
		int iteraciones = Integer.getInteger("stress.iterations", 20);
		List<TableRef> tablas = inspector.listarTablas();
		assertThat(tablas).isNotEmpty();

		HikariDataSource pool = (HikariDataSource) dataSource;
		System.out.printf("%n=== PRUEBA DE ESTRÉS: %s | %d tablas | pool máx. %d conexiones | %d iteraciones por hilo ===%n",
				inspector.obtenerInfoBase().productName(), tablas.size(), pool.getMaximumPoolSize(), iteraciones);

		// Calentamiento (JIT, conexiones del pool)
		inspeccionarCompleta(tablas.get(0));

		List<Etapa> resultados = new ArrayList<>();
		for (int paso = 0; paso < etapas.length; paso++) {
			Etapa etapa = ejecutarEtapa(etapas[paso], iteraciones, tablas, pool);
			resultados.add(etapa);
			System.out.printf("Paso %d/%d: %3d hilos -> %6d ops, %d errores, %6d ms, %8.1f ops/s, "
					+ "p50 %6.2f ms, p95 %6.2f ms, p99 %6.2f ms, máx %7.2f ms, conexiones activas al final: %d%n",
					paso + 1, etapas.length, etapa.hilos(), etapa.operaciones(), etapa.errores(), etapa.duracionMs(),
					etapa.porSegundo(), etapa.p50(), etapa.p95(), etapa.p99(), etapa.max(), etapa.activasAlFinal());
		}
		System.out.println();

		assertThat(resultados).allSatisfy(e -> {
			assertThat(e.errores()).as("errores con %d hilos", e.hilos()).isZero();
			assertThat(e.operaciones()).isEqualTo(e.hilos() * iteraciones);
			assertThat(e.activasAlFinal()).as("conexiones sin devolver al pool (fuga)").isZero();
		});
	}

	private Etapa ejecutarEtapa(int hilos, int iteraciones, List<TableRef> tablas, HikariDataSource pool)
			throws InterruptedException {
		ConcurrentLinkedQueue<Long> latencias = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<Throwable> errores = new ConcurrentLinkedQueue<>();
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(hilos);

		for (int h = 0; h < hilos; h++) {
			int hilo = h;
			executor.submit(() -> {
				try {
					largada.await();
					for (int i = 0; i < iteraciones; i++) {
						TableRef tabla = tablas.get((hilo + i) % tablas.size());
						long inicio = System.nanoTime();
						try {
							inspeccionarCompleta(tabla);
							latencias.add(System.nanoTime() - inicio);
						} catch (Exception e) {
							errores.add(e);
						}
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}

		long inicio = System.nanoTime();
		largada.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.MINUTES)).as("la etapa terminó a tiempo").isTrue();
		long duracionMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));

		errores.stream().limit(3).forEach(Throwable::printStackTrace);

		List<Long> ordenadas = new ArrayList<>(latencias);
		Collections.sort(ordenadas);
		int ops = ordenadas.size();
		return new Etapa(hilos, ops, errores.size(), duracionMs, ops * 1000.0 / duracionMs,
				percentil(ordenadas, 50), percentil(ordenadas, 95), percentil(ordenadas, 99),
				percentil(ordenadas, 100), pool.getHikariPoolMXBean().getActiveConnections());
	}

	/** Lo mismo que hace la consola al elegir una tabla. */
	private void inspeccionarCompleta(TableRef tabla) throws Exception {
		inspector.obtenerInfoBase();
		inspector.listarColumnas(tabla);
		inspector.obtenerClavePrimaria(tabla);
		inspector.listarClavesForaneas(tabla);
		inspector.listarIndices(tabla);
		assertThat(inspector.consultar(tabla).rows().size()).isLessThanOrEqualTo(DatabaseInspectorService.MAX_FILAS);
	}

	private static double percentil(List<Long> ordenadas, int p) {
		if (ordenadas.isEmpty()) {
			return 0;
		}
		int idx = (int) Math.ceil(p / 100.0 * ordenadas.size()) - 1;
		return ordenadas.get(Math.max(0, idx)) / 1_000_000.0;
	}
}
