package pe.openstrategy.databaseproject.infrastructure.jdbc;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Sole class allowed to reference Connection, PreparedStatement, and ResultSet
 * directly.
 * Provides a simple template pattern for JDBC operations.
 */
@Slf4j
@Component
public class JdbcTemplate {

    /**
     * Functional interface for extracting a single result from a ResultSet.
     */
    @FunctionalInterface
    public interface ResultSetExtractor<T> {
        T extract(ResultSet rs) throws SQLException;
    }

    /**
     * Executes a query and returns a single result.
     *
     * @param connection the JDBC connection
     * @param sql        the SQL query
     * @param extractor  extracts the result from the ResultSet
     * @return the extracted result, or null if the query returns no rows
     * @throws SQLException if a database error occurs
     */
    public <T> T query(Connection connection, String sql, ResultSetExtractor<T> extractor) throws SQLException {
        return query(connection, sql, null, extractor);
    }

    /**
     * Executes a parameterized query and returns a single result.
     *
     * @param connection the JDBC connection
     * @param sql        the SQL query with placeholders (?)
     * @param params     the parameters to set on the prepared statement
     * @param extractor  extracts the result from the ResultSet
     * @return the extracted result, or null if the query returns no rows
     * @throws SQLException if a database error occurs
     */
    public <T> T query(Connection connection, String sql, List<Object> params, ResultSetExtractor<T> extractor)
            throws SQLException {
        log.debug("Executing query: {}", sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return extractor.extract(rs);
                }
                return null;
            }
        }
    }

    /**
     * Executes a query and returns a list of results.
     *
     * @param connection the JDBC connection
     * @param sql        the SQL query
     * @param extractor  extracts a single row from the ResultSet
     * @return a list of extracted results, empty if no rows
     * @throws SQLException if a database error occurs
     */
    public <T> List<T> queryForList(Connection connection, String sql, ResultSetExtractor<T> extractor)
            throws SQLException {
        return queryForList(connection, sql, null, extractor);
    }

    /**
     * Executes a parameterized query and returns a list of results.
     *
     * @param connection the JDBC connection
     * @param sql        the SQL query with placeholders (?)
     * @param params     the parameters to set on the prepared statement
     * @param extractor  extracts a single row from the ResultSet
     * @return a list of extracted results, empty if no rows
     * @throws SQLException if a database error occurs
     */
    public <T> List<T> queryForList(Connection connection, String sql, List<Object> params,
            ResultSetExtractor<T> extractor)
            throws SQLException {
        log.debug("Executing query for list: {}", sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(extractor.extract(rs));
                }
                return results;
            }
        }
    }

    /**
     * Executes an update (INSERT, UPDATE, DELETE) and returns the number of
     * affected rows.
     *
     * @param connection the JDBC connection
     * @param sql        the SQL update statement
     * @return the number of affected rows
     * @throws SQLException if a database error occurs
     */
    public int update(Connection connection, String sql) throws SQLException {
        return update(connection, sql, null);
    }

    /**
     * Executes a parameterized update (INSERT, UPDATE, DELETE) and returns the
     * number of affected rows.
     *
     * @param connection the JDBC connection
     * @param sql        the SQL update statement with placeholders (?)
     * @param params     the parameters to set on the prepared statement
     * @return the number of affected rows
     * @throws SQLException if a database error occurs
     */
    public int update(Connection connection, String sql, List<Object> params) throws SQLException {
        log.debug("Executing update: {}", sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            return ps.executeUpdate();
        }
    }
}