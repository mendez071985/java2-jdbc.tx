package py.edu.ucsa.jdbc.tx.ejemplos;

import java.time.LocalDate;
import java.util.List;

import py.edu.ucsa.jdbc.tx.dao.CuentaDao;
import py.edu.ucsa.jdbc.tx.dao.FuncionarioDao;
import py.edu.ucsa.jdbc.tx.model.Funcionario;
import py.edu.ucsa.jdbc.tx.model.ResumenCuenta;

/** 3. Consultas SELECT: LIKE, BETWEEN, COUNT/AVG, paginación y JOIN. */
final class EjemploConsultas {

	private EjemploConsultas() {
	}

	static void ejecutar() throws Exception {
		Salida.titulo("3. CONSULTAS SELECT: LIKE, BETWEEN, COUNT/AVG, paginación y JOIN");
		Salida.info("Todas las consultas usan PreparedStatement con parámetros (?).");
		FuncionarioDao dao = new FuncionarioDao();

		Salida.paso("COUNT y AVG (funciones de agregación)");
		Salida.info("Las funciones de agregación devuelven una sola fila: rs.next() y rs.getLong(1) / rs.getBigDecimal(1).",
				"AVG ignora los NULL; si no hay edades devuelve NULL (por eso promedioEdad() devuelve Optional).");
		System.out.println("  SELECT COUNT(*) FROM funcionarios  -> " + dao.contar());
		System.out.println("  SELECT AVG(edad) FROM funcionarios -> "
				+ dao.promedioEdad().map(p -> p + " años").orElse("NULL (no hay edades cargadas)"));

		Salida.paso("LIKE: apellidos que empiezan con \"Ben\"  (WHERE apellido LIKE ?  con 'Ben%')");
		Salida.info("% = cualquier texto, _ = un carácter. El comodín va dentro del parámetro: ps.setString(1, \"Ben%\").");
		Salida.mostrar(dao.buscarPorApellido("Ben%"), 5);

		Salida.paso("LIKE: apellidos que contienen \"ez\"  ('%ez%')");
		Salida.mostrar(dao.buscarPorApellido("%ez%"), 5);

		LocalDate desde = LocalDate.of(2020, 1, 1);
		LocalDate hasta = LocalDate.now();
		Salida.paso("BETWEEN: ingresaron entre " + desde + " y " + hasta);
		Salida.info("BETWEEN ? AND ? incluye ambos extremos. Las fechas se pasan con ps.setObject(i, LocalDate).");
		Salida.mostrar(dao.buscarPorRangoIngreso(desde, hasta), 5);

		int tamanio = 3;
		Salida.info("Paginación: LIMIT = cuántas filas, OFFSET = cuántas saltear ((pagina - 1) * tamaño).",
				"Siempre con ORDER BY, para que las páginas sean estables.");
		for (int pagina = 1; pagina <= 2; pagina++) {
			Salida.paso("Paginación: página " + pagina + " de a " + tamanio
					+ "  (LIMIT " + tamanio + " OFFSET " + (pagina - 1) * tamanio + ")");
			List<Funcionario> filas = dao.listarPagina(pagina, tamanio);
			Salida.mostrar(filas, tamanio);
		}

		CuentaDao cuentas = new CuentaDao();
		Salida.paso("JOIN: estado de cuenta de un funcionario (funcionarios + cuenta_funcionario + movimiento)");
		Salida.info("JOIN une filas de varias tablas por su clave: movimiento.funcionario_id = funcionarios.id.",
				"Se muestran datos del funcionario, su saldo actual y todos sus movimientos.");
		var ana = dao.buscarPorLegajo(9001);
		if (ana.isEmpty()) {
			System.out.println("  (todavía no hay cuentas: ejecute primero la opción 2 - Transacciones)");
		} else {
			long id = ana.get().id();
			System.out.println("  Funcionario: " + ana.get().nombre() + " " + ana.get().apellido() + " (ID " + id + ")");
			System.out.println("  Saldo actual: " + cuentas.obtenerSaldo(id).map(Salida::gs).orElse("sin cuenta"));
			var movimientos = cuentas.ultimosMovimientos(id, 1000);
			System.out.println("  Movimientos (" + movimientos.size() + ", del más reciente al más antiguo; se muestran hasta 10):");
			movimientos.stream().limit(10).forEach(m -> System.out.printf("    #%-5d %s  %-7s %13s  %s%n",
					m.id(), m.fechaHora().withNano(0), m.tipo(), Salida.gs(m.monto()), m.descripcion()));
		}

		Salida.paso("JOIN + GROUP BY: saldo y movimientos por funcionario (funcionarios, cuenta_funcionario, movimiento)");
		Salida.info("JOIN con cuenta_funcionario + LEFT JOIN con movimiento (para incluir cuentas sin movimientos).",
				"GROUP BY agrupa por funcionario; COUNT y SUM(CASE ...) calculan cantidad, créditos y débitos.");
		List<ResumenCuenta> resumen = cuentas.resumenCuentas();
		if (resumen.isEmpty()) {
			System.out.println("  (todavía no hay cuentas: ejecute primero la opción 2 - Transacciones)");
		}
		for (ResumenCuenta r : resumen) {
			System.out.printf("  ID %-5d %-24s saldo %15s | %3d movimientos | créditos %15s | débitos %15s%n",
					r.funcionarioId(), r.funcionario(), Salida.gs(r.saldo()), r.cantidadMovimientos(),
					Salida.gs(r.totalCreditos()), Salida.gs(r.totalDebitos()));
		}
	}
}
