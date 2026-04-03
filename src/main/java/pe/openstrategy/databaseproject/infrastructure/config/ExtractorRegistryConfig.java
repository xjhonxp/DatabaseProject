package pe.openstrategy.databaseproject.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.openstrategy.databaseproject.domain.DatabaseType;
import pe.openstrategy.databaseproject.infrastructure.extractor.MariaDbDdlExtractor;
import pe.openstrategy.databaseproject.infrastructure.extractor.OracleDdlExtractor;
import pe.openstrategy.databaseproject.infrastructure.extractor.PostgresDdlExtractor;
import pe.openstrategy.databaseproject.infrastructure.extractor.SqlServerDdlExtractor;
import pe.openstrategy.databaseproject.port.out.DdlExtractor;

import java.util.Map;

/**
 * Registers a Map<DatabaseType, DdlExtractor> bean for use case lookup.
 */
@Configuration
@RequiredArgsConstructor
public class ExtractorRegistryConfig {

    @Bean
    public Map<DatabaseType, DdlExtractor> extractorRegistry(
            PostgresDdlExtractor postgres,
            OracleDdlExtractor oracle,
            SqlServerDdlExtractor sqlServer,
            MariaDbDdlExtractor mariaDb) {

        return Map.of(
            DatabaseType.POSTGRESQL, postgres,
            DatabaseType.ORACLE,     oracle,
            DatabaseType.SQL_SERVER, sqlServer,
            DatabaseType.AZURE_SQL,  sqlServer,   // shared implementation
            DatabaseType.MARIADB,    mariaDb
        );
    }
}