package py.edu.ucsa.jdbc.tx.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import py.edu.ucsa.jdbc.tx.Database;
import py.edu.ucsa.jdbc.tx.model.Funcionario;

public class FuncionarioDao {

	private static final String COLUMNAS = """
			id, nombre, apellido, edad, fecha_nacimiento, fecha_ingreso, foto, fecha_ult_modif, legajo
			""";

	//PreparedStatement
	//NULL
	//JDBCType
	//LocalDate
	//LocalDateTime
	//byte[]
	//try-with-resources
	//PK autogenerada
	public long insertar(Funcionario f) throws SQLException {
		String sql = """
				INSERT INTO funcionarios
	            (
	                nombre,
	                apellido,
	                edad,
	                fecha_nacimiento,
	                fecha_ingreso,
	                foto,
	                fecha_ult_modif,
	                legajo
	            )
	            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""";
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
			ps.setString(1, f.nombre());
			ps.setString(2, f.apellido());
			if (f.edad() == null) {
				//ps.setObject(3, null, JDBCType.INTEGER);	
				ps.setNull(3, Types.INTEGER);
			}else {
				ps.setInt(3, f.edad());
			}
			ps.setObject(4, f.fechaNacimiento());
			//ps.setDate(4, Date.valueOf(f.fechaNacimiento()));
			ps.setObject(5, f.fechaIngreso());
			//ps.setDate(5, Date.valueOf(f.fechaIngreso()));
			
			if (f.foto() == null) {
				//ps.setObject(6, null, JDBCType.BINARY);
				ps.setNull(6, Types.BINARY);
			}else {
				ps.setBytes(6, f.foto());				
			}
			
			ps.setObject(7, f.fechaUltModif());
			//ps.setTimestamp(7, Timestamp.valueOf(f.fechaUltModif()));
			
			ps.setInt(8, f.legajo());
			
			int filas = ps.executeUpdate();
			
			if (filas != 1) {
				throw new SQLException("No se pudo insertar el funcionario");
			}
			
			try (ResultSet rs = ps.getGeneratedKeys()){
				if (rs.next()) {					
					return rs.getLong("id");
				}
			}
			
			throw new SQLException("No se obtuvo el ID generado");
		}
	}

	//SELECT por PK
	//Optional si no existe
	public Optional<Funcionario> buscarPorId(long id) throws SQLException {
		String sql = "SELECT " + COLUMNAS + " FROM funcionarios WHERE id = ?";
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(sql)){
			ps.setLong(1, id);
			
			try (ResultSet rs = ps.executeQuery()){
				if (rs.next()) {
					return Optional.of(mapear(rs));
				}
				return Optional.empty();
			}
		}
	}
	
	//SELECT de varios registros
	public List<Funcionario> listarTodos() throws SQLException {
		String sql = "SELECT " + COLUMNAS + " FROM funcionarios ORDER BY id";
		List<Funcionario> funcionarios = new ArrayList<>();
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery()){
			while (rs.next()) {
				funcionarios.add(mapear(rs));
			}
		}
		return funcionarios;
	}
	
	//UPDATE por PK
	//devuelve false si no existe el id
	public boolean actualizar(Funcionario f) throws SQLException {
		String sql = """
				UPDATE funcionarios
				SET nombre = ?,
				    apellido = ?,
				    edad = ?,
				    fecha_nacimiento = ?,
				    fecha_ingreso = ?,
				    foto = ?,
				    fecha_ult_modif = ?,
				    legajo = ?
				WHERE id = ?
				""";
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(sql)){
			ps.setString(1, f.nombre());
			ps.setString(2, f.apellido());
			if (f.edad() == null) {
				ps.setNull(3, Types.INTEGER);
			}else {
				ps.setInt(3, f.edad());
			}
			ps.setObject(4, f.fechaNacimiento());
			ps.setObject(5, f.fechaIngreso());
			if (f.foto() == null) {
				ps.setNull(6, Types.BINARY);
			}else {
				ps.setBytes(6, f.foto());
			}
			ps.setObject(7, f.fechaUltModif());
			if (f.legajo() == null) {
				ps.setNull(8, Types.INTEGER);
			}else {
				ps.setInt(8, f.legajo());
			}
			ps.setLong(9, f.id());
			
			return ps.executeUpdate() == 1;
		}
	}
	
	//SELECT con LIKE (el patrón va como parámetro, p. ej. "Ben%")
	public List<Funcionario> buscarPorApellido(String patron) throws SQLException {
		String sql = "SELECT " + COLUMNAS + " FROM funcionarios WHERE apellido LIKE ? ORDER BY apellido, nombre, id";
		return listar(sql, ps -> ps.setString(1, patron));
	}
	
	//SELECT con BETWEEN de fechas (LocalDate)
	public List<Funcionario> buscarPorRangoIngreso(LocalDate desde, LocalDate hasta) throws SQLException {
		String sql = "SELECT " + COLUMNAS + " FROM funcionarios WHERE fecha_ingreso BETWEEN ? AND ? ORDER BY fecha_ingreso, id";
		return listar(sql, ps -> {
			ps.setObject(1, desde);
			ps.setObject(2, hasta);
		});
	}
	
	//SELECT del primero con ese legajo
	public Optional<Funcionario> buscarPorLegajo(int legajo) throws SQLException {
		String sql = "SELECT " + COLUMNAS + " FROM funcionarios WHERE legajo = ? ORDER BY id LIMIT 1";
		List<Funcionario> encontrados = listar(sql, ps -> ps.setInt(1, legajo));
		return encontrados.stream().findFirst();
	}
	
	//Paginación con LIMIT / OFFSET (pagina empieza en 1)
	public List<Funcionario> listarPagina(int pagina, int tamanio) throws SQLException {
		String sql = "SELECT " + COLUMNAS + " FROM funcionarios ORDER BY id LIMIT ? OFFSET ?";
		return listar(sql, ps -> {
			ps.setInt(1, tamanio);
			ps.setInt(2, (pagina - 1) * tamanio);
		});
	}
	
	//Función de agregación: COUNT
	public long contar() throws SQLException {
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM funcionarios");
			ResultSet rs = ps.executeQuery()){
			rs.next();
			return rs.getLong(1);
		}
	}
	
	//Función de agregación: AVG (vacío si no hay edades cargadas)
	public Optional<BigDecimal> promedioEdad() throws SQLException {
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement("SELECT AVG(edad) FROM funcionarios");
			ResultSet rs = ps.executeQuery()){
			rs.next();
			BigDecimal promedio = rs.getBigDecimal(1);
			return Optional.ofNullable(promedio).map(p -> p.setScale(2, RoundingMode.HALF_UP));
		}
	}
	
	//INSERT en batch: una sola conexión, una sola transacción y un solo viaje por lote
	//devuelve los IDs generados en el mismo orden
	public List<Long> insertarVarios(List<Funcionario> funcionarios) throws SQLException {
		String sql = """
				INSERT INTO funcionarios
				(nombre, apellido, edad, fecha_nacimiento, fecha_ingreso, foto, fecha_ult_modif, legajo)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""";
		List<Long> ids = new ArrayList<>();
		try (Connection conn = Database.getDataSource().getConnection()) {
			conn.setAutoCommit(false);
			try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
				for (Funcionario f : funcionarios) {
					ps.setString(1, f.nombre());
					ps.setString(2, f.apellido());
					ps.setObject(3, f.edad(), Types.INTEGER);
					ps.setObject(4, f.fechaNacimiento());
					ps.setObject(5, f.fechaIngreso());
					if (f.foto() == null) {
						ps.setNull(6, Types.BINARY);
					}else {
						ps.setBytes(6, f.foto());
					}
					ps.setObject(7, f.fechaUltModif());
					ps.setObject(8, f.legajo(), Types.INTEGER);
					ps.addBatch();
				}
				ps.executeBatch();
				
				try (ResultSet rs = ps.getGeneratedKeys()){
					while (rs.next()) {
						ids.add(rs.getLong("id"));
					}
				}
				conn.commit();
			}catch (SQLException e) {
				conn.rollback();
				throw e;
			}finally {
				conn.setAutoCommit(true);
			}
		}
		return ids;
	}
	
	//DELETE por PK (devuelve false si no existía)
	public boolean eliminar(long id) throws SQLException {
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement("DELETE FROM funcionarios WHERE id = ?")){
			ps.setLong(1, id);
			return ps.executeUpdate() == 1;
		}
	}
	
	//DELETE en batch dentro de una transacción; devuelve cuántas filas se borraron
	public int eliminarVarios(List<Long> ids) throws SQLException {
		try (Connection conn = Database.getDataSource().getConnection()) {
			conn.setAutoCommit(false);
			try (PreparedStatement ps = conn.prepareStatement("DELETE FROM funcionarios WHERE id = ?")){
				for (long id : ids) {
					ps.setLong(1, id);
					ps.addBatch();
				}
				int total = 0;
				for (int filas : ps.executeBatch()) {
					total += filas;
				}
				conn.commit();
				return total;
			}catch (SQLException e) {
				conn.rollback();
				throw e;
			}finally {
				conn.setAutoCommit(true);
			}
		}
	}
	
	//¡NO USAR! Sólo para demostrar la inyección SQL:
	//el texto del usuario se pega dentro del SQL con un Statement
	public List<Funcionario> buscarPorApellidoInseguro(String apellido) throws SQLException {
		String sql = sqlInseguro(apellido);
		List<Funcionario> funcionarios = new ArrayList<>();
		try (Connection conn = Database.getDataSource().getConnection();
			Statement st = conn.createStatement();
			ResultSet rs = st.executeQuery(sql)){
			while (rs.next()) {
				funcionarios.add(mapear(rs));
			}
		}
		return funcionarios;
	}
	
	//El SQL que arma buscarPorApellidoInseguro (para mostrarlo en pantalla)
	public String sqlInseguro(String apellido) {
		return "SELECT " + COLUMNAS.strip() + " FROM funcionarios WHERE apellido = '" + apellido + "'";
	}
	
	//Forma correcta: PreparedStatement, el texto del usuario viaja como valor y nunca como SQL
	public List<Funcionario> buscarPorApellidoSeguro(String apellido) throws SQLException {
		String sql = "SELECT " + COLUMNAS + " FROM funcionarios WHERE apellido = ? ORDER BY id";
		return listar(sql, ps -> ps.setString(1, apellido));
	}
	
	@FunctionalInterface
	private interface Parametros {
		void setear(PreparedStatement ps) throws SQLException;
	}
	
	//Ejecuta un SELECT con parámetros y convierte cada fila en Funcionario
	private List<Funcionario> listar(String sql, Parametros parametros) throws SQLException {
		List<Funcionario> funcionarios = new ArrayList<>();
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(sql)){
			parametros.setear(ps);
			try (ResultSet rs = ps.executeQuery()){
				while (rs.next()) {
					funcionarios.add(mapear(rs));
				}
			}
		}
		return funcionarios;
	}
	
	//ResultSet -> record
	//getObject(..., Integer.class) devuelve null si la columna es NULL (getInt devolvería 0)
	private Funcionario mapear(ResultSet rs) throws SQLException {
		return new Funcionario(
				rs.getLong("id"),
				rs.getString("nombre"),
				rs.getString("apellido"),
				rs.getObject("edad", Integer.class),
				rs.getObject("fecha_nacimiento", LocalDate.class),
				rs.getObject("fecha_ingreso", LocalDate.class),
				rs.getBytes("foto"),
				rs.getObject("fecha_ult_modif", LocalDateTime.class),
				rs.getObject("legajo", Integer.class));
	}
}
