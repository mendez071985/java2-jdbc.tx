package py.edu.ucsa.jdbc.tx.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import py.edu.ucsa.jdbc.tx.Database;
import py.edu.ucsa.jdbc.tx.model.Funcionario;

public class FuncionarioDao {

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
}
