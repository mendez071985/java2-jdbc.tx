package py.edu.ucsa.jdbc.tx.inspector.model;

import java.util.List;

/** Índice agrupado: las columnas vienen ordenadas por ORDINAL_POSITION. */
public record IndexInfo(String name, boolean unique, String type, List<String> columns) {
}
