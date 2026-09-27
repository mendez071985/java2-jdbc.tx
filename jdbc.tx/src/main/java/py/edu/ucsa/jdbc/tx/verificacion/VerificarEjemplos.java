package py.edu.ucsa.jdbc.tx.verificacion;

import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import py.edu.ucsa.jdbc.tx.Database;
import py.edu.ucsa.jdbc.tx.dao.CuentaDao;
import py.edu.ucsa.jdbc.tx.dao.FuncionarioDao;
import py.edu.ucsa.jdbc.tx.ejemplos.EjemploAvanzado;
import py.edu.ucsa.jdbc.tx.ejemplos.PreparacionBase;
import py.edu.ucsa.jdbc.tx.model.Funcionario;
import py.edu.ucsa.jdbc.tx.model.ResumenCuenta;
import py.edu.ucsa.jdbc.tx.services.TransferenciaService;

/**
 * Verifica automáticamente, contra la base real, que cada ejemplo del menú
 * (CRUD, transacciones, consultas, batch, inyección SQL y temas avanzados)
 * funciona como se explica. Imprime [ OK ] / [FALLA] con la explicación.
 * <p>
 * Los registros que crea para verificar se borran al final; sólo quedan los
 * funcionarios y cuentas de ejemplo de las transferencias (legajos 9001 a 9003).
 * Ejecutar: en STS, clic derecho → Run As → Java Application.
 */
public final class VerificarEjemplos {

	private static final String APELLIDO_PRUEBA = "ZzVerificacion";

	private final FuncionarioDao dao = new FuncionarioDao();
	private final CuentaDao cuentas = new CuentaDao();
	private final TransferenciaService service = new TransferenciaService();
	private final PrintStream silencio = new PrintStream(OutputStream.nullOutputStream());
	private final List<Long> creados = new ArrayList<>();
	private int ok;
	private int fallas;

	public static void main(String[] args) throws Exception {
		boolean todoOk;
		try {
			PreparacionBase.asegurarTablas(Database.getDataSource());
			todoOk = new VerificarEjemplos().verificar();
		} finally {
			Database.cerrarPool();
		}
		System.exit(todoOk ? 0 : 1);
	}

	public boolean verificar() throws Exception {
		linea();
		System.out.println(" VERIFICACIÓN DE LOS EJEMPLOS JDBC (menú de ejemplos.Main)");
		linea();
		try {
			seccion(() -> crud(), "1. CRUD");
			seccion(() -> transacciones(), "2. TRANSACCIONES");
			seccion(() -> consultas(), "3. CONSULTAS");
			seccion(() -> batch(), "4. BATCH");
			seccion(() -> inyeccion(), "5. INYECCIÓN SQL");
			seccion(() -> avanzado(), "6. AVANZADO");
		} finally {
			if (!creados.isEmpty()) {
				dao.eliminarVarios(creados);
			}
		}
		System.out.println();
		linea();
		System.out.printf(" RESULTADO: %d OK, %d FALLAS -> %s%n", ok, fallas,
				fallas == 0 ? "TODOS LOS EJEMPLOS FUNCIONAN" : "HAY EJEMPLOS QUE FALLAN");
		linea();
		return fallas == 0;
	}

	// -------------------------------------------------------------------------
	private void crud() throws Exception {
		Funcionario original = new Funcionario(null, "Verif", APELLIDO_PRUEBA, null, LocalDate.of(1990, 5, 20),
				LocalDate.of(2021, 6, 1), new byte[] { 9, 8, 7 }, LocalDateTime.now().withNano(0), 7001);
		long id = dao.insertar(original);
		creados.add(id);
		check("INSERT devuelve el ID generado (getGeneratedKeys)", id > 0, "ID = " + id);

		Funcionario leido = dao.buscarPorId(id).orElse(null);
		check("SELECT por ID trae lo mismo que se insertó", leido != null
				&& leido.nombre().equals("Verif") && leido.edad() == null
				&& Arrays.equals(leido.foto(), original.foto())
				&& leido.fechaNacimiento().equals(original.fechaNacimiento())
				&& Objects.equals(leido.legajo(), 7001),
				"nombre, edad NULL (no 0), foto de 3 bytes, fecha de nacimiento y legajo coinciden");

		boolean actualizado = dao.actualizar(new Funcionario(id, "Verif", APELLIDO_PRUEBA, 33,
				leido.fechaNacimiento(), leido.fechaIngreso(), null, LocalDateTime.now(), 7002));
		Funcionario despues = dao.buscarPorId(id).orElseThrow();
		check("UPDATE cambia los datos y devuelve true", actualizado
				&& Objects.equals(despues.edad(), 33) && despues.foto() == null && Objects.equals(despues.legajo(), 7002),
				"edad NULL -> 33, foto -> NULL, legajo 7001 -> 7002");

		check("UPDATE de un ID inexistente devuelve false (0 filas)", !dao.actualizar(new Funcionario(-1L, "x", "x",
				null, LocalDate.now(), LocalDate.now(), null, LocalDateTime.now(), 0)), "executeUpdate() == 0");

		boolean borrado = dao.eliminar(id);
		creados.remove(Long.valueOf(id));
		check("DELETE borra el registro y luego ya no se encuentra", borrado && dao.buscarPorId(id).isEmpty()
				&& !dao.eliminar(id), "eliminar -> true; buscarPorId -> vacío; eliminar otra vez -> false");
	}

