package py.edu.ucsa.jdbc.tx.inspector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Arranca la consola interactiva al iniciar la aplicación (se desactiva en los tests). */
@Component
@ConditionalOnProperty(name = "inspector.console.enabled", havingValue = "true", matchIfMissing = true)
public class InspectorRunner implements CommandLineRunner {

	private final DatabaseInspectorService inspector;
	private final String url;

	public InspectorRunner(DatabaseInspectorService inspector, @Value("${spring.datasource.url:}") String url) {
		this.inspector = inspector;
		this.url = url;
	}

	@Override
	public void run(String... args) throws Exception {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
		try {
			new DatabaseInspectorConsole(inspector, in, System.out).ejecutar();
		} catch (SQLException e) {
			System.err.println();
			System.err.println("ERROR: no se pudo trabajar con la base de datos " + url);
			System.err.println("       " + e.getMessage());
			System.err.println("Verifique que el servidor esté iniciado y que la URL, el usuario y la clave sean correctos");
			System.err.println("(src/main/resources/application.properties o variables DB_URL, DB_USER, DB_PASSWORD).");
		}
	}
}
