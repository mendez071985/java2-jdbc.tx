INSERT INTO departamento (nombre) VALUES ('Sistemas'), ('Contabilidad'), ('RRHH');

INSERT INTO funcionarios (nombre, apellido, edad, fecha_nacimiento, fecha_ingreso, foto, legajo, departamento_id)
SELECT 'Nombre' || n, 'Apellido' || n, 20 + MOD(n, 40),
       DATEADD('DAY', n * 200, DATE '1980-01-01'), DATEADD('DAY', n * 30, DATE '2015-01-01'),
       CASE WHEN n = 1 THEN X'CAFEBABE' ELSE NULL END,
       1000 + n, 1 + MOD(n, 3)
FROM (SELECT "X" AS n FROM SYSTEM_RANGE(1, 25)) t;

INSERT INTO movimiento (funcionario_id, tipo, monto, fecha_hora, descripcion)
SELECT 1 + MOD(n, 25), 'CREDITO', n * 1000, CURRENT_TIMESTAMP, 'Movimiento ' || n
FROM (SELECT "X" AS n FROM SYSTEM_RANGE(1, 5)) t;

INSERT INTO proyecto VALUES ('INSP', 2026, 'Database Inspector');
INSERT INTO asignacion_proyecto VALUES (1, 'INSP', 2026, 40.5);

INSERT INTO "Log Eventos" VALUES ('arranque');
