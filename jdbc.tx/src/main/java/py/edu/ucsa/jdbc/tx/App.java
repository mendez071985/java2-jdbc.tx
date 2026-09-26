package py.edu.ucsa.jdbc.tx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada Spring Boot: levanta el DataSource (HikariCP) y ejecuta el
 * Database Inspector por consola.
 */
@SpringBootApplication
public class App {

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}
}
