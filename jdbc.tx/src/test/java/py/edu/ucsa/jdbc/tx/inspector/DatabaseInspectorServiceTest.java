package py.edu.ucsa.jdbc.tx.inspector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import py.edu.ucsa.jdbc.tx.inspector.model.ColumnInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.DatabaseInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.ForeignKeyInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.IndexInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.PrimaryKeyInfo;
import py.edu.ucsa.jdbc.tx.inspector.model.QueryResult;
import py.edu.ucsa.jdbc.tx.inspector.model.TableRef;

/**
 * Verifica cada parte del trabajo práctico contra la base de prueba
 * (H2 por defecto, o PostgreSQL con -Dspring.profiles.active=pg).
 */
@SpringBootTest
class DatabaseInspectorServiceTest {

	@Autowired
	DatabaseInspectorService inspector;

	private TableRef tabla(String nombre) throws Exception {
		return inspector.listarTablas().stream()
				.filter(t -> t.name().equals(nombre))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No se descubrió la tabla " + nombre));
	}

	private static ColumnInfo columna(List<ColumnInfo> columnas, String nombre) {
		return columnas.stream().filter(c -> c.name().equals(nombre)).findFirst().orElseThrow();
	}

	@Test
	void parte1_informacionDelMotorYDriver() throws Exception {
		DatabaseInfo info = inspector.obtenerInfoBase();

		assertThat(info.productName()).isNotBlank();
		assertThat(info.productVersion()).isNotBlank();
		assertThat(info.driverName()).isNotBlank();
		assertThat(info.driverVersion()).isNotBlank();
		assertThat(info.url()).startsWith("jdbc:");
		assertThat(info.userName()).isNotBlank();
		assertThat(info.supportsTransactions()).isTrue();
		assertThat(info.supportsBatchUpdates()).isTrue();
		assertThat(info.readOnly()).isFalse();
	}

	@Test
	void parte2_descubreLasTablasDinamicamente() throws Exception {
		List<TableRef> tablas = inspector.listarTablas();

		assertThat(tablas).extracting(TableRef::name)
				.contains("departamento", "funcionarios", "movimiento", "proyecto", "asignacion_proyecto", "Log Eventos");
		assertThat(tablas).allSatisfy(t -> assertThat(t.schema()).isEqualTo("public"));
	}

	@Test
	void parte3_columnasConTipoTamanioNullYAutoincremento() throws Exception {
		List<ColumnInfo> columnas = inspector.listarColumnas(tabla("funcionarios"));

		assertThat(columnas).extracting(ColumnInfo::name).containsExactly(
				"id", "nombre", "apellido", "edad", "fecha_nacimiento", "fecha_ingreso",
				"foto", "fecha_ult_modif", "legajo", "departamento_id");

		ColumnInfo id = columna(columnas, "id");
		assertThat(id.autoIncrement()).isEqualTo("SI");
		assertThat(id.nullable()).isEqualTo("NO");
		assertThat(id.jdbcType()).isEqualTo(java.sql.Types.BIGINT);

		ColumnInfo nombre = columna(columnas, "nombre");
		assertThat(nombre.size()).isEqualTo(60);
		assertThat(nombre.nullable()).isEqualTo("NO");
		assertThat(nombre.autoIncrement()).isEqualTo("NO");
		assertThat(nombre.jdbcType()).isEqualTo(java.sql.Types.VARCHAR);

		assertThat(columna(columnas, "edad").nullable()).isEqualTo("SI");

		ColumnInfo monto = columna(inspector.listarColumnas(tabla("movimiento")), "monto");
		assertThat(monto.size()).isEqualTo(15);
		assertThat(monto.decimalDigits()).isEqualTo(2);
	}

	@Test
	void parte4_clavePrimariaSimpleYCompuesta() throws Exception {
		assertThat(inspector.obtenerClavePrimaria(tabla("funcionarios")).columns()).containsExactly("id");

		PrimaryKeyInfo compuesta = inspector.obtenerClavePrimaria(tabla("asignacion_proyecto"));
		assertThat(compuesta.name()).isNotBlank();
		assertThat(compuesta.columns()).containsExactly("funcionario_id", "proyecto_codigo", "proyecto_anio");

		assertThat(inspector.obtenerClavePrimaria(tabla("Log Eventos")).exists()).isFalse();
	}

