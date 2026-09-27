package py.edu.ucsa.jdbc.tx.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import py.edu.ucsa.jdbc.tx.Database;
import py.edu.ucsa.jdbc.tx.model.Movimiento;
import py.edu.ucsa.jdbc.tx.model.ResumenCuenta;

public class CuentaDao {

	//UPDATE y, si no existía la cuenta, INSERT (deja el saldo en el valor indicado)
	public void fijarSaldo(long funcionarioId, BigDecimal saldo) throws SQLException {
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement update = conn.prepareStatement(
					"UPDATE cuenta_funcionario SET saldo = ? WHERE funcionario_id = ?")){
			update.setBigDecimal(1, saldo);
			update.setLong(2, funcionarioId);
			if (update.executeUpdate() == 0) {
				try (PreparedStatement insert = conn.prepareStatement(
						"INSERT INTO cuenta_funcionario (funcionario_id, saldo) VALUES (?, ?)")){
					insert.setLong(1, funcionarioId);
					insert.setBigDecimal(2, saldo);
					insert.executeUpdate();
				}
			}
		}
	}
	
	public Optional<BigDecimal> obtenerSaldo(long funcionarioId) throws SQLException {
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(
					"SELECT saldo FROM cuenta_funcionario WHERE funcionario_id = ?")){
			ps.setLong(1, funcionarioId);
			try (ResultSet rs = ps.executeQuery()){
				return rs.next() ? Optional.of(rs.getBigDecimal("saldo")) : Optional.empty();
			}
		}
	}
	
	public long contarMovimientos(long funcionarioId) throws SQLException {
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(
					"SELECT COUNT(*) FROM movimiento WHERE funcionario_id = ?")){
			ps.setLong(1, funcionarioId);
			try (ResultSet rs = ps.executeQuery()){
				rs.next();
				return rs.getLong(1);
			}
		}
	}
	
	//JOIN movimiento + funcionarios: últimos movimientos de un funcionario
	public List<Movimiento> ultimosMovimientos(long funcionarioId, int cantidad) throws SQLException {
		String sql = """
				SELECT m.id, m.funcionario_id, f.nombre || ' ' || f.apellido AS funcionario,
				       m.tipo, m.monto, m.fecha_hora, m.descripcion
				FROM movimiento m
				JOIN funcionarios f ON f.id = m.funcionario_id
				WHERE m.funcionario_id = ?
				ORDER BY m.id DESC
				LIMIT ?
				""";
		List<Movimiento> movimientos = new ArrayList<>();
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(sql)){
			ps.setLong(1, funcionarioId);
			ps.setInt(2, cantidad);
			try (ResultSet rs = ps.executeQuery()){
				while (rs.next()) {
					movimientos.add(new Movimiento(
							rs.getLong("id"),
							rs.getLong("funcionario_id"),
							rs.getString("funcionario"),
							rs.getString("tipo"),
							rs.getBigDecimal("monto"),
							rs.getObject("fecha_hora", LocalDateTime.class),
							rs.getString("descripcion")));
				}
			}
		}
		return movimientos;
	}
	
	//JOIN funcionarios + cuenta_funcionario + LEFT JOIN movimiento con GROUP BY
	public List<ResumenCuenta> resumenCuentas() throws SQLException {
		String sql = """
				SELECT f.id,
				       f.nombre || ' ' || f.apellido AS funcionario,
				       c.saldo,
				       COUNT(m.id) AS cantidad,
				       COALESCE(SUM(CASE WHEN m.tipo = 'CREDITO' THEN m.monto END), 0) AS creditos,
				       COALESCE(SUM(CASE WHEN m.tipo = 'DEBITO'  THEN m.monto END), 0) AS debitos
				FROM funcionarios f
				JOIN cuenta_funcionario c ON c.funcionario_id = f.id
				LEFT JOIN movimiento m ON m.funcionario_id = f.id
				GROUP BY f.id, f.nombre, f.apellido, c.saldo
				ORDER BY f.id
				""";
		List<ResumenCuenta> resumen = new ArrayList<>();
		try (Connection conn = Database.getDataSource().getConnection();
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery()){
			while (rs.next()) {
				resumen.add(new ResumenCuenta(
						rs.getLong("id"),
						rs.getString("funcionario"),
						rs.getBigDecimal("saldo"),
						rs.getLong("cantidad"),
						rs.getBigDecimal("creditos"),
						rs.getBigDecimal("debitos")));
			}
		}
		return resumen;
	}
}
