package py.edu.ucsa.jdbc.tx.ejemplos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import py.edu.ucsa.jdbc.tx.dao.CuentaDao;
import py.edu.ucsa.jdbc.tx.dao.FuncionarioDao;
import py.edu.ucsa.jdbc.tx.model.Funcionario;
import py.edu.ucsa.jdbc.tx.model.Movimiento;
import py.edu.ucsa.jdbc.tx.services.TransferenciaService;

/**
 * 2. Transacciones con TransferenciaService: commit cuando todo sale bien y
 * rollback cuando algo falla (nada queda a medias).
 */
final class EjemploTransaccion {

	private static final int LEGAJO_ANA = 9001;
	private static final int LEGAJO_CARLOS = 9002;
	private static final BigDecimal SALDO_INICIAL_ANA = new BigDecimal("1000000");
	private static final BigDecimal SALDO_INICIAL_CARLOS = new BigDecimal("500000");

	private EjemploTransaccion() {
	}

	static void ejecutar() throws Exception {
		Salida.titulo("2. TRANSACCIONES: commit y rollback (TransferenciaService)");
		Salida.info("Una transferencia son 4 operaciones: débito, crédito y 2 movimientos. Deben hacerse TODAS o NINGUNA.",
				"TransferenciaService: conn.setAutoCommit(false) abre la transacción; si todo sale bien conn.commit(),",
				"si algo falla conn.rollback() deshace lo que se había hecho. SELECT ... FOR UPDATE bloquea la cuenta",
				"de origen para que dos transferencias simultáneas no usen el mismo saldo.");
		FuncionarioDao funcionarios = new FuncionarioDao();
		CuentaDao cuentas = new CuentaDao();
		TransferenciaService service = new TransferenciaService();

		Funcionario ana = obtenerOCrear(funcionarios, "Ana", "Transferencia", LEGAJO_ANA);
		Funcionario carlos = obtenerOCrear(funcionarios, "Carlos", "Transferencia", LEGAJO_CARLOS);

		Salida.paso("Preparación: se fijan los saldos iniciales (para que el ejemplo se pueda repetir)");
		cuentas.fijarSaldo(ana.id(), SALDO_INICIAL_ANA);
		cuentas.fijarSaldo(carlos.id(), SALDO_INICIAL_CARLOS);
		mostrarSaldos(cuentas, ana, carlos);

		// Caso 1: todo sale bien -> COMMIT
		Salida.paso("CASO 1: Ana transfiere 200.000 Gs. a Carlos -> todo sale bien -> COMMIT");
		Salida.info("Se ejecutan las 4 operaciones y commit(): los cambios quedan guardados.");
		service.transferir(ana.id(), carlos.id(), new BigDecimal("200000"));
		System.out.println("  Transferencia confirmada (commit).");
		mostrarSaldos(cuentas, ana, carlos);
		System.out.println("  Últimos movimientos (JOIN movimiento + funcionarios):");
		for (Movimiento m : cuentas.ultimosMovimientos(ana.id(), 1)) {
			mostrar(m);
		}
		for (Movimiento m : cuentas.ultimosMovimientos(carlos.id(), 1)) {
			mostrar(m);
		}

		// Caso 2: saldo insuficiente -> ROLLBACK
		Salida.paso("CASO 2: Ana intenta transferir 5.000.000 Gs. -> saldo insuficiente -> ROLLBACK");
		Salida.info("El servicio lee el saldo, ve que no alcanza y lanza una excepción: rollback().");
		intentar(cuentas, ana, carlos, () -> service.transferir(ana.id(), carlos.id(), new BigDecimal("5000000")));

		// Caso 3: falla a mitad de camino -> ROLLBACK deshace el débito ya hecho
		Salida.paso("CASO 3: Ana transfiere 100.000 Gs. a un funcionario que NO existe -> ROLLBACK");
		System.out.println("  El débito a Ana se ejecuta, pero el crédito al destino falla:");
		System.out.println("  el rollback deshace también el débito, el saldo de Ana no cambia.");
		intentar(cuentas, ana, carlos, () -> service.transferir(ana.id(), -1, new BigDecimal("100000")));

		// Caso 4: validación antes de empezar la transacción
		Salida.paso("CASO 4: monto negativo -> se rechaza antes de abrir la transacción");
		Salida.info("Validar antes de abrir la conexión evita trabajo innecesario en la base.");
		try {
			service.transferir(ana.id(), carlos.id(), new BigDecimal("-50000"));
		} catch (IllegalArgumentException e) {
			System.out.println("  Rechazado: " + e.getMessage());
		}

		Salida.paso("Resultado final");
		mostrarSaldos(cuentas, ana, carlos);
		BigDecimal total = saldo(cuentas, ana).add(saldo(cuentas, carlos));
		System.out.println("  Suma de ambos saldos: " + Salida.gs(total) + " (igual al inicio: "
				+ Salida.gs(SALDO_INICIAL_ANA.add(SALDO_INICIAL_CARLOS)) + ") -> "
				+ (total.compareTo(SALDO_INICIAL_ANA.add(SALDO_INICIAL_CARLOS)) == 0
						? "el dinero no se crea ni se pierde" : "¡INCONSISTENCIA!"));
	}

