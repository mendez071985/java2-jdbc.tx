package py.edu.ucsa.jdbc.tx.ejemplos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import py.edu.ucsa.jdbc.tx.Database;
import py.edu.ucsa.jdbc.tx.dao.FuncionarioDao;
import py.edu.ucsa.jdbc.tx.model.Funcionario;

public class Main {

	/*
	 * SI QUEREMOS QUE ANA TRANSFIERA A CARLOS 200.000 GS. NECESITAMOS MÀS DE UNA OPERACIÓN
	 * ACÁ TIENE SENTIDO UNA TRANSACCION
	 */
    public static void main(String[] args)
            throws Exception {

        // Agrega a la base las tablas/columnas que falten (no borra datos)
        PreparacionBase.asegurarTablas(Database.getDataSource());

        FuncionarioDao dao =
                new FuncionarioDao();

        try {
            // ---------------------------------------------------------------
            // PASO 1: INSERT de varios funcionarios
            // ---------------------------------------------------------------
            titulo("PASO 1: INSERT de varios funcionarios");

            List<Funcionario> nuevos = List.of(
                    new Funcionario(
                            null,
                            "Casilda",
                            "Pereira",
                            null,                       // edad NULL
                            LocalDate.of(1994, 8, 17),
                            LocalDate.now(),
                            null,                       // sin foto
                            LocalDateTime.now(),
                            1009
                    ),
                    new Funcionario(
                            null,
                            "Ana",
                            "Benítez",
                            30,
                            LocalDate.of(1996, 3, 2),
                            LocalDate.of(2020, 2, 1),
                            new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE },
                            LocalDateTime.now(),
                            1010
                    ),
                    new Funcionario(
                            null,
                            "Carlos",
                            "Giménez",
                            45,
                            LocalDate.of(1981, 11, 23),
                            LocalDate.of(2010, 7, 15),
                            null,
                            LocalDateTime.now(),
                            1011
                    )
            );

            List<Long> ids = new ArrayList<>();
            for (Funcionario f : nuevos) {
                long id = dao.insertar(f);
                ids.add(id);
                System.out.println("Insertado: " + f.nombre() + " " + f.apellido() + " -> ID = " + id);
            }

            // ---------------------------------------------------------------
            // PASO 2: SELECT de los registros insertados (por ID)
            // ---------------------------------------------------------------
            titulo("PASO 2: SELECT de los registros insertados");
            for (long id : ids) {
                mostrar(buscar(dao, id));
            }

            // ---------------------------------------------------------------
            // PASO 3: UPDATE de los registros insertados
            // ---------------------------------------------------------------
            titulo("PASO 3: UPDATE de los registros insertados");

            Funcionario casilda = buscar(dao, ids.get(0));
            Funcionario casildaActualizada = new Funcionario(
                    casilda.id(),
                    casilda.nombre(),
                    casilda.apellido(),
                    32,                                 // antes NULL
                    casilda.fechaNacimiento(),
                    casilda.fechaIngreso(),
                    new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }, // se le carga una foto
                    LocalDateTime.now(),
                    casilda.legajo()
            );
            System.out.println("Actualizar ID " + casilda.id() + " (edad y foto): "
                    + resultado(dao.actualizar(casildaActualizada)));

            Funcionario ana = buscar(dao, ids.get(1));
            Funcionario anaActualizada = new Funcionario(
                    ana.id(),
                    ana.nombre(),
                    "Benítez Rojas",                    // cambia el apellido
                    ana.edad() + 1,
                    ana.fechaNacimiento(),
                    ana.fechaIngreso(),
                    null,                               // se le quita la foto
                    LocalDateTime.now(),
                    ana.legajo()
            );
            System.out.println("Actualizar ID " + ana.id() + " (apellido, edad y sin foto): "
                    + resultado(dao.actualizar(anaActualizada)));

            Funcionario carlos = buscar(dao, ids.get(2));
            Funcionario carlosActualizado = new Funcionario(
                    carlos.id(),
                    carlos.nombre(),
                    carlos.apellido(),
                    carlos.edad(),
                    carlos.fechaNacimiento(),
                    LocalDate.now(),                    // reingreso
                    carlos.foto(),
                    LocalDateTime.now(),
                    2011                                // nuevo legajo
            );
            System.out.println("Actualizar ID " + carlos.id() + " (fecha de ingreso y legajo): "
                    + resultado(dao.actualizar(carlosActualizado)));

            // UPDATE de un ID que no existe: no falla, devuelve false
            Funcionario inexistente = new Funcionario(
                    -1L, "No", "Existe", null, LocalDate.now(), LocalDate.now(), null, LocalDateTime.now(), 0);
            System.out.println("Actualizar ID -1 (no existe): " + resultado(dao.actualizar(inexistente)));

            // ---------------------------------------------------------------
            // PASO 4: SELECT de los registros actualizados
            // ---------------------------------------------------------------
            titulo("PASO 4: SELECT de los registros actualizados");
            for (long id : ids) {
                mostrar(buscar(dao, id));
            }

            // ---------------------------------------------------------------
            // PASO 5: SELECT de todos los funcionarios
            // ---------------------------------------------------------------
            titulo("PASO 5: SELECT de todos los funcionarios");
            List<Funcionario> todos = dao.listarTodos();
            System.out.println("Total de funcionarios en la tabla: " + todos.size());
            System.out.println("Últimos registros:");
            todos.stream()
                    .skip(Math.max(0, todos.size() - 5))
                    .forEach(Main::mostrar);

            // ---------------------------------------------------------------
            // PASO 6: SELECT de un ID que no existe
            // ---------------------------------------------------------------
            titulo("PASO 6: SELECT de un ID que no existe");
            Optional<Funcionario> noExiste = dao.buscarPorId(-1);
            System.out.println("buscarPorId(-1) -> "
                    + (noExiste.isPresent() ? noExiste.get() : "no existe (Optional.empty)"));
        } finally {
            Database.cerrarPool();
        }
    }

    private static Funcionario buscar(FuncionarioDao dao, long id) throws Exception {
        return dao.buscarPorId(id)
                .orElseThrow(() -> new IllegalStateException("No se encontró el funcionario " + id));
    }

    private static void mostrar(Funcionario f) {
        System.out.printf(
                "  ID %-4d | %-10s %-15s | edad %-4s | nac. %s | ingreso %s | legajo %-5s | foto %-9s | modif. %s%n",
                f.id(),
                f.nombre(),
                f.apellido(),
                f.edad() == null ? "NULL" : f.edad(),
                f.fechaNacimiento(),
                f.fechaIngreso(),
                f.legajo() == null ? "NULL" : f.legajo(),
                f.foto() == null ? "NULL" : f.foto().length + " bytes",
                f.fechaUltModif() == null ? "NULL" : f.fechaUltModif().withNano(0)
        );
    }

    private static String resultado(boolean actualizado) {
        return actualizado ? "OK (1 fila actualizada)" : "no se actualizó ninguna fila";
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("=== " + texto + " ===");
    }
}
