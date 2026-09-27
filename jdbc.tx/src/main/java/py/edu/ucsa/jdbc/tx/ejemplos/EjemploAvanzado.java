package py.edu.ucsa.jdbc.tx.ejemplos;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

import py.edu.ucsa.jdbc.tx.Database;
import py.edu.ucsa.jdbc.tx.dao.CuentaDao;
import py.edu.ucsa.jdbc.tx.dao.FuncionarioDao;
import py.edu.ucsa.jdbc.tx.model.Funcionario;

/**
 * 6. Temas avanzados: savepoints, niveles de aislamiento, ResultSet desplazable
 * y CallableStatement.
 * <p>
 * Cada método imprime en el {@link PrintStream} recibido y devuelve un resultado
 * que usa {@code VerificarEjemplos} para comprobarlo.
 */
public final class EjemploAvanzado {

	public record ResultadoSavepoint(boolean primeroExiste, boolean segundoExiste) {
	}

	public record ResultadoAislamiento(BigDecimal inicial, BigDecimal leidoSinCommit, BigDecimal leidoConCommit,
			BigDecimal repeatablePrimeraLectura, BigDecimal repeatableSegundaLectura, BigDecimal repeatableDespues) {
	}

	public record ResultadoDesplazable(int filas, long idUltima, long idTercera, long idAnteriorALaTercera,
			long idPrimera, long idTerceraEsperado, long idSegundaEsperado) {
	}

	public record ResultadoCallable(long porFuncion, long porConsulta) {
	}

	private static final int LEGAJO_AISLAMIENTO = 9003;

	private EjemploAvanzado() {
	}

	static void ejecutar() throws Exception {
		Salida.titulo("6. TEMAS AVANZADOS: savepoints, aislamiento, ResultSet desplazable, CallableStatement");
		savepoint(System.out);
		aislamiento(System.out);
		desplazable(System.out);
		callable(System.out);
	}

	// -------------------------------------------------------------------------
	// Savepoints
	// -------------------------------------------------------------------------
	public static ResultadoSavepoint savepoint(PrintStream out) throws SQLException {
		paso(out, "SAVEPOINT: deshacer sólo una parte de la transacción");
		info(out, "conn.setSavepoint() marca un punto dentro de la transacción.",
				"conn.rollback(savepoint) deshace sólo lo hecho después de esa marca;",
				"lo anterior sigue pendiente y se confirma con commit().");

		String sql = "INSERT INTO funcionarios (nombre, apellido, fecha_nacimiento, fecha_ingreso, fecha_ult_modif, legajo) "
				+ "VALUES (?, 'Savepoint', ?, ?, ?, ?)";
		long idPrimero;
		long idSegundo;
		try (Connection conn = Database.getDataSource().getConnection()) {
			conn.setAutoCommit(false);
			try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
				idPrimero = insertar(ps, "Primero", 9101);
				out.println("  1) INSERT 'Primero'  -> ID " + idPrimero);

				Savepoint marca = conn.setSavepoint("despues_del_primero");
				out.println("  2) setSavepoint(\"despues_del_primero\")");

				idSegundo = insertar(ps, "Segundo", 9102);
				out.println("  3) INSERT 'Segundo'  -> ID " + idSegundo);

				conn.rollback(marca);
				out.println("  4) rollback(savepoint) -> se deshace sólo el INSERT de 'Segundo'");

				conn.commit();
				out.println("  5) commit()            -> se confirma 'Primero'");
			} catch (SQLException e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		}

		FuncionarioDao dao = new FuncionarioDao();
		boolean primeroExiste = dao.buscarPorId(idPrimero).isPresent();
		boolean segundoExiste = dao.buscarPorId(idSegundo).isPresent();
		out.println("  Resultado: 'Primero' existe = " + siNo(primeroExiste) + ", 'Segundo' existe = " + siNo(segundoExiste));
		dao.eliminar(idPrimero);
		out.println("  (se borra 'Primero' para dejar la tabla como estaba)");
		return new ResultadoSavepoint(primeroExiste, segundoExiste);
	}

	private static long insertar(PreparedStatement ps, String nombre, int legajo) throws SQLException {
		ps.setString(1, nombre);
		ps.setObject(2, LocalDate.of(2000, 1, 1));
		ps.setObject(3, LocalDate.now());
		ps.setObject(4, LocalDateTime.now());
		ps.setInt(5, legajo);
		ps.executeUpdate();
		try (ResultSet rs = ps.getGeneratedKeys()) {
			rs.next();
			return rs.getLong("id");
		}
	}

