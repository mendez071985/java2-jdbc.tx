# Database Inspector (JDBC + Spring Boot)

Aplicación de consola que inspecciona dinámicamente la estructura de una base de datos
usando sólo `DatabaseMetaData` y `ResultSetMetaData`. No hay nombres de tablas ni de
columnas programados: todo se descubre en tiempo de ejecución (hay un test que lo verifica).

| Parte del TP | Dónde |
|---|---|
| 1. Motor, driver, usuario, URL, transacciones, batch, sólo lectura | `DatabaseInspectorService.obtenerInfoBase()` |
| 2. Tablas (`getTables(null, "public", null, {"TABLE"})`), numeradas y seleccionables | `listarTablas()` + `DatabaseInspectorConsole` |
| 3. Columnas: nombre, tipo SQL, tamaño, NULL, autoincremental (`getColumns`) | `listarColumnas()` |
| 4. Clave primaria (`getPrimaryKeys`) | `obtenerClavePrimaria()` |
| 5. Claves foráneas (`getImportedKeys`) | `listarClavesForaneas()` |
| 6. Índices (`getIndexInfo`) | `listarIndices()` |
| 7. Consulta + `ResultSetMetaData`, máximo 10 registros | `consultar()` |

Código en `src/main/java/py/edu/ucsa/jdbc/tx/inspector/`.

## Requisitos

- Java 21 y Maven 3.9+
- PostgreSQL (por defecto `localhost:5435`, base `ucsajava`, usuario/clave `postgres`)

## Paso a paso

### 1. (Opcional) Cargar la base de ejemplo

```bash
psql -h localhost -p 5435 -U postgres -d ucsajava -f db/schema.sql
```

Crea 7 tablas con PK simples y compuestas, FK simples y compuestas, índices, columnas
autoincrementales, `bytea` y una tabla con espacios en el nombre (`"Log Eventos"`).
**Borra y recrea esas tablas** si ya existen.

#### ¿Ya tenés tus propias tablas? (sin borrar nada)

Si al ejecutar el ejemplo `ejemplos.Main` aparece un error como
`column "fecha_nacimiento" of relation "funcionarios" does not exist`, tu tabla tiene otra estructura.
Este script **no borra datos**: sólo agrega las columnas que falten y crea `cuenta_funcionario` y `movimiento` si no existen.

```bash
psql -h localhost -p 5435 -U postgres -d ucsajava -f db/ajustar_tablas_ejemplo.sql
```

### 2. Ejecutar la aplicación

**Clase principal: `py.edu.ucsa.jdbc.tx.App`** (no `ejemplos.Main`, que es el ejemplo de inserción).

#### Desde Spring Tools (STS) / Eclipse

1. *File → Import → Maven → Existing Maven Projects* y elegir la carpeta `jdbc.tx` (la que tiene el `pom.xml`).
2. Clic derecho en el proyecto → *Maven → Update Project…* (tildar *Force Update*) y luego *Project → Clean…*.
3. *Run → Run Configurations…*: borrar cualquier configuración vieja que apunte a `ejemplos.Main`.
4. Ejecutar con **Run → Run History / Favorites → DatabaseInspector** (viene incluida en `DatabaseInspector.launch`),
   o clic derecho en `App.java` → *Run As → Spring Boot App* / *Java Application*.
5. Escribir el número de la tabla en la vista *Console*.

#### Desde la terminal (no hace falta tener Maven instalado)

```bash
./mvnw spring-boot:run        # Linux / Mac
mvnw.cmd spring-boot:run      # Windows
# o, con Maven instalado:
mvn spring-boot:run
# o bien
mvn package -DskipTests && java -jar target/jdbc.tx-0.0.1-SNAPSHOT.jar
```

Para otra base, usar variables de entorno:

```bash
DB_URL=jdbc:postgresql://host:5432/otra DB_USER=yo DB_PASSWORD=secreto DB_SCHEMA=public mvn spring-boot:run
```

Elegir una tabla por su número; `0` sale.

### 3. Preparar datos, verificar el TP y prueba de estrés (desde STS, sin psql)

En STS: clic derecho en cada archivo `.launch` de la raíz del proyecto → *Run As* → el nombre de la configuración.
(O clic derecho en la clase → *Run As → Java Application*.)

| Paso | Configuración / clase | Qué hace |
|---|---|---|
| 1 | `1-PrepararDatos` → `demo.PrepararDatos` | Crea tablas `tp_*` y `"TP Auditoria"` con datos que usan **todo** lo que muestra el inspector: autoincrementales, NULL/NOT NULL, PK simple y compuesta, FK simple y compuesta, índices únicos/no únicos/de expresión, tablas con miles de filas y una tabla sin PK. **No toca ninguna otra tabla.** Si ya están cargados no hace nada; con el argumento `--recargar` vacía sólo las `tp_*` y las recarga. Inserta con operaciones batch dentro de una transacción. |
| 2 | `2-VerificarTP` → `verificacion.VerificarTP` | Recorre, contra tu base, cada requisito del TP (1 a 17) y marca `[ OK ]`, `[FALLA]` o `[AVISO]` con la evidencia. |
| 3 | `3-PruebaEstres` → `verificacion.PruebaEstres` | Prueba de estrés paso a paso (1 → 5 → 10 → 25 → 50 hilos). Argumentos: `<hilos por paso> <iteraciones>`, p. ej. `50,100,200 50`. |
| 4 | `DatabaseInspector` → `App` | El inspector interactivo. |

