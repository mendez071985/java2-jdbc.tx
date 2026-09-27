package py.edu.ucsa.jdbc.tx.inspector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Arranca la consola interactiva al iniciar la aplicación (se desactiva en los tests). */
@Component
@ConditionalOnProperty(name = "inspector.console.enabled", havingValue = "true", matchIfMissing = true)
public class InspectorRunner implements CommandLineRunner {

	private final DatabaseInspectorService inspector;

	public InspectorRunner(DatabaseInspectorService inspector) {
		this.inspector = inspector;
	}

	@Override
	public void run(String... args) throws Exception {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
		new DatabaseInspectorConsole(inspector, in, System.out).ejecutar();
	}
}
