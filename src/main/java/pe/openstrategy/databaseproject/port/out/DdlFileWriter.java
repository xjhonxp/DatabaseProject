package pe.openstrategy.databaseproject.port.out;

import pe.openstrategy.databaseproject.domain.valueobject.DdlStatement;

import java.util.List;

/**
 * Port for writing DDL statements to files.
 */
public interface DdlFileWriter {
    
    void writeDdl(String basePath, List<DdlStatement> ddlStatements);
}