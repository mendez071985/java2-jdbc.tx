package py.edu.ucsa.jdbc.tx.ejemplos;

import java.sql.SQLException;
import java.util.List;

import py.edu.ucsa.jdbc.tx.dao.FuncionarioDao;
import py.edu.ucsa.jdbc.tx.model.Funcionario;

/**
 * 5. Inyección SQL: por qué nunca hay que armar el SQL concatenando lo que
 * escribe el usuario, y por qué PreparedStatement lo evita.
 */
final class EjemploInyeccionSql {

	private EjemploInyeccionSql() {
	}

	static void ejecutar() throws Exception {
		Salida.titulo("5. INYECCIÓN SQL: Statement concatenado vs PreparedStatement");
		FuncionarioDao dao = new FuncionarioDao();
		System.out.println("  Búsqueda de funcionarios por apellido exacto. Total en la tabla: " + dao.contar());

		comparar(dao, "Pereira", "Entrada normal");
		comparar(dao, "x' OR '1'='1", "Entrada maliciosa: la condición pasa a ser siempre verdadera");
		comparar(dao, "O'Higgins", "Apellido con apóstrofo: el SQL concatenado ni siquiera es válido");

		Salida.paso("Conclusión");
		System.out.println("  Con Statement + concatenación, lo que escribe el usuario se convierte en SQL:");
		System.out.println("  puede leer datos que no debería (o algo peor). Con PreparedStatement el valor");
		System.out.println("  viaja por separado (parámetro ?) y siempre se trata como un dato.");
	}

	private static void comparar(FuncionarioDao dao, String entrada, String descripcion) {
		Salida.paso(descripcion + ":  " + entrada);

		System.out.println("  INSEGURO (Statement):");
		System.out.println("    SQL enviado: " + dao.sqlInseguro(entrada));
		try {
			List<Funcionario> filas = dao.buscarPorApellidoInseguro(entrada);
			System.out.println("    Resultado:   " + filas.size() + " registro(s)"
					+ (filas.size() > 1 && !"Pereira".equals(entrada) ? "  <-- ¡devolvió TODA la tabla!" : ""));
		} catch (SQLException e) {
			String mensaje = e.getMessage().lines().findFirst().orElse("");
			int corte = mensaje.indexOf(" in SQL");
			System.out.println("    Resultado:   ERROR de SQL -> " + (corte > 0 ? mensaje.substring(0, corte) : mensaje));
		}

		System.out.println("  SEGURO (PreparedStatement):");
		System.out.println("    SQL enviado: ... WHERE apellido = ?   con el parámetro [" + entrada + "]");
		try {
			List<Funcionario> filas = dao.buscarPorApellidoSeguro(entrada);
			System.out.println("    Resultado:   " + filas.size() + " registro(s)");
		} catch (SQLException e) {
			System.out.println("    Resultado:   ERROR -> " + e.getMessage());
		}
	}
}