	@Test
	void parte5_clavesForaneas() throws Exception {
		List<ForeignKeyInfo> fks = inspector.listarClavesForaneas(tabla("funcionarios"));
		assertThat(fks).singleElement().satisfies(fk -> {
			assertThat(fk.column()).isEqualTo("departamento_id");
			assertThat(fk.referencedTable()).isEqualTo("public.departamento");
			assertThat(fk.referencedColumn()).isEqualTo("id");
			assertThat(fk.onDelete()).isEqualTo("SET NULL");
		});

		List<ForeignKeyInfo> compuesta = inspector.listarClavesForaneas(tabla("asignacion_proyecto")).stream()
				.filter(fk -> fk.name().equalsIgnoreCase("fk_asignacion_proyecto"))
				.toList();
		assertThat(compuesta).extracting(ForeignKeyInfo::column).containsExactly("proyecto_codigo", "proyecto_anio");
		assertThat(compuesta).extracting(ForeignKeyInfo::referencedColumn).containsExactly("codigo", "anio");
		assertThat(compuesta).allSatisfy(fk -> assertThat(fk.onUpdate()).isEqualTo("CASCADE"));

		assertThat(inspector.listarClavesForaneas(tabla("Log Eventos"))).isEmpty();
	}

	@Test
	void parte6_indices() throws Exception {
		List<IndexInfo> indices = inspector.listarIndices(tabla("funcionarios"));

		assertThat(indices).filteredOn(i -> i.name().equalsIgnoreCase("idx_funcionarios_apellido_nombre"))
				.singleElement()
				.satisfies(i -> {
					assertThat(i.unique()).isFalse();
					assertThat(i.columns()).containsExactly("apellido", "nombre");
				});
		assertThat(indices).anySatisfy(i -> {
			assertThat(i.unique()).isTrue();
			assertThat(i.columns()).containsExactly("legajo");
		});
		assertThat(indices).anySatisfy(i -> {
			assertThat(i.unique()).isTrue();
			assertThat(i.columns()).containsExactly("id");
		});
	}

	@Test
	void parte7_consultaConResultSetMetaDataYMaximo10Registros() throws Exception {
		TableRef funcionarios = tabla("funcionarios");
		QueryResult resultado = inspector.consultar(funcionarios);

		// Las columnas del resultado se descubren con ResultSetMetaData y coinciden con getColumns()
		assertThat(resultado.columns()).extracting(QueryResult.ResultColumn::label)
				.containsExactlyElementsOf(inspector.listarColumnas(funcionarios).stream().map(ColumnInfo::name).toList());
		// La tabla tiene 25 registros pero sólo se muestran 10
		assertThat(resultado.rows()).hasSize(DatabaseInspectorService.MAX_FILAS);
		assertThat(resultado.rows()).allSatisfy(fila -> assertThat(fila).hasSize(resultado.columns().size()));

		int foto = resultado.columns().stream().map(QueryResult.ResultColumn::label).toList().indexOf("foto");
		assertThat(resultado.rows()).extracting(fila -> fila.get(foto)).contains("[4 bytes]", "NULL");
	}

	@Test
	void consultaSobreTablaConNombreQueRequiereComillas() throws Exception {
		QueryResult resultado = inspector.consultar(tabla("Log Eventos"));

		assertThat(resultado.sql()).contains("\"Log Eventos\"");
		assertThat(resultado.columns()).extracting(QueryResult.ResultColumn::label).containsExactly("Mensaje");
		assertThat(resultado.rows()).containsExactly(List.of("arranque"));
	}

	@Test
	void todasLasTablasSePuedenInspeccionarCompletas() throws Exception {
		for (TableRef t : inspector.listarTablas()) {
			assertThat(inspector.listarColumnas(t)).as("columnas de %s", t.name()).isNotEmpty();
			inspector.obtenerClavePrimaria(t);
			inspector.listarClavesForaneas(t);
			inspector.listarIndices(t);
			assertThat(inspector.consultar(t).rows().size()).isLessThanOrEqualTo(DatabaseInspectorService.MAX_FILAS);
		}
	}

	/** Restricción del TP: el código del inspector no programa nombres de tablas ni columnas. */
	@Test
	void restriccion_noHayNombresDeTablasNiColumnasProgramados() throws Exception {
		String codigo = leerCodigoFuenteDelInspector();
		for (TableRef t : inspector.listarTablas()) {
			assertThat(codigo).as("nombre de tabla en el código").doesNotContain("\"" + t.name() + "\"");
			for (ColumnInfo c : inspector.listarColumnas(t)) {
				assertThat(codigo).as("nombre de columna en el código").doesNotContain("\"" + c.name() + "\"");
			}
		}
	}

	private static String leerCodigoFuenteDelInspector() throws IOException {
		Path dir = Path.of("src/main/java/py/edu/ucsa/jdbc/tx/inspector");
		StringBuilder sb = new StringBuilder();
		try (Stream<Path> archivos = Files.walk(dir)) {
			for (Path p : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
				sb.append(Files.readString(p));
			}
		}
		return sb.toString();
	}
}
