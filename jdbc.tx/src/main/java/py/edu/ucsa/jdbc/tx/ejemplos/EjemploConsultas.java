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
		FuncionarioDao dao = new FuncionarioDao();

		Salida.paso("COUNT y AVG (funciones de agregación)");
		System.out.println("  SELECT COUNT(*) FROM funcionarios  -> " + dao.contar());
		System.out.println("  SELECT AVG(edad) FROM funcionarios -> "
				+ dao.promedioEdad().map(p -> p + " años").orElse("NULL (no hay edades cargadas)"));

		Salida.paso("LIKE: apellidos que empiezan con \"Ben\"  (WHERE apellido LIKE ?  con 'Ben%')");
		Salida.mostrar(dao.buscarPorApellido("Ben%"), 5);

		Salida.paso("LIKE: apellidos que contienen \"ez\"  ('%ez%')");
		Salida.mostrar(dao.buscarPorApellido("%ez%"), 5);

		LocalDate desde = LocalDate.of(2020, 1, 1);
		LocalDate hasta = LocalDate.now();
		Salida.paso("BETWEEN: ingresaron entre " + desde + " y " + hasta);
		Salida.mostrar(dao.buscarPorRangoIngreso(desde, hasta), 5);

		int tamanio = 3;
		for (int pagina = 1; pagina <= 2; pagina++) {
			Salida.paso("Paginación: página " + pagina + " de a " + tamanio
					+ "  (LIMIT " + tamanio + " OFFSET " + (pagina - 1) * tamanio + ")");
			List<Funcionario> filas = dao.listarPagina(pagina, tamanio);
			Salida.mostrar(filas, tamanio);
		}

		Salida.paso("JOIN + GROUP BY: saldo y movimientos por funcionario (funcionarios, cuenta_funcionario, movimiento)");
		List<ResumenCuenta> resumen = new CuentaDao().resumenCuentas();
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
