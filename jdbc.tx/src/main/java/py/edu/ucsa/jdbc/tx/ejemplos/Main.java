package py.edu.ucsa.jdbc.tx.ejemplos;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

        Funcionario funcionario =
                new Funcionario(
                        null,
                        "Casilda",
                        "Pereira",
                        null,
                        LocalDate.of(
                                1994,
                                8,
                                17
                        ),
                        LocalDate.now(),
                        null,
                        LocalDateTime.now(),
                        1009
                );

        // Agrega a la base las tablas/columnas que falten (no borra datos)
        PreparacionBase.asegurarTablas(Database.getDataSource());

        FuncionarioDao dao =
                new FuncionarioDao();

        long id =
                dao.insertar(funcionario);

        System.out.println(
                "Funcionario creado. ID = " + id
        );

        Database.cerrarPool();
    }
}