	@FunctionalInterface
	private interface Operacion {
		void ejecutar() throws Exception;
	}

	private static void intentar(CuentaDao cuentas, Funcionario ana, Funcionario carlos, Operacion operacion)
			throws Exception {
		BigDecimal saldoAna = saldo(cuentas, ana);
		BigDecimal saldoCarlos = saldo(cuentas, carlos);
		long movimientosAna = cuentas.contarMovimientos(ana.id());
		try {
			operacion.ejecutar();
			System.out.println("  (no falló)");
		} catch (Exception e) {
			System.out.println("  Falló con: " + e.getMessage() + " -> se hizo ROLLBACK");
		}
		mostrarSaldos(cuentas, ana, carlos);
		boolean sinCambios = saldo(cuentas, ana).compareTo(saldoAna) == 0
				&& saldo(cuentas, carlos).compareTo(saldoCarlos) == 0
				&& cuentas.contarMovimientos(ana.id()) == movimientosAna;
		System.out.println("  ¿Quedó todo como antes (saldos y movimientos)? " + (sinCambios ? "SI" : "NO"));
	}

	private static Funcionario obtenerOCrear(FuncionarioDao dao, String nombre, String apellido, int legajo)
			throws Exception {
		var existente = dao.buscarPorLegajo(legajo);
		if (existente.isPresent()) {
			return existente.get();
		}
		long id = dao.insertar(new Funcionario(null, nombre, apellido, 30, LocalDate.of(1995, 1, 1),
				LocalDate.of(2024, 1, 1), null, LocalDateTime.now(), legajo));
		return dao.buscarPorId(id).orElseThrow();
	}

	private static BigDecimal saldo(CuentaDao cuentas, Funcionario f) throws Exception {
		return cuentas.obtenerSaldo(f.id()).orElseThrow();
	}

	private static void mostrarSaldos(CuentaDao cuentas, Funcionario ana, Funcionario carlos) throws Exception {
		System.out.printf("  Saldo %-6s (ID %d): %15s%n", ana.nombre(), ana.id(), Salida.gs(saldo(cuentas, ana)));
		System.out.printf("  Saldo %-6s (ID %d): %15s%n", carlos.nombre(), carlos.id(), Salida.gs(saldo(cuentas, carlos)));
	}

	private static void mostrar(Movimiento m) {
		System.out.printf("    #%-5d %-22s %-7s %13s  %s%n", m.id(), m.funcionario(), m.tipo(), Salida.gs(m.monto()),
				m.descripcion());
	}
}
