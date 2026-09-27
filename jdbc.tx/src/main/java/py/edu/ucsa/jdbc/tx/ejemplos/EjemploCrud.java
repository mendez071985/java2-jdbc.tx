package py.edu.ucsa.jdbc.tx.ejemplos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import py.edu.ucsa.jdbc.tx.dao.FuncionarioDao;
import py.edu.ucsa.jdbc.tx.model.Funcionario;

/** 1. CRUD: INSERT, SELECT, UPDATE y DELETE con PreparedStatement. */
final class EjemploCrud {

	private EjemploCrud() {
	}

	static void ejecutar() throws Exception {
		Salida.titulo("1. CRUD: INSERT, SELECT, UPDATE y DELETE");
		Salida.info("CRUD = Create (INSERT), Read (SELECT), Update (UPDATE), Delete (DELETE).",
				"Todo con PreparedStatement: los valores van como parámetros (?), nunca pegados al SQL.");
		FuncionarioDao dao = new FuncionarioDao();

		Salida.paso("PASO 1: INSERT de varios funcionarios (PreparedStatement + RETURN_GENERATED_KEYS)");
		Salida.info("prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) + executeUpdate().",
				"getGeneratedKeys() devuelve el ID que generó la base (columna IDENTITY/SERIAL).",
				"setNull(..., Types.INTEGER) para la edad vacía, setBytes(...) para la foto, setObject(...) para LocalDate.");
		List<Funcionario> nuevos = List.of(
				new Funcionario(null, "Casilda", "Pereira", null,          // edad NULL
						LocalDate.of(1994, 8, 17), LocalDate.now(), null, LocalDateTime.now(), 1009),
				new Funcionario(null, "Ana", "Benítez", 30,
						LocalDate.of(1996, 3, 2), LocalDate.of(2020, 2, 1),
						new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE }, LocalDateTime.now(), 1010),
				new Funcionario(null, "Carlos", "Giménez", 45,
						LocalDate.of(1981, 11, 23), LocalDate.of(2010, 7, 15), null, LocalDateTime.now(), 1011));

		List<Long> ids = new ArrayList<>();
		for (Funcionario f : nuevos) {
			long id = dao.insertar(f);
			ids.add(id);
			System.out.println("  Insertado: " + f.nombre() + " " + f.apellido() + " -> ID = " + id);
		}

		Salida.paso("PASO 2: SELECT de los registros insertados (buscarPorId)");
		Salida.info("executeQuery() + ResultSet: rs.getObject(\"edad\", Integer.class) devuelve null si es NULL",
				"(rs.getInt devolvería 0). buscarPorId devuelve Optional: vacío si el ID no existe.");
		for (long id : ids) {
			Salida.mostrar(buscar(dao, id));
		}

		Salida.paso("PASO 3: UPDATE de los registros insertados");
		Salida.info("executeUpdate() devuelve cuántas filas cambió: 1 = actualizado, 0 = ese ID no existe.");
		Funcionario casilda = buscar(dao, ids.get(0));
		System.out.println("  ID " + casilda.id() + " (edad NULL -> 32 y se carga una foto): " + resultado(dao.actualizar(
				new Funcionario(casilda.id(), casilda.nombre(), casilda.apellido(), 32,
						casilda.fechaNacimiento(), casilda.fechaIngreso(), new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 },
						LocalDateTime.now(), casilda.legajo()))));

		Funcionario ana = buscar(dao, ids.get(1));
		System.out.println("  ID " + ana.id() + " (apellido, edad +1 y se quita la foto): " + resultado(dao.actualizar(
				new Funcionario(ana.id(), ana.nombre(), "Benítez Rojas", ana.edad() + 1,
						ana.fechaNacimiento(), ana.fechaIngreso(), null, LocalDateTime.now(), ana.legajo()))));

		Funcionario carlos = buscar(dao, ids.get(2));
		System.out.println("  ID " + carlos.id() + " (fecha de ingreso y legajo): " + resultado(dao.actualizar(
				new Funcionario(carlos.id(), carlos.nombre(), carlos.apellido(), carlos.edad(),
						carlos.fechaNacimiento(), LocalDate.now(), carlos.foto(), LocalDateTime.now(), 2011))));

		System.out.println("  ID -1 (no existe): " + resultado(dao.actualizar(
				new Funcionario(-1L, "No", "Existe", null, LocalDate.now(), LocalDate.now(), null, LocalDateTime.now(), 0))));

		Salida.paso("PASO 4: SELECT de los registros actualizados");
		Salida.info("Se vuelven a leer de la base para comprobar que los cambios quedaron guardados.");
		for (long id : ids) {
			Salida.mostrar(buscar(dao, id));
		}

		Salida.paso("PASO 5: DELETE de un registro (" + carlos.nombre() + ", ID " + carlos.id() + ")");
		Salida.info("DELETE ... WHERE id = ?  -> executeUpdate() devuelve 1 si lo borró, 0 si ya no existía.");
		System.out.println("  eliminar(" + carlos.id() + ") -> " + (dao.eliminar(carlos.id()) ? "OK (1 fila borrada)" : "no existía"));
		System.out.println("  buscarPorId(" + carlos.id() + ") -> "
				+ dao.buscarPorId(carlos.id()).map(f -> "todavía existe").orElse("no existe (Optional.empty)"));
		System.out.println("  eliminar(" + carlos.id() + ") otra vez -> "
				+ (dao.eliminar(carlos.id()) ? "OK" : "no existía (0 filas)"));

		Salida.paso("PASO 6: SELECT de todos (últimos 5 de " + dao.contar() + ")");
		List<Funcionario> todos = dao.listarTodos();
		Salida.mostrar(todos.subList(Math.max(0, todos.size() - 5), todos.size()), 5);
	}

	private static Funcionario buscar(FuncionarioDao dao, long id) throws Exception {
		return dao.buscarPorId(id).orElseThrow(() -> new IllegalStateException("No se encontró el funcionario " + id));
	}

	private static String resultado(boolean actualizado) {
		return actualizado ? "OK (1 fila actualizada)" : "no se actualizó ninguna fila";
	}
}
