package py.edu.ucsa.jdbc.tx.dao;

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
