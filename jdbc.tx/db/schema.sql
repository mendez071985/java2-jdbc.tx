-- Base de ejemplo para probar el Database Inspector (PostgreSQL).
-- Uso: psql -h localhost -p 5435 -U postgres -d ucsajava -f db/schema.sql

DROP TABLE IF EXISTS "Log Eventos", asignacion_proyecto, proyecto, movimiento, cuenta_funcionario, funcionarios, departamento CASCADE;

CREATE TABLE departamento (
    id          SERIAL PRIMARY KEY,
    nombre      VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE funcionarios (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre            VARCHAR(60)  NOT NULL,
    apellido          VARCHAR(60)  NOT NULL,
    edad              INTEGER,
    fecha_nacimiento  DATE         NOT NULL,
    fecha_ingreso     DATE         NOT NULL,
    foto              BYTEA,
    fecha_ult_modif   TIMESTAMP    NOT NULL DEFAULT now(),
    legajo            INTEGER      NOT NULL UNIQUE,
    departamento_id   INTEGER      REFERENCES departamento(id) ON DELETE SET NULL
);
CREATE INDEX idx_funcionarios_apellido_nombre ON funcionarios (apellido, nombre);

CREATE TABLE cuenta_funcionario (
    id              BIGSERIAL PRIMARY KEY,
    funcionario_id  BIGINT NOT NULL UNIQUE REFERENCES funcionarios(id) ON DELETE CASCADE,
    saldo           NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (saldo >= 0)
);

CREATE TABLE movimiento (
    id              BIGSERIAL PRIMARY KEY,
    funcionario_id  BIGINT NOT NULL REFERENCES funcionarios(id),
    tipo            VARCHAR(10) NOT NULL,
    monto           NUMERIC(15,2) NOT NULL,
    fecha_hora      TIMESTAMP NOT NULL,
    descripcion     TEXT
);
CREATE INDEX idx_movimiento_funcionario_fecha ON movimiento (funcionario_id, fecha_hora DESC);

-- Tablas con clave primaria compuesta y FK compuesta de ejemplo
CREATE TABLE proyecto (
    codigo   VARCHAR(10),
    anio     INTEGER,
    titulo   VARCHAR(120) NOT NULL,
    PRIMARY KEY (codigo, anio)
);

CREATE TABLE asignacion_proyecto (
    funcionario_id   BIGINT      NOT NULL REFERENCES funcionarios(id),
    proyecto_codigo  VARCHAR(10) NOT NULL,
    proyecto_anio    INTEGER     NOT NULL,
    horas            NUMERIC(6,1),
    PRIMARY KEY (funcionario_id, proyecto_codigo, proyecto_anio),
    CONSTRAINT fk_asignacion_proyecto FOREIGN KEY (proyecto_codigo, proyecto_anio)
        REFERENCES proyecto (codigo, anio) ON UPDATE CASCADE
);

-- Tabla sin PK, FK ni índices y con un nombre que obliga a usar comillas
CREATE TABLE "Log Eventos" (
    "Mensaje" VARCHAR(200)
);

-- Datos
INSERT INTO departamento (nombre) VALUES ('Sistemas'), ('Contabilidad'), ('RRHH');

INSERT INTO funcionarios (nombre, apellido, edad, fecha_nacimiento, fecha_ingreso, legajo, departamento_id)
SELECT 'Nombre' || g, 'Apellido' || g, 20 + (g % 40),
       DATE '1980-01-01' + g * 200, DATE '2015-01-01' + g * 30, 1000 + g, 1 + (g % 3)
FROM generate_series(1, 25) g;
UPDATE funcionarios SET foto = '\xCAFEBABE'::bytea WHERE legajo = 1001;

INSERT INTO cuenta_funcionario (funcionario_id, saldo)
SELECT id, 1000000 FROM funcionarios;

INSERT INTO movimiento (funcionario_id, tipo, monto, fecha_hora, descripcion)
SELECT 1 + (g % 25), CASE WHEN g % 2 = 0 THEN 'DEBITO' ELSE 'CREDITO' END,
       g * 1000, now() - (g || ' hours')::interval, 'Movimiento de prueba ' || g
FROM generate_series(1, 50) g;

INSERT INTO proyecto VALUES ('INSP', 2026, 'Database Inspector'), ('TX', 2026, 'Transacciones JDBC');
INSERT INTO asignacion_proyecto VALUES (1, 'INSP', 2026, 40.5), (2, 'INSP', 2026, 12), (1, 'TX', 2026, 8);

INSERT INTO "Log Eventos" VALUES ('arranque');
