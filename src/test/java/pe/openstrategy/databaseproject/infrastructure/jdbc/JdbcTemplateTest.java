package pe.openstrategy.databaseproject.infrastructure.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JdbcTemplate}.
 */
@ExtendWith(MockitoExtension.class)
class JdbcTemplateTest {

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate();
    }

    @Test
    @DisplayName("query should execute SQL and extract result using ResultSetExtractor")
    void query_shouldExecuteSqlAndExtractResult() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString("name")).thenReturn("test");

        JdbcTemplate.ResultSetExtractor<String> extractor = rs -> rs.getString("name");
        String result = jdbcTemplate.query(connection, "SELECT name FROM test", extractor);

        assertEquals("test", result);
        verify(preparedStatement).executeQuery();
        verify(resultSet).next();
        verify(resultSet).getString("name");
    }

    @Test
    @DisplayName("query should handle SQLException and rethrow it")
    void query_whenSqlExceptionOccurs_shouldRethrow() throws SQLException {
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Connection error"));

        JdbcTemplate.ResultSetExtractor<String> extractor = rs -> null;
        assertThrows(SQLException.class, () ->
                jdbcTemplate.query(connection, "SELECT 1", extractor));
    }

    @Test
    @DisplayName("query with parameters should bind parameters and extract result")
    void query_withParameters_shouldBindParametersAndExtractResult() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt("id")).thenReturn(42);

        JdbcTemplate.ResultSetExtractor<Integer> extractor = rs -> rs.getInt("id");
        List<Object> params = List.of("param1", 100);
        Integer result = jdbcTemplate.query(connection, "SELECT id FROM test WHERE name = ? AND value = ?",
                params, extractor);

        assertEquals(42, result);
        verify(preparedStatement).setObject(1, "param1");
        verify(preparedStatement).setObject(2, 100);
        verify(preparedStatement).executeQuery();
    }

    @Test
    @DisplayName("queryForList should return list of extracted objects")
    void queryForList_shouldReturnListOfExtractedObjects() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("name")).thenReturn("first", "second");

        JdbcTemplate.ResultSetExtractor<String> extractor = rs -> rs.getString("name");
        List<String> results = jdbcTemplate.queryForList(connection, "SELECT name FROM test", extractor);

        assertEquals(List.of("first", "second"), results);
        verify(preparedStatement).executeQuery();
        verify(resultSet, times(3)).next();
        verify(resultSet, times(2)).getString("name");
    }

    @Test
    @DisplayName("queryForList with parameters should bind parameters and return list")
    void queryForList_withParameters_shouldBindParametersAndReturnList() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getInt("count")).thenReturn(5);

        JdbcTemplate.ResultSetExtractor<Integer> extractor = rs -> rs.getInt("count");
        List<Integer> results = jdbcTemplate.queryForList(connection,
                "SELECT count FROM test WHERE active = ?", List.of(true), extractor);

        assertEquals(List.of(5), results);
        verify(preparedStatement).setObject(1, true);
        verify(preparedStatement).executeQuery();
    }

    @Test
    @DisplayName("update should execute update statement and return affected rows")
    void update_shouldExecuteUpdateAndReturnAffectedRows() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(3);

        int affected = jdbcTemplate.update(connection, "UPDATE test SET value = 1");

        assertEquals(3, affected);
        verify(preparedStatement).executeUpdate();
    }

    @Test
    @DisplayName("update with parameters should bind parameters and return affected rows")
    void update_withParameters_shouldBindParametersAndReturnAffectedRows() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        int affected = jdbcTemplate.update(connection, "UPDATE test SET value = ? WHERE id = ?",
                List.of("newValue", 10));

        assertEquals(1, affected);
        verify(preparedStatement).setObject(1, "newValue");
        verify(preparedStatement).setObject(2, 10);
        verify(preparedStatement).executeUpdate();
    }

    @Test
    @DisplayName("update should propagate SQLException")
    void update_whenSqlExceptionOccurs_shouldRethrow() throws SQLException {
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Update error"));

        assertThrows(SQLException.class, () ->
                jdbcTemplate.update(connection, "UPDATE test SET x = 1"));
    }

    @Test
    @DisplayName("query with empty result set should return null")
    void query_whenResultSetEmpty_shouldReturnNull() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        JdbcTemplate.ResultSetExtractor<String> extractor = rs -> rs.getString("name");
        String result = jdbcTemplate.query(connection, "SELECT name FROM test WHERE false", extractor);

        assertNull(result);
        verify(preparedStatement).executeQuery();
        verify(resultSet, never()).getString(anyString());
    }

    @Test
    @DisplayName("queryForList with empty result set should return empty list")
    void queryForList_whenResultSetEmpty_shouldReturnEmptyList() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        JdbcTemplate.ResultSetExtractor<String> extractor = rs -> rs.getString("name");
        List<String> results = jdbcTemplate.queryForList(connection, "SELECT name FROM test WHERE false", extractor);

        assertTrue(results.isEmpty());
        verify(preparedStatement).executeQuery();
    }
}