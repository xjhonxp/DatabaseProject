package pe.openstrategy.databaseproject.domain;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum DdlObjectType {
    TABLE,
    VIEW,
    INDEX,
    PROCEDURE,
    FUNCTION,
    SEQUENCE;

    private static final Map<String, DdlObjectType> LOOKUP = Stream.of(values())
        .collect(Collectors.toMap(
            type -> type.name().toLowerCase(),
            Function.identity()
        ));

    public static DdlObjectType fromString(String objectType) {
        if (objectType == null) {
            throw new IllegalArgumentException("Object type cannot be null");
        }
        DdlObjectType type = LOOKUP.get(objectType.toLowerCase().trim());
        if (type == null) {
            throw new IllegalArgumentException("Unsupported DDL object type: " + objectType);
        }
        return type;
    }
}