	// -------------------------------------------------------------------------
	// Niveles de aislamiento con dos conexiones
	// -------------------------------------------------------------------------
	public static ResultadoAislamiento aislamiento(PrintStream out) throws SQLException {
		paso(out, "NIVELES DE AISLAMIENTO: dos conexiones al mismo tiempo");
		info(out, "conn.setTransactionIsolation(...) define qué ve una transacción de los cambios de otras.",
				"READ_COMMITTED (por defecto en PostgreSQL): sólo ve lo confirmado (nunca 'lecturas sucias').",
				"REPEATABLE_READ: ve siempre la misma foto de los datos durante toda su transacción.");

		FuncionarioDao funcionarios = new FuncionarioDao();
		CuentaDao cuentas = new CuentaDao();
		Funcionario f = funcionarios.buscarPorLegajo(LEGAJO_AISLAMIENTO).orElse(null);
		long id = f != null ? f.id()
				: funcionarios.insertar(new Funcionario(null, "Iris", "Aislamiento", 28, LocalDate.of(1998, 5, 5),
						LocalDate.of(2023, 3, 1), null, LocalDateTime.now(), LEGAJO_AISLAMIENTO));
		BigDecimal inicial = new BigDecimal("100000.00");
		cuentas.fijarSaldo(id, inicial);
		out.println("  Cuenta de prueba (funcionario ID " + id + ") con saldo " + Salida.gs(inicial));

		String leer = "SELECT saldo FROM cuenta_funcionario WHERE funcionario_id = ?";
		String sumar = "UPDATE cuenta_funcionario SET saldo = saldo + ? WHERE funcionario_id = ?";
		BigDecimal sinCommit;
		BigDecimal conCommit;
		BigDecimal rr1;
		BigDecimal rr2;
		BigDecimal rrDespues;

		try (Connection a = Database.getDataSource().getConnection();
				Connection b = Database.getDataSource().getConnection()) {

			out.println();
			out.println("  a) READ_COMMITTED");
			b.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			a.setAutoCommit(false);
			ejecutarUpdate(a, sumar, new BigDecimal("50000"), id);
			out.println("     Conexión A: UPDATE saldo + 50.000 (todavía SIN commit)");
			sinCommit = leerSaldo(b, leer, id);
			out.println("     Conexión B lee: " + Salida.gs(sinCommit) + "  <- no ve el cambio sin confirmar");
			a.commit();
			out.println("     Conexión A: commit()");
			conCommit = leerSaldo(b, leer, id);
			out.println("     Conexión B lee: " + Salida.gs(conCommit) + "  <- ahora sí lo ve");

			out.println();
			out.println("  b) REPEATABLE_READ");
			b.setAutoCommit(false);
			b.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			rr1 = leerSaldo(b, leer, id);
			out.println("     Conexión B (en su transacción) lee: " + Salida.gs(rr1));
			ejecutarUpdate(a, sumar, new BigDecimal("25000"), id);
			a.commit();
			out.println("     Conexión A: UPDATE saldo + 25.000 y commit()");
			rr2 = leerSaldo(b, leer, id);
			out.println("     Conexión B lee otra vez: " + Salida.gs(rr2) + "  <- sigue viendo su foto inicial");
			b.commit();
			b.setAutoCommit(true);
			b.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			rrDespues = leerSaldo(b, leer, id);
			out.println("     Conexión B, después de su commit(): " + Salida.gs(rrDespues) + "  <- ve el valor nuevo");
			a.setAutoCommit(true);
		}
		return new ResultadoAislamiento(inicial, sinCommit, conCommit, rr1, rr2, rrDespues);
	}

