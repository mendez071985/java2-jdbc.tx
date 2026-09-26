package py.edu.ucsa.jdbc.tx;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class Database {
	private static final HikariDataSource DATA_SOURCE;
	
	static {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:postgresql://localhost:5435/ucsajava");
		config.setUsername("postgres");
		config.setPassword("postgres");
		
		config.setMaximumPoolSize(10);
		config.setMinimumIdle(2);
		
		config.setConnectionTimeout(5000);
		
		DATA_SOURCE = new HikariDataSource(config);
	}
	
	private Database() {}
	
	public static DataSource getDataSource() {
		return DATA_SOURCE;
	}
	
	public static void cerrarPool() {
		DATA_SOURCE.close();
	}
}