Desde PowerShell:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.main-class=py.edu.ucsa.jdbc.tx.demo.PrepararDatos"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.main-class=py.edu.ucsa.jdbc.tx.verificacion.VerificarTP"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.main-class=py.edu.ucsa.jdbc.tx.verificacion.PruebaEstres"
```

Resultado esperado de `VerificarTP` después de `PrepararDatos`:

```
 RESULTADO: 18 OK, 0 FALLAS, 0 AVISOS -> EL TRABAJO PRÁCTICO FUNCIONA
```

### 4. Menú de ejemplos JDBC (`ejemplos.Main`)

En STS: `4-MenuEjemplos.launch` → *Run As → 4-MenuEjemplos* (o clic derecho en `ejemplos/Main.java` → *Run As → Java Application*).
Se elige un número en la consola; con argumentos se ejecuta directo (p. ej. `2`, `1 3` o `9`).

| Opción | Clase | Qué muestra |
|---|---|---|
| 1 | `EjemploCrud` | INSERT con ID generado, SELECT por ID y de todos, UPDATE (incluye NULL y `byte[]`), DELETE, casos de ID inexistente |
| 2 | `EjemploTransaccion` | `TransferenciaService`: transferencia OK (**commit**), saldo insuficiente (**rollback**), destino inexistente a mitad de camino (**rollback** deshace el débito), validación previa; muestra saldos y movimientos (JOIN) |
| 3 | `EjemploConsultas` | `COUNT`, `AVG`, `LIKE`, `BETWEEN` con fechas, paginación `LIMIT/OFFSET`, `JOIN` + `GROUP BY` |
| 4 | `EjemploBatch` | 500 INSERT uno por uno vs en **batch** (con tiempos) y DELETE en batch; la tabla queda como estaba |
| 5 | `EjemploInyeccionSql` | Statement concatenado vs `PreparedStatement` con `x' OR '1'='1` y `O'Higgins` |
| 6 | `EjemploAvanzado` | **Savepoint** (deshacer sólo una parte), **niveles de aislamiento** con dos conexiones (READ_COMMITTED y REPEATABLE_READ), **ResultSet desplazable** (`last`, `absolute`, `previous`, `first`, `relative`) y **CallableStatement** (función de PostgreSQL) |
| 9 | — | Todos en orden |

Cada paso imprime líneas `[i]` que explican qué hace y qué método de JDBC usa.
La opción 3 incluye además el **estado de cuenta** de un funcionario (JOIN funcionarios + cuenta + movimientos).

**Verificación automática de los ejemplos:** `5-VerificarEjemplos.launch` (clase `verificacion.VerificarEjemplos`)
comprueba con datos reales cada ejemplo (31 verificaciones: CRUD, commit/rollback, consultas, batch, inyección SQL,
savepoint, aislamiento, ResultSet desplazable y CallableStatement). Resultado esperado:

```
 RESULTADO: 31 OK, 0 FALLAS -> TODOS LOS EJEMPLOS FUNCIONAN
```

Los DAO están en `dao/FuncionarioDao.java` y `dao/CuentaDao.java`.

### 5. Tests automáticos

```bash
mvn test                                  # H2 en memoria, no necesita PostgreSQL
mvn test -Dspring.profiles.active=pg      # contra PostgreSQL (requiere el paso 1)
```

### 6. Prueba de estrés con Maven

Ejecuta la inspección completa de las tablas (metadatos + consulta) con cantidades
crecientes de hilos concurrentes. Falla si hay algún error o si quedan conexiones sin
devolver al pool.

```bash
mvn test -Pstress                                        # H2
mvn test -Pstress -Dspring.profiles.active=pg            # PostgreSQL
mvn test -Pstress -Dspring.profiles.active=pg \
    -Dstress.stages=50,100,200 -Dstress.iterations=100   # carga alta
```

Ejemplo de salida (PostgreSQL 16, pool de 10 conexiones):

```
Paso 1/5:   1 hilos ->     40 ops, 0 errores,  101.8 ops/s, p95  23.24 ms, conexiones activas al final: 0
Paso 3/5:  10 hilos ->    400 ops, 0 errores,  703.0 ops/s, p95  34.65 ms, conexiones activas al final: 0
Paso 5/5:  50 hilos ->   2000 ops, 0 errores, 1160.1 ops/s, p95  89.28 ms, conexiones activas al final: 0
Paso 3/3: 200 hilos ->  20000 ops, 0 errores, 1860.6 ops/s, p95 275.60 ms, conexiones activas al final: 0
```