	// -------------------------------------------------------------------------
	private void transacciones() throws Exception {
		long ana = obtenerOCrear("Ana", "Transferencia", 9001);
		long carlos = obtenerOCrear("Carlos", "Transferencia", 9002);
		cuentas.fijarSaldo(ana, new BigDecimal("1000000"));
		cuentas.fijarSaldo(carlos, new BigDecimal("500000"));
		BigDecimal total = new BigDecimal("1500000");

		long movAna = cuentas.contarMovimientos(ana);
		long movCarlos = cuentas.contarMovimientos(carlos);
		service.transferir(ana, carlos, new BigDecimal("200000"));
		check("COMMIT: la transferencia OK cambia ambos saldos", igual(saldo(ana), "800000") && igual(saldo(carlos), "700000"),
				"Ana 1.000.000 -> 800.000, Carlos 500.000 -> 700.000");
		check("COMMIT: se registran los 2 movimientos (DEBITO y CREDITO)",
				cuentas.contarMovimientos(ana) == movAna + 1 && cuentas.contarMovimientos(carlos) == movCarlos + 1
						&& "DEBITO".equals(cuentas.ultimosMovimientos(ana, 1).get(0).tipo())
						&& "CREDITO".equals(cuentas.ultimosMovimientos(carlos, 1).get(0).tipo()),
				"+1 movimiento para cada uno, con el tipo correcto");

		movAna = cuentas.contarMovimientos(ana);
		String error = falla(() -> service.transferir(ana, carlos, new BigDecimal("5000000")));
		check("ROLLBACK por saldo insuficiente: nada cambia", error != null && igual(saldo(ana), "800000")
				&& igual(saldo(carlos), "700000") && cuentas.contarMovimientos(ana) == movAna,
				"excepción: " + error + "; saldos y movimientos iguales");

		error = falla(() -> service.transferir(ana, -1, new BigDecimal("100000")));
		check("ROLLBACK a mitad de camino: se deshace el débito ya hecho", error != null && igual(saldo(ana), "800000")
				&& cuentas.contarMovimientos(ana) == movAna,
				"excepción: " + error + "; el saldo de Ana sigue en 800.000");

		boolean rechazado = false;
		try {
			service.transferir(ana, carlos, new BigDecimal("-1"));
		} catch (IllegalArgumentException e) {
			rechazado = true;
		}
		check("Monto negativo: se rechaza antes de abrir la transacción", rechazado, "IllegalArgumentException");
		check("El dinero total no cambia (consistencia)", saldo(ana).add(saldo(carlos)).compareTo(total) == 0,
				"800.000 + 700.000 = 1.500.000");
	}

	// -------------------------------------------------------------------------
	private void consultas() throws Exception {
		List<Funcionario> todos = dao.listarTodos();
		check("COUNT(*) coincide con la cantidad de filas", dao.contar() == todos.size(), "COUNT = " + todos.size());

		List<Integer> edades = todos.stream().map(Funcionario::edad).filter(Objects::nonNull).toList();
		Optional<BigDecimal> esperado = edades.isEmpty() ? Optional.empty()
				: Optional.of(BigDecimal.valueOf(edades.stream().mapToInt(Integer::intValue).sum())
						.divide(BigDecimal.valueOf(edades.size()), 2, RoundingMode.HALF_UP));
		Optional<BigDecimal> avg = dao.promedioEdad();
		check("AVG(edad) coincide con el promedio calculado en Java (ignora NULL)",
				esperado.map(e -> avg.isPresent() && avg.get().compareTo(e) == 0).orElse(avg.isEmpty()),
				"AVG = " + avg.map(BigDecimal::toPlainString).orElse("NULL"));

		// datos propios para LIKE y BETWEEN
		List<Funcionario> propios = List.of(
				nuevo("Uno", APELLIDO_PRUEBA + "Alfa", LocalDate.of(2019, 12, 31)),
				nuevo("Dos", APELLIDO_PRUEBA + "Alfa", LocalDate.of(2020, 6, 15)),
				nuevo("Tres", APELLIDO_PRUEBA + "Beta", LocalDate.of(2021, 1, 1)));
		creados.addAll(dao.insertarVarios(propios));

		List<Funcionario> like = dao.buscarPorApellido(APELLIDO_PRUEBA + "Al%");
		check("LIKE 'prefijo%' trae sólo los que empiezan así", like.size() == 2
				&& like.stream().allMatch(f -> f.apellido().startsWith(APELLIDO_PRUEBA + "Al")),
				"2 de 3 registros de prueba");

		List<Funcionario> rango = dao.buscarPorRangoIngreso(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)).stream()
				.filter(f -> f.apellido() != null && f.apellido().startsWith(APELLIDO_PRUEBA)).toList();
		check("BETWEEN de fechas incluye los extremos", rango.size() == 2
				&& rango.stream().noneMatch(f -> f.fechaIngreso().equals(LocalDate.of(2019, 12, 31))),
				"2020-06-15 y 2021-01-01 entran; 2019-12-31 no");

