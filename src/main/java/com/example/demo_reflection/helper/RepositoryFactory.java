package com.example.demo_reflection.helper;

import com.example.demo_reflection.repositories.MyCrudRepository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RepositoryFactory {
    public <T> T createRepository(Class<?> repositoryInterface) {
        if (!MyCrudRepository.class.isAssignableFrom(repositoryInterface)) {
            throw new IllegalArgumentException("Interface must extend MyCrudRepository");
        }

        ParameterizedType genericSuperclass = (ParameterizedType) repositoryInterface.getGenericInterfaces()[0];
        Class<T> entityType = (Class<T>) genericSuperclass.getActualTypeArguments()[0];
        Class<?> idType = (Class<?>) genericSuperclass.getActualTypeArguments()[1];

        // Create a dynamic proxy for the repository
        return (T) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface},
                new RepositoryInvocationHandler<>(entityType, idType)
        );
    }

    class RepositoryInvocationHandler<T, ID> implements InvocationHandler {
        private final Class<T> entityType;
        private final Class<ID> idType;

        public RepositoryInvocationHandler(Class<T> entityType, Class<ID> idType) {
            this.entityType = entityType;
            this.idType = idType;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Reflection logic for CRUD operations
            if (method.getName().equals("save")) {
                return handleSave(args[0]);
            } else if (method.getName().equals("findById")) {
                return handleFindById(args[0]);
            } else if (method.getName().equals("findAll")) {
                return handleFindAll();
            } else if (method.getName().equals("deleteById")) {
                handleDeleteById(args[0]);
                return null;
            } else if (method.getName().equals("count")) {
                return handleCount();
            }
            return null;
        }

        private T handleSave(Object entity) throws SQLException {
            // Logic for saving entity: either INSERT or UPDATE
            String sql = generateInsertOrUpdateSQL(entity);
            try (Connection conn = DriverManager.getConnection("jdbc:your_database_url", "username", "password");
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            }
            return (T) entity;
        }

        private T handleFindById(Object id) throws SQLException {
            // Logic for finding by ID
            String sql = "SELECT * FROM " + entityType.getSimpleName() + " WHERE id = ?";
            try (Connection conn = DriverManager.getConnection("jdbc:your_database_url", "username", "password");
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, id);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return mapRowToEntity(rs);
                }
            }
            return null;
        }

        private List<T> handleFindAll() throws SQLException {
            // Logic for finding all records
            String sql = "SELECT * FROM " + entityType.getSimpleName();
            List<T> result = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection("jdbc:your_database_url", "username", "password");
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    result.add(mapRowToEntity(rs));
                }
            }
            return result;
        }

        private void handleDeleteById(Object id) throws SQLException {
            // Logic for deleting by ID
            String sql = "DELETE FROM " + entityType.getSimpleName() + " WHERE id = ?";
            try (Connection conn = DriverManager.getConnection("jdbc:your_database_url", "username", "password");
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, id);
                stmt.executeUpdate();
            }
        }

        private long handleCount() throws SQLException {
            // Logic for counting rows
            String sql = "SELECT COUNT(*) FROM " + entityType.getSimpleName();
            try (Connection conn = DriverManager.getConnection("jdbc:your_database_url", "username", "password");
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0;
        }

        private String generateInsertOrUpdateSQL(Object entity) {
            // Logic to generate INSERT or UPDATE SQL based on entity annotations and fields
            return "INSERT INTO " + entityType.getSimpleName() + " (fields...) VALUES (values...)";
        }

        private T mapRowToEntity(ResultSet rs) throws SQLException {
            // Map ResultSet to entity
            try {
                T entity = entityType.getDeclaredConstructor().newInstance();
                // Populate entity fields from ResultSet (based on @MyColumn annotations)
                return entity;
            } catch (Exception e) {
                throw new SQLException("Error mapping result to entity", e);
            }
        }
    }
}
