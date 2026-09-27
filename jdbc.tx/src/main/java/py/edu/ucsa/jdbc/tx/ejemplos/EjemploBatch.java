package py.edu.ucsa.jdbc.tx.ejemplos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import py.edu.ucsa.jdbc.tx.dao.FuncionarioDao;
import py.edu.ucsa.jdbc.tx.model.Funcionario;

/**
 * 4. INSERT en batch contra INSERT uno por uno, y DELETE en batch.
 * Al final se borran los registros creados, la tabla queda como estaba.
 */
final class EjemploBatch {

	private static final int CANTIDAD = 500;

	private EjemploBatch() {
	}

	static void ejecutar() throws Exception {
		Salida.titulo("4. INSERT EN BATCH vs UNO POR UNO, y DELETE EN BATCH (" + CANTIDAD + " registros)");
		FuncionarioDao dao = new FuncionarioDao();
		long antes = dao.contar();
		System.out.println("  Funcionarios en la tabla antes: " + antes);

		Salida.paso("A) " + CANTIDAD + " INSERT uno por uno (una conexión y un commit por cada fila)");
		long inicio = System.nanoTime();
		List<Long> idsUnoPorUno = new ArrayList<>();
		for (Funcionario f : generar("UnoPorUno", 8000)) {
			idsUnoPorUno.add(dao.insertar(f));
		}
		long msUnoPorUno = ms(inicio);
		System.out.println("  Tiempo: " + msUnoPorUno + " ms  (IDs " + idsUnoPorUno.get(0) + " .. "
				+ idsUnoPorUno.get(idsUnoPorUno.size() - 1) + ")");

		Salida.paso("B) " + CANTIDAD + " INSERT en batch (addBatch/executeBatch, una conexión y un solo commit)");
		inicio = System.nanoTime();
		List<Long> idsBatch = dao.insertarVarios(generar("Batch", 8500));
		long msBatch = ms(inicio);
		System.out.println("  Tiempo: " + msBatch + " ms  (IDs " + idsBatch.get(0) + " .. "
				+ idsBatch.get(idsBatch.size() - 1) + ", " + idsBatch.size() + " IDs generados)");

		Salida.paso("Comparación");
		System.out.printf("  Uno por uno: %6d ms%n  Batch:       %6d ms%n  El batch fue %.1f veces más rápido.%n",
				msUnoPorUno, msBatch, (double) msUnoPorUno / Math.max(1, msBatch));
		System.out.println("  Funcionarios en la tabla ahora: " + dao.contar() + " (+" + (dao.contar() - antes) + ")");

		Salida.paso("Algunos de los insertados en batch (SELECT por ID)");
		for (int i = 0; i < 3; i++) {
			dao.buscarPorId(idsBatch.get(i)).ifPresent(Salida::mostrar);
		}

		Salida.paso("DELETE en batch de los " + (idsUnoPorUno.size() + idsBatch.size()) + " registros creados");
		List<Long> todos = new ArrayList<>(idsUnoPorUno);
		todos.addAll(idsBatch);
		inicio = System.nanoTime();
		int borrados = dao.eliminarVarios(todos);
		System.out.println("  Filas borradas: " + borrados + " en " + ms(inicio) + " ms");
		System.out.println("  Funcionarios en la tabla después: " + dao.contar() + " (igual que al inicio: " + antes + ")");
	}

	private static List<Funcionario> generar(String apellido, int legajoInicial) {
		List<Funcionario> lista = new ArrayList<>(CANTIDAD);
		for (int i = 1; i <= CANTIDAD; i++) {
			lista.add(new Funcionario(null, "Prueba" + i, apellido, 20 + i % 40,
					LocalDate.of(1990, 1, 1).plusDays(i), LocalDate.of(2025, 1, 1), null,
					LocalDateTime.now(), legajoInicial + i));
		}
		return lista;
	}

	private static long ms(long inicioNanos) {
		return (System.nanoTime() - inicioNanos) / 1_000_000;
	}
}
