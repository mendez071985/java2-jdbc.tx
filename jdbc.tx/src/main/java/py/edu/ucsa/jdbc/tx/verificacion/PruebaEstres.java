package py.edu.ucsa.jdbc.tx.verificacion;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariDataSource;

import py.edu.ucsa.jdbc.tx.Database;
import py.edu.ucsa.jdbc.tx.inspector.DatabaseInspectorService;
import py.edu.ucsa.jdbc.tx.inspector.model.TableRef;

/**
 * Prueba de estrés paso a paso del Database Inspector.
 * <p>
 * En cada paso, N hilos concurrentes hacen inspecciones completas de las tablas
 * (información del motor, columnas, PK, FK, índices y consulta de 10 registros),
 * igual que la consola al elegir una tabla. Se mide throughput y latencia y se
 * verifica que no haya errores ni conexiones sin devolver al pool.
 * <p>
 * Argumentos opcionales: {@code <hilos por paso> <iteraciones por hilo>},
 * por ejemplo {@code 1,5,10,25,50 20} (valores por defecto).
 */
public final class PruebaEstres {

	public record Etapa(int hilos, int operaciones, int errores, long duracionMs, double porSegundo,
			double p50, double p95, double p99, double max, int activasAlFinal) {

		public boolean ok(int iteraciones) {
			return errores == 0 && operaciones == hilos * iteraciones && activasAlFinal == 0;
		}
	}

	private PruebaEstres() {
	}

	public static void main(String[] args) throws Exception {
		int[] etapas = parsearEtapas(args.length > 0 ? args[0] : "1,5,10,25,50");
		int iteraciones = args.length > 1 ? Integer.parseInt(args[1]) : 20;
		HikariDataSource pool = (HikariDataSource) Database.getDataSource();
		boolean ok;
		try {
			DatabaseInspectorService inspector = new DatabaseInspectorService(pool, "public");
			List<Etapa> resultados = ejecutar(inspector, pool, etapas, iteraciones, System.out);
			ok = resultados.stream().allMatch(e -> e.ok(iteraciones));
		} finally {
			Database.cerrarPool();
		}
		System.out.println(ok ? "RESULTADO: PRUEBA DE ESTRÉS SUPERADA (0 errores, 0 conexiones perdidas)"
				: "RESULTADO: LA PRUEBA DE ESTRÉS FALLÓ");
		System.exit(ok ? 0 : 1);
	}

	public static int[] parsearEtapas(String texto) {
		return Arrays.stream(texto.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
	}

	public static List<Etapa> ejecutar(DatabaseInspectorService inspector, HikariDataSource pool, int[] etapas,
			int iteraciones, PrintStream out) throws Exception {
		List<TableRef> tablas = inspector.listarTablas();
		if (tablas.isEmpty()) {
			throw new IllegalStateException("No hay tablas para inspeccionar");
		}
		out.printf("%n=== PRUEBA DE ESTRÉS: %s | %d tablas | pool máx. %d conexiones | %d iteraciones por hilo ===%n",
				inspector.obtenerInfoBase().productName(), tablas.size(), pool.getMaximumPoolSize(), iteraciones);
		out.println("Cada operación = información del motor + columnas + PK + FK + índices + consulta (máx. 10 filas)");

		// Calentamiento (JIT y conexiones del pool)
		inspeccionarCompleta(inspector, tablas.get(0));

		List<Etapa> resultados = new ArrayList<>();
		for (int paso = 0; paso < etapas.length; paso++) {
			Etapa e = ejecutarEtapa(inspector, pool, etapas[paso], iteraciones, tablas, out);
			resultados.add(e);
			out.printf("Paso %d/%d: %3d hilos -> %6d ops, %d errores, %6d ms, %8.1f ops/s, "
					+ "p50 %6.2f ms, p95 %6.2f ms, p99 %6.2f ms, máx %7.2f ms, conexiones activas al final: %d  [%s]%n",
					paso + 1, etapas.length, e.hilos(), e.operaciones(), e.errores(), e.duracionMs(), e.porSegundo(),
					e.p50(), e.p95(), e.p99(), e.max(), e.activasAlFinal(), e.ok(iteraciones) ? "OK" : "FALLA");
		}
		out.println();
		return resultados;
	}

	private static Etapa ejecutarEtapa(DatabaseInspectorService inspector, HikariDataSource pool, int hilos,
			int iteraciones, List<TableRef> tablas, PrintStream out) throws InterruptedException {
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
							inspeccionarCompleta(inspector, tabla);
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
		if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
			executor.shutdownNow();
			errores.add(new IllegalStateException("La etapa no terminó a tiempo"));
		}
		long duracionMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));

		errores.stream().limit(3).forEach(e -> out.println("  Error: " + e));

		List<Long> ordenadas = new ArrayList<>(latencias);
		Collections.sort(ordenadas);
		int ops = ordenadas.size();
		return new Etapa(hilos, ops, errores.size(), duracionMs, ops * 1000.0 / duracionMs,
				percentil(ordenadas, 50), percentil(ordenadas, 95), percentil(ordenadas, 99),
				percentil(ordenadas, 100), pool.getHikariPoolMXBean().getActiveConnections());
	}

	/** Lo mismo que hace la consola al elegir una tabla. */
	public static void inspeccionarCompleta(DatabaseInspectorService inspector, TableRef tabla) throws SQLException {
		inspector.obtenerInfoBase();
		inspector.listarColumnas(tabla);
		inspector.obtenerClavePrimaria(tabla);
		inspector.listarClavesForaneas(tabla);
		inspector.listarIndices(tabla);
		if (inspector.consultar(tabla).rows().size() > DatabaseInspectorService.MAX_FILAS) {
			throw new IllegalStateException("Se devolvieron más de " + DatabaseInspectorService.MAX_FILAS + " filas");
		}
	}

	private static double percentil(List<Long> ordenadas, int p) {
		if (ordenadas.isEmpty()) {
			return 0;
		}
		int idx = (int) Math.ceil(p / 100.0 * ordenadas.size()) - 1;
		return ordenadas.get(Math.max(0, idx)) / 1_000_000.0;
	}
}
