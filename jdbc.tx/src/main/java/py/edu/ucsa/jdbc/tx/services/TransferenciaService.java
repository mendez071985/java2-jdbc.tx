package py.edu.ucsa.jdbc.tx.services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import py.edu.ucsa.jdbc.tx.Database;

public class TransferenciaService {

	private BigDecimal obtenerSaldo(Connection c, long funcionarioId) throws SQLException {
		String sql = """
				SELECT saldo
	            FROM cuenta_funcionario
	            WHERE funcionario_id = ?
	            FOR UPDATE
				""";
		try (PreparedStatement ps = c.prepareStatement(sql)){
			ps.setLong(1, funcionarioId);
			
			try(ResultSet rs = ps.executeQuery()){
				if (!rs.next()) {
					throw new SQLException("Funcionario sin cuenta");
				}
				return rs.getBigDecimal("saldo");
			}
		}
				
	}
	
	private void actualizarSaldo(Connection c, long funcionarioId, BigDecimal diferencia) throws SQLException {
		String sql = """
				UPDATE cuenta_funcionario
	            SET saldo = saldo + ?
	            WHERE funcionario_id = ?
				""";
		try (PreparedStatement ps = c.prepareStatement(sql)){
			ps.setBigDecimal(1, diferencia);
			ps.setLong(2, funcionarioId);
			
			int filas = ps.executeUpdate();
			if (filas != 1) {
				throw new SQLException("No se pudo actualizar la cuenta");
			}
		}
	}
	
	private long registrarMovimiento(Connection c, long funcionarioId, String tipo, BigDecimal monto, String descripcion) throws SQLException {
		String sql = """
				INSERT INTO movimiento
		        (
		            funcionario_id,
		            tipo,
		            monto,
		            fecha_hora,
		            descripcion
		        )
		        VALUES (?, ?, ?, ?, ?)
				""";
		try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
			ps.setLong(1, funcionarioId);
			ps.setString(2, tipo);
			ps.setBigDecimal(3, monto);
			ps.setObject(4, LocalDateTime.now());
			ps.setString(5, descripcion);
			
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()){
				if (rs.next()) {					
					return rs.getLong("id");
				}
			}
			
			throw new SQLException("No se obtuvo el ID del movimiento");
		}
	}
	
	/**
	 * Transaccion
	 * @param origenFuncionarioId
	 * @param destinoFuncionarioId
	 * @param monto
	 * @throws SQLException
	 */
	public void transferir(long origenFuncionarioId, long destinoFuncionarioId, BigDecimal monto) throws SQLException {
		if (monto.signum() <= 0) {
			throw new IllegalArgumentException("El monto debe ser mayor que cero");
		}
		try (Connection c = Database.getDataSource().getConnection()){
			c.setAutoCommit(false);
			try {
				BigDecimal saldoOrigen = this.obtenerSaldo(c, origenFuncionarioId);
				//BigDecimal saldoDestino = this.obtenerSaldo(c, destinoFuncionarioId);
				
				if (saldoOrigen.compareTo(monto) < 0) {
					throw new SQLException("Saldo insuficiente");
				}
				
				actualizarSaldo(c, origenFuncionarioId, monto.negate());
				actualizarSaldo(c, destinoFuncionarioId, monto);
				
				registrarMovimiento(c, origenFuncionarioId, "DEBITO", monto, "Transferencia a funcionario " + destinoFuncionarioId);
				registrarMovimiento(c, destinoFuncionarioId, "CREDITO", monto, "Transferencia recibida de funcionario " + origenFuncionarioId);
				
				c.commit();
			}catch(Exception e) {
				c.rollback();
				throw e;
			}finally {
				c.setAutoCommit(true);
			}
		}
	}
}