		todos = dao.listarTodos();
		List<Funcionario> pagina1 = dao.listarPagina(1, 3);
		List<Funcionario> pagina2 = dao.listarPagina(2, 3);
		check("Paginación LIMIT/OFFSET: páginas 1 y 2 = primeras 6 filas en orden",
				ids(pagina1).equals(ids(todos.subList(0, Math.min(3, todos.size()))))
						&& ids(pagina2).equals(ids(todos.subList(Math.min(3, todos.size()), Math.min(6, todos.size())))),
				"página 1 = " + ids(pagina1) + ", página 2 = " + ids(pagina2));

		long ana = obtenerOCrear("Ana", "Transferencia", 9001);
		ResumenCuenta resumen = cuentas.resumenCuentas().stream().filter(r -> r.funcionarioId() == ana)
				.findFirst().orElse(null);
		check("JOIN + GROUP BY: saldo y cantidad de movimientos correctos", resumen != null
				&& resumen.saldo().compareTo(saldo(ana)) == 0
				&& resumen.cantidadMovimientos() == cuentas.contarMovimientos(ana),
				resumen == null ? "sin resumen" : "saldo " + resumen.saldo().toPlainString() + ", "
						+ resumen.cantidadMovimientos() + " movimientos");
		check("JOIN estado de cuenta: todos los movimientos son del funcionario pedido",
				cuentas.ultimosMovimientos(ana, 1000).stream().allMatch(m -> m.funcionarioId() == ana
						&& m.funcionario().startsWith("Ana")), "movimiento.funcionario_id = funcionarios.id");
	}

	// -------------------------------------------------------------------------
	private void batch() throws Exception {
		long antes = dao.contar();
		int n = 300;
		List<Funcionario> lista = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			lista.add(nuevo("Batch" + i, APELLIDO_PRUEBA, LocalDate.of(2025, 1, 1)));
		}
		List<Long> ids = dao.insertarVarios(lista);
		check("insertarVarios (addBatch/executeBatch) inserta todo y devuelve los IDs",
				ids.size() == n && ids.stream().distinct().count() == n && dao.contar() == antes + n,
				n + " IDs distintos; COUNT subió en " + n);
		check("Los registros del batch se pueden leer", dao.buscarPorId(ids.get(n - 1))
				.map(f -> f.nombre().equals("Batch" + (n - 1))).orElse(false), "último ID -> Batch" + (n - 1));
		int borrados = dao.eliminarVarios(ids);
		check("eliminarVarios (DELETE en batch) borra todo", borrados == n && dao.contar() == antes,
				borrados + " filas borradas; COUNT volvió a " + antes);
	}

	// -------------------------------------------------------------------------
	private void inyeccion() throws Exception {
		long total = dao.contar();
		String ataque = "x' OR '1'='1";
		check("Statement concatenado + \"" + ataque + "\" devuelve TODA la tabla (vulnerable)",
				dao.buscarPorApellidoInseguro(ataque).size() == total, total + " de " + total + " registros");
		check("PreparedStatement con la misma entrada no devuelve nada (seguro)",
				dao.buscarPorApellidoSeguro(ataque).isEmpty(), "0 registros");
		check("Statement concatenado con \"O'Higgins\" da error de SQL",
				falla(() -> dao.buscarPorApellidoInseguro("O'Higgins")) != null, "el apóstrofo rompe el SQL");
		check("PreparedStatement con \"O'Higgins\" funciona sin error",
				falla(() -> dao.buscarPorApellidoSeguro("O'Higgins")) == null, "el apóstrofo se trata como dato");
	}

	// -------------------------------------------------------------------------
	private void avanzado() throws Exception {
		EjemploAvanzado.ResultadoSavepoint sp = EjemploAvanzado.savepoint(silencio);
		check("SAVEPOINT: rollback(savepoint) deshace sólo lo posterior", sp.primeroExiste() && !sp.segundoExiste(),
				"'Primero' confirmado, 'Segundo' deshecho");

		EjemploAvanzado.ResultadoAislamiento ai = EjemploAvanzado.aislamiento(silencio);
		check("READ_COMMITTED: no se ven cambios sin confirmar", ai.leidoSinCommit().compareTo(ai.inicial()) == 0,
				"leído " + ai.leidoSinCommit().toPlainString() + " (sin el +50.000 pendiente)");
		check("READ_COMMITTED: después del commit sí se ven",
				ai.leidoConCommit().compareTo(ai.inicial().add(new BigDecimal("50000"))) == 0,
				"leído " + ai.leidoConCommit().toPlainString());
		check("REPEATABLE_READ: la transacción mantiene su foto de los datos",
				ai.repeatablePrimeraLectura().compareTo(ai.repeatableSegundaLectura()) == 0
						&& ai.repeatableDespues().compareTo(ai.repeatablePrimeraLectura().add(new BigDecimal("25000"))) == 0,
				"2 lecturas iguales (" + ai.repeatableSegundaLectura().toPlainString() + ") y después del commit "
						+ ai.repeatableDespues().toPlainString());

		EjemploAvanzado.ResultadoDesplazable rs = EjemploAvanzado.desplazable(silencio);
		check("ResultSet desplazable: last(), absolute(3), previous() y first()", rs.filas() >= 3
				&& rs.idTercera() == rs.idTerceraEsperado() && rs.idAnteriorALaTercera() == rs.idSegundaEsperado()
				&& rs.idPrimera() < rs.idUltima(), rs.filas() + " filas; absolute(3) = ID " + rs.idTercera()
						+ "; previous() = ID " + rs.idAnteriorALaTercera());

		EjemploAvanzado.ResultadoCallable cs = EjemploAvanzado.callable(silencio);
		check("CallableStatement: la función devuelve lo mismo que un SELECT", cs.porFuncion() == cs.porConsulta(),
				"fn_contar_funcionarios_por_edad(25, 40) = " + cs.porFuncion());
	}

	// -------------------------------------------------------------------------
	@FunctionalInterface
	private interface Paso {
		void ejecutar() throws Exception;
	}

	private void seccion(Paso paso, String nombre) {
		System.out.println();
		System.out.println("--- " + nombre + " ---");
		try {
			paso.ejecutar();
		} catch (Exception e) {
			check("La sección se ejecutó sin errores inesperados", false, e.toString());
		}
	}

	/** Ejecuta y devuelve el mensaje de la excepción, o null si no falló. */
	private static String falla(Paso paso) {
		try {
			paso.ejecutar();
			return null;
		} catch (Exception e) {
			String mensaje = String.valueOf(e.getMessage()).lines().findFirst().orElse("");
			int corte = mensaje.indexOf(" in SQL");
			return corte > 0 ? mensaje.substring(0, corte) : mensaje;
		}
	}

	private void check(String descripcion, boolean cumple, String detalle) {
		if (cumple) {
			ok++;
		} else {
			fallas++;
		}
		System.out.printf("[%s] %s%n        -> %s%n", cumple ? " OK  " : "FALLA", descripcion, detalle);
	}

	private long obtenerOCrear(String nombre, String apellido, int legajo) throws SQLException {
		Optional<Funcionario> f = dao.buscarPorLegajo(legajo);
		if (f.isPresent()) {
			return f.get().id();
		}
		return dao.insertar(new Funcionario(null, nombre, apellido, 30, LocalDate.of(1995, 1, 1),
				LocalDate.of(2024, 1, 1), null, LocalDateTime.now(), legajo));
	}

	private BigDecimal saldo(long funcionarioId) throws SQLException {
		return cuentas.obtenerSaldo(funcionarioId).orElseThrow();
	}

	private static boolean igual(BigDecimal valor, String esperado) {
		return valor.compareTo(new BigDecimal(esperado)) == 0;
	}

	private static Funcionario nuevo(String nombre, String apellido, LocalDate ingreso) {
		return new Funcionario(null, nombre, apellido, 30, LocalDate.of(1995, 1, 1), ingreso, null,
				LocalDateTime.now(), 7100);
	}

	private static List<Long> ids(List<Funcionario> funcionarios) {
		return funcionarios.stream().map(Funcionario::id).toList();
	}

	private static void linea() {
		System.out.println("=".repeat(70));
	}
}
