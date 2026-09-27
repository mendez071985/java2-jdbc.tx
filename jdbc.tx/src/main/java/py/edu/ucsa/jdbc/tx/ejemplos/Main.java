package py.edu.ucsa.jdbc.tx.ejemplos;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import py.edu.ucsa.jdbc.tx.Database;

/**
 * Menú de ejemplos de JDBC.
 * <p>
 * Sin argumentos muestra un menú por consola. Con argumentos ejecuta directamente
 * las opciones indicadas, por ejemplo {@code 2} o {@code 1 3} o {@code 9} (todas).
 */
public class Main {

	/*
	 * SI QUEREMOS QUE ANA TRANSFIERA A CARLOS 200.000 GS. NECESITAMOS MÀS DE UNA OPERACIÓN
	 * ACÁ TIENE SENTIDO UNA TRANSACCION  -> opción 2 del menú
	 */
    public static void main(String[] args)
            throws Exception {

        // Agrega a la base las tablas/columnas que falten (no borra datos)
        PreparacionBase.asegurarTablas(Database.getDataSource());

        try {
            if (args.length > 0) {
                for (String opcion : args) {
                    ejecutar(opcion.trim());
                }
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
            while (true) {
                mostrarMenu();
                String linea = in.readLine();
                if (linea == null || linea.trim().equals("0")) {
                    System.out.println("Fin de los ejemplos.");
                    return;
                }
                ejecutar(linea.trim());
            }
        } finally {
            Database.cerrarPool();
        }
    }

    private static void mostrarMenu() {
        System.out.println();
        System.out.println("################ EJEMPLOS JDBC ################");
        System.out.println("  1. CRUD: INSERT, SELECT, UPDATE y DELETE");
        System.out.println("  2. Transacciones: commit y rollback");
        System.out.println("  3. Consultas: LIKE, BETWEEN, COUNT/AVG, paginación y JOIN");
        System.out.println("  4. INSERT en batch vs uno por uno (y DELETE en batch)");
        System.out.println("  5. Inyección SQL: Statement vs PreparedStatement");
        System.out.println("  9. Ejecutar todos en orden");
        System.out.println("  0. Salir");
        System.out.print("Opción: ");
        System.out.flush();
    }

    private static void ejecutar(String opcion) {
        try {
            switch (opcion) {
                case "1" -> EjemploCrud.ejecutar();
                case "2" -> EjemploTransaccion.ejecutar();
                case "3" -> EjemploConsultas.ejecutar();
                case "4" -> EjemploBatch.ejecutar();
                case "5" -> EjemploInyeccionSql.ejecutar();
                case "9" -> {
                    EjemploCrud.ejecutar();
                    EjemploTransaccion.ejecutar();
                    EjemploConsultas.ejecutar();
                    EjemploBatch.ejecutar();
                    EjemploInyeccionSql.ejecutar();
                }
                default -> System.out.println("Opción inválida: \"" + opcion + "\"");
            }
        } catch (Exception e) {
            // un error en un ejemplo no corta el menú
            System.out.println();
            System.out.println("ERROR en el ejemplo " + opcion + ": " + e);
        }
    }
}