	private static void ejecutarUpdate(Connection conn, String sql, BigDecimal monto, long id) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setBigDecimal(1, monto);
			ps.setLong(2, id);
			ps.executeUpdate();
		}
	}

	private static BigDecimal leerSaldo(Connection conn, String sql, long id) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getBigDecimal(1);
			}
		}
	}

	// -------------------------------------------------------------------------
	// ResultSet desplazable
	// -------------------------------------------------------------------------
	public static ResultadoDesplazable desplazable(PrintStream out) throws SQLException {
		paso(out, "RESULTSET DESPLAZABLE: moverse hacia adelante y hacia atrás");
		info(out, "Por defecto un ResultSet sólo avanza con next() (TYPE_FORWARD_ONLY).",
				"Con createStatement(TYPE_SCROLL_INSENSITIVE, CONCUR_READ_ONLY) se puede usar",
				"last(), first(), absolute(n), previous() y relative(n).");

		String sql = "SELECT id, nombre, apellido FROM funcionarios ORDER BY id LIMIT 10";
		try (Connection conn = Database.getDataSource().getConnection();
				Statement st = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
				ResultSet rs = st.executeQuery(sql)) {

			// valores esperados leyendo hacia adelante
			java.util.List<Long> ids = new java.util.ArrayList<>();
			while (rs.next()) {
				ids.add(rs.getLong("id"));
			}
			if (ids.size() < 3) {
				out.println("  (se necesitan al menos 3 funcionarios; ejecute primero la opción 1)");
				return new ResultadoDesplazable(ids.size(), 0, 0, 0, 0, 0, 0);
			}

			rs.last();
			int filas = rs.getRow();
			long ultima = rs.getLong("id");
			out.println("  last()      -> fila " + filas + ", " + fila(rs) + "   (getRow() = cantidad de filas)");

			rs.absolute(3);
			long tercera = rs.getLong("id");
			out.println("  absolute(3) -> fila " + rs.getRow() + ", " + fila(rs));

			rs.previous();
			long anterior = rs.getLong("id");
			out.println("  previous()  -> fila " + rs.getRow() + ", " + fila(rs));

			rs.first();
			long primera = rs.getLong("id");
			out.println("  first()     -> fila " + rs.getRow() + ", " + fila(rs));

			rs.relative(2);
			out.println("  relative(2) -> fila " + rs.getRow() + ", " + fila(rs));

			return new ResultadoDesplazable(filas, ultima, tercera, anterior, primera, ids.get(2), ids.get(1));
		}
	}

	private static String fila(ResultSet rs) throws SQLException {
		return "ID " + rs.getLong("id") + " " + rs.getString("nombre") + " " + rs.getString("apellido");
	}

	// -------------------------------------------------------------------------
	// CallableStatement
	// -------------------------------------------------------------------------
	public static ResultadoCallable callable(PrintStream out) throws SQLException {
		paso(out, "CALLABLESTATEMENT: llamar a una función de PostgreSQL");
		info(out, "Se crea (o reemplaza) la función fn_contar_funcionarios_por_edad(desde, hasta).",
				"conn.prepareCall(\"{? = call fn(?, ?)}\") la invoca; registerOutParameter(1, ...)",
				"indica el tipo del valor que devuelve y getLong(1) lo lee.");

		int desde = 25;
		int hasta = 40;
		long porFuncion;
		long porConsulta;
		try (Connection conn = Database.getDataSource().getConnection()) {
			try (Statement st = conn.createStatement()) {
				st.execute("""
						CREATE OR REPLACE FUNCTION fn_contar_funcionarios_por_edad(desde INTEGER, hasta INTEGER)
						RETURNS BIGINT
						LANGUAGE SQL
						AS $$
						    SELECT COUNT(*) FROM funcionarios WHERE edad BETWEEN desde AND hasta
						$$
						""");
			}
			out.println("  Función creada: fn_contar_funcionarios_por_edad(desde INTEGER, hasta INTEGER) RETURNS BIGINT");

			try (CallableStatement cs = conn.prepareCall("{? = call fn_contar_funcionarios_por_edad(?, ?)}")) {
				cs.registerOutParameter(1, Types.BIGINT);
				cs.setInt(2, desde);
				cs.setInt(3, hasta);
				cs.execute();
				porFuncion = cs.getLong(1);
			}
			out.println("  {? = call fn_contar_funcionarios_por_edad(" + desde + ", " + hasta + ")} -> " + porFuncion);

			try (PreparedStatement ps = conn.prepareStatement(
					"SELECT COUNT(*) FROM funcionarios WHERE edad BETWEEN ? AND ?")) {
				ps.setInt(1, desde);
				ps.setInt(2, hasta);
				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					porConsulta = rs.getLong(1);
				}
			}
			out.println("  Comprobación con un SELECT COUNT(*) normal       -> " + porConsulta);
		}
		return new ResultadoCallable(porFuncion, porConsulta);
	}

	private static void paso(PrintStream out, String texto) {
		out.println();
		out.println("--- " + texto + " ---");
	}

	private static void info(PrintStream out, String... lineas) {
		for (String linea : lineas) {
			out.println("  [i] " + linea);
		}
	}

	private static String siNo(boolean valor) {
		return valor ? "SI" : "NO";
	}
}
