package com.example.demo_reflection.helper;

import com.example.demo_reflection.annotations.*;
import com.example.demo_reflection.repositories.MyCrudRepository;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RepositoryFactory {

    private final Properties dbConfig; // Biến lưu trữ cấu hình DB

    /**
     * Constructor mặc định: Tự động đọc cấu hình từ file db.properties trong classpath.
     */
    public RepositoryFactory() {
        this.dbConfig = loadProperties();
    }

    /**
     * Constructor cho phép truyền cấu hình từ bên ngoài.
     * @param dbConfig Đối tượng Properties chứa cấu hình DB.
     */
    public RepositoryFactory(Properties dbConfig) {
        if (dbConfig == null) {
            throw new IllegalArgumentException("Database configuration cannot be null");
        }
        this.dbConfig = dbConfig;
    }

    /**
     * Phương thức helper để đọc file properties.
     * @return Properties đã đọc được, hoặc Properties rỗng nếu không tìm thấy file/lỗi.
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        // Sử dụng try-with-resources để đảm bảo InputStream được đóng
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            if (input == null) {
                System.err.println("Sorry, unable to find db.properties in classpath.");
                // Có thể ném lỗi ở đây thay vì trả về props rỗng nếu file config là bắt buộc
                // throw new RuntimeException("db.properties not found in classpath");
                return props; // Trả về rỗng nếu không tìm thấy
            }
            // Load properties từ input stream
            props.load(input);
        } catch (IOException ex) {
            System.err.println("Error reading db.properties file: " + ex.getMessage());
            // Ghi log lỗi hoặc ném ngoại lệ runtime
            // throw new RuntimeException("Error reading db.properties", ex);
        }
        return props;
    }

    /**
     * Tạo repository instance.
     * Đã được cập nhật để truyền dbConfig vào InvocationHandler.
     */
    public <RepoInterface> RepoInterface createRepository(Class<RepoInterface> repositoryInterface) {
        // --- Phần kiểm tra và lấy generic type giữ nguyên như cũ ---
        if (!MyCrudRepository.class.isAssignableFrom(repositoryInterface)) {
            throw new IllegalArgumentException("Interface " + repositoryInterface.getName() + " must extend MyCrudRepository");
        }

        Type[] genericInterfaces = repositoryInterface.getGenericInterfaces();
        ParameterizedType crudRepositoryType = null;
        for (Type type : genericInterfaces) {
            if (type instanceof ParameterizedType) {
                ParameterizedType pType = (ParameterizedType) type;
                // Kiểm tra xem Raw Type có phải là MyCrudRepository không
                if (MyCrudRepository.class.equals(pType.getRawType())) {
                    crudRepositoryType = pType;
                    break;
                }
            }
        }

        if (crudRepositoryType == null) {
            // Cũng cần kiểm tra các interface cha của interface cha... (phức tạp hơn)
            // Hoặc yêu cầu MyCrudRepository phải là interface *trực tiếp*
            throw new IllegalStateException("Cannot determine generic types <T, ID> from MyCrudRepository for interface: " + repositoryInterface.getName());
        }


        Type[] actualTypeArguments = crudRepositoryType.getActualTypeArguments();
        if (actualTypeArguments.length < 2) {
            throw new IllegalStateException("Cannot determine generic types <T, ID> from MyCrudRepository for interface: " + repositoryInterface.getName());
        }

        Class<?> entityType = (Class<?>) actualTypeArguments[0];
        Class<?> idType = (Class<?>) actualTypeArguments[1];
        // --- Hết phần lấy generic type ---


        // Tạo proxy và truyền dbConfig vào InvocationHandler
        Object proxyInstance = Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface},
                new RepositoryInvocationHandler<>(entityType, idType, this.dbConfig) // Truyền dbConfig vào đây
        );

        return repositoryInterface.cast(proxyInstance); // Sử dụng cast để an toàn kiểu hơn
    }


    // --- InvocationHandler giờ đây nhận và sử dụng dbConfig ---
    static class RepositoryInvocationHandler<T, ID> implements InvocationHandler {
        private final Class<T> entityType;
        private final Class<?> idType; // ID type có thể là bất kỳ, dùng Class<?>
        private final Properties dbConfig; // Thêm biến lưu cấu hình

        // Constructor nhận thêm dbConfig
        public RepositoryInvocationHandler(Class<T> entityType, Class<?> idType, Properties dbConfig) {
            this.entityType = entityType;
            this.idType = idType;
            this.dbConfig = dbConfig; // Lưu cấu hình
            validateConfig(); // Kiểm tra config ngay khi tạo handler
        }

        private void validateConfig() {
            if (dbConfig == null || dbConfig.getProperty("db.url") == null ||
                    dbConfig.getProperty("db.username") == null || dbConfig.getProperty("db.password") == null) {
                throw new IllegalStateException("Database configuration (URL, username, password) is missing or incomplete. Ensure db.properties is loaded correctly or configuration is passed.");
            }
            // Không cần kiểm tra driver vì Class.forName sẽ báo lỗi nếu driver sai/thiếu
        }


        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // --- Phần định tuyến (save, findById,...) giữ nguyên ---
            String methodName = method.getName();

            try { // Bọc các xử lý trong try-catch để bắt SQLException và ReflectionException tốt hơn
                if (methodName.equals("save")) {
                    return handleSave(args[0]);
                } else if (methodName.equals("findById")) {
                    return handleFindById(args[0]);
                } else if (methodName.equals("findAll")) {
                    return handleFindAll();
                } else if (methodName.equals("deleteById")) {
                    handleDeleteById(args[0]);
                    return null;
                } else if (methodName.equals("count")) {
                    return handleCount();
                } else if (method.isDefault()) { // Hỗ trợ default method (Java 8+)
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }

                // Xử lý các phương thức của Object (toString, hashCode, equals)
                if (method.getDeclaringClass() == Object.class) {
                    if (methodName.equals("toString")) {
                        return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy)) +
                                ", with InvocationHandler " + this;
                    } else {
                        // equals và hashCode nên dựa vào proxy instance
                        return method.invoke(this, args); // Hoặc xử lý khác nếu cần
                    }
                }

            } catch (InvocationTargetException e) {
                // Unwrap exception gốc nếu là InvocationTargetException từ Reflection
                throw e.getTargetException();
            } catch (SQLException | ReflectiveOperationException e) {
                // Log lỗi và/hoặc ném một exception cụ thể của ứng dụng
                System.err.println("Error executing repository method '" + methodName + "': " + e.getMessage());
                // Có thể ném một RuntimeException hoặc một custom exception
                throw new RuntimeException("Repository operation failed: " + methodName, e);
            } catch (Throwable t) {
                System.err.println("Unexpected error in repository method '" + methodName + "': " + t.getMessage());
                throw t; // Ném lại lỗi không mong muốn
            }

            throw new UnsupportedOperationException("Method not supported by MyCrudRepository proxy: " + method);
        }

        // --- Hàm kết nối CSDL sử dụng dbConfig ---
        private Connection getConnection() throws SQLException {
            // Lấy thông tin từ Properties
            String jdbcUrl = dbConfig.getProperty("db.url");
            String username = dbConfig.getProperty("db.username");
            String password = dbConfig.getProperty("db.password");
            String driverClassName = dbConfig.getProperty("db.driver");

            // 1. (Tùy chọn nhưng an toàn) Đăng ký driver nếu cần thiết
            // JDBC 4+ thường tự động đăng ký driver nếu JAR có trong classpath.
            // Tuy nhiên, Class.forName đảm bảo driver được load.
            if (driverClassName != null && !driverClassName.trim().isEmpty()) {
                try {
                    Class.forName(driverClassName);
                } catch (ClassNotFoundException e) {
                    throw new SQLException("Failed to load JDBC driver: " + driverClassName, e);
                }
            }

            // 2. Lấy kết nối
            // !!! Chú ý: Cách này tạo kết nối mới mỗi lần gọi.
            // !!! Trong ứng dụng thực tế, NÊN SỬ DỤNG CONNECTION POOL (ví dụ HikariCP)
            // !!! để quản lý kết nối hiệu quả hơn.
            System.out.println("Connecting to DB: " + jdbcUrl); // Log để debug
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        // --- Các phương thức handleSave, handleFindById, handleFindAll, handleDeleteById, handleCount ---
        // --- Giữ nguyên logic xử lý SQL và Reflection như trước, nhưng giờ chúng sẽ gọi getConnection() đã được cập nhật ---
        // --- Chúng ta cần hoàn thiện chúng để đọc annotation và sinh SQL đúng ---

        // --- Ví dụ cập nhật handleFindAll để dùng getConnection mới ---
        private List<T> handleFindAll() throws SQLException {
            String tableName = getTableName();
            String sql = "SELECT * FROM " + tableName;
            System.out.println("Executing SQL (FindAll): " + sql);
            List<T> result = new ArrayList<>();
            // Sử dụng try-with-resources cho Connection, Statement, ResultSet
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    result.add(mapRowToEntity(rs)); // mapRowToEntity cần được hoàn thiện
                }
            } // Connection, Statement, ResultSet sẽ tự động đóng ở đây
            catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            return result;
        }

        // --- handleSave cần được hoàn thiện logic INSERT/UPDATE và set tham số ---
        private T handleSave(Object entity) throws SQLException, ReflectiveOperationException {
            if (!entityType.isInstance(entity)) {
                throw new IllegalArgumentException("Object to save is not an instance of " + entityType.getName());
            }

            Object idValue = getIdValue(entity);
            boolean isInsert = (idValue == null); // Hoặc kiểm tra giá trị mặc định nếu ID là kiểu nguyên thủy

            String sql = generateInsertOrUpdateSQL(entity, isInsert);
            System.out.println("Executing SQL (Save): " + sql + " for entity: " + entity);

            try (Connection conn = getConnection();
                 // Sử dụng RETURN_GENERATED_KEYS nếu là INSERT và cần lấy ID tự tăng
                 PreparedStatement stmt = conn.prepareStatement(sql, isInsert ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {

                // *** QUAN TRỌNG: Set tham số cho PreparedStatement ***
                setParametersForSave(stmt, entity, isInsert);

                int affectedRows = stmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new SQLException("Saving entity failed, no rows affected.");
                }

                // Nếu là INSERT và có ID tự tăng, lấy lại ID gán vào entity
                if (isInsert) {
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            Object generatedId = generatedKeys.getObject(1); // Lấy ID từ cột đầu tiên
                            setIdValue(entity, generatedId); // Gán ID mới vào entity
                        } else {
                            // Có thể không phải mọi DB/bảng đều trả về key, hoặc ID không tự tăng
                            System.out.println("No generated ID obtained for INSERT.");
                        }
                    }
                }
            }
            return (T) entity;
        }

        // --- handleFindById ---
        private Optional<T> handleFindById(Object id) throws SQLException, ReflectiveOperationException {
            String tableName = getTableName();
            String idColumnName = getIdColumnName();
            String sql = "SELECT * FROM " + tableName + " WHERE " + idColumnName + " = ?";
            System.out.println("Executing SQL (FindById): " + sql + " with ID: " + id);

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Nếu tìm thấy, map và gói vào Optional
                        return Optional.of(mapRowToEntity(rs));
                    }
                }
            }
            // Nếu không tìm thấy hoặc có lỗi, trả về Optional rỗng
            return Optional.empty();
        }

        // --- handleDeleteById ---
        private void handleDeleteById(Object id) throws SQLException {
            String tableName = getTableName();
            String idColumnName = getIdColumnName();
            String sql = "DELETE FROM " + tableName + " WHERE " + idColumnName + " = ?";
            System.out.println("Executing SQL (DeleteById): " + sql + " with ID: " + id);
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, id);
                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    System.out.println("No rows deleted for ID: " + id + " in table " + tableName);
                    // Có thể ném lỗi hoặc không tùy yêu cầu
                }
            }
        }

        // --- handleCount ---
        private long handleCount() throws SQLException {
            String tableName = getTableName();
            String sql = "SELECT COUNT(*) FROM " + tableName;
            System.out.println("Executing SQL (Count): " + sql);
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0;
        }


        // --- Các hàm helper getTableName, getIdColumnName, getIdValue giữ nguyên ---
        // --- Cần bổ sung/hoàn thiện các hàm helper khác ---

        private String getTableName() {
            // Ưu tiên @MyTable trước
            if (entityType.isAnnotationPresent(MyTable.class)) {
                MyTable tableAnnotation = entityType.getAnnotation(MyTable.class);
                // Tên trong MyTable là bắt buộc nên không cần check rỗng
                return tableAnnotation.name();
            }
            if (entityType.isAnnotationPresent(MyEntity.class)) {
                MyEntity entityAnnotation = entityType.getAnnotation(MyEntity.class);
                if (!entityAnnotation.tableName().isEmpty()) {
                    return entityAnnotation.tableName();
                }
            }
            // Logic mặc định: tên lớp viết thường + 's'
            return entityType.getSimpleName().toLowerCase() + "s";
        }

        private Field getIdField() {
            for (Field field : entityType.getDeclaredFields()) {
                if (field.isAnnotationPresent(MyId.class)) {
                    return field;
                }
            }
            throw new IllegalStateException("No @MyId field found in entity: " + entityType.getName());
        }

        private String getIdColumnName() {
            Field idField = getIdField();
            if (idField.isAnnotationPresent(MyColumn.class)) {
                MyColumn columnAnnotation = idField.getAnnotation(MyColumn.class);
                if (!columnAnnotation.name().isEmpty()) {
                    return columnAnnotation.name();
                }
            }
            return idField.getName(); // Mặc định dùng tên trường
        }

        private Object getIdValue(Object entity) throws ReflectiveOperationException {
            Field idField = getIdField();
            idField.setAccessible(true); // Cho phép truy cập private field
            return idField.get(entity);
        }

        // Helper để gán giá trị ID (cho generated keys)
        private void setIdValue(Object entity, Object idValue) throws ReflectiveOperationException {
            Field idField = getIdField();
            idField.setAccessible(true);
            // Cần chuyển đổi kiểu nếu generated key trả về kiểu khác (ví dụ BigInteger -> Long)
            Object convertedId = convertIdType(idValue, idField.getType());
            idField.set(entity, convertedId);
        }

        // Helper chuyển đổi kiểu ID (cơ bản)
        private Object convertIdType(Object sourceId, Class<?> targetType) {
            if (sourceId == null) return null;
            if (targetType.isAssignableFrom(sourceId.getClass())) {
                return sourceId; // Cùng kiểu hoặc kiểu con
            }
            // Chuyển đổi cơ bản từ số (thường là Long hoặc BigInteger từ generatedKeys)
            if (sourceId instanceof Number) {
                Number numId = (Number) sourceId;
                if (targetType == Long.class || targetType == long.class) {
                    return numId.longValue();
                } else if (targetType == Integer.class || targetType == int.class) {
                    return numId.intValue();
                }
                // Thêm các kiểu khác nếu cần
            }
            // Nếu không chuyển đổi được, trả về gốc hoặc ném lỗi
            System.err.println("Warning: Cannot convert generated ID type " + sourceId.getClass().getName() + " to target field type " + targetType.getName());
            return sourceId;
        }


        // --- generateInsertOrUpdateSQL cần hoàn thiện ---
        private String generateInsertOrUpdateSQL(Object entity, boolean isInsert) {
            String tableName = getTableName();
            List<Field> relevantFields = getMappedFields(false); // Lấy các trường cần map (không transient, không ID)

            if (isInsert) {
                // INSERT INTO table (col1, col2, col3) VALUES (?, ?, ?)
                String columns = relevantFields.stream()
                        .map(this::getColumnName)
                        .collect(Collectors.joining(", "));
                String placeholders = IntStream.range(0, relevantFields.size())
                        .mapToObj(i -> "?")
                        .collect(Collectors.joining(", "));
                return "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
            } else {
                // UPDATE table SET col1 = ?, col2 = ? WHERE idCol = ?
                String setClause = relevantFields.stream()
                        .map(field -> getColumnName(field) + " = ?")
                        .collect(Collectors.joining(", "));
                String idColumn = getIdColumnName();
                return "UPDATE " + tableName + " SET " + setClause + " WHERE " + idColumn + " = ?";
            }
        }

        // --- setParametersForSave cần hoàn thiện ---
        private void setParametersForSave(PreparedStatement stmt, Object entity, boolean isInsert) throws SQLException, ReflectiveOperationException {
            List<Field> relevantFields = getMappedFields(false); // Lấy các trường cần map (không ID)
            int paramIndex = 1;
            for (Field field : relevantFields) {
                field.setAccessible(true);
                Object value = field.get(entity);
                stmt.setObject(paramIndex++, value);
            }

            if (!isInsert) { // Nếu là UPDATE, set tham số ID ở cuối
                Object idValue = getIdValue(entity);
                stmt.setObject(paramIndex, idValue);
            }
        }


        // --- mapRowToEntity cần hoàn thiện ---
        private T mapRowToEntity(ResultSet rs) throws SQLException, ReflectiveOperationException {
            T entity = entityType.getDeclaredConstructor().newInstance();
            List<Field> allFields = getMappedFields(true); // Lấy tất cả các trường cần map (bao gồm cả ID)

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            // Tạo một map từ tên cột (viết thường) sang index để tìm nhanh hơn
            java.util.Map<String, Integer> columnMap = new java.util.HashMap<>();
            for(int i = 1; i <= columnCount; i++) {
                columnMap.put(metaData.getColumnLabel(i).toLowerCase(), i);
            }


            for (Field field : allFields) {
                String columnName = getColumnName(field);

                // Kiểm tra xem cột có tồn tại trong ResultSet không (không phân biệt hoa thường)
                Integer columnIndex = columnMap.get(columnName.toLowerCase());

                if (columnIndex != null) {
                    // Lấy giá trị từ ResultSet bằng index (hiệu quả hơn bằng tên)
                    Object value = rs.getObject(columnIndex);

                    // Xử lý giá trị null cho kiểu nguyên thủy (nếu cần)
                    if (value == null && field.getType().isPrimitive()) {
                        // Bỏ qua hoặc gán giá trị mặc định (0, false, ...)
                        // Ví dụ: if (field.getType() == int.class) field.set(entity, 0);
                        System.err.println("Warning: Assigning null from DB column '" + columnName + "' to primitive field '" + field.getName() + "'. Skipping.");
                        continue; // Bỏ qua gán null cho kiểu nguyên thủy
                    }

                    // Chuyển đổi kiểu nếu cần (ví dụ từ java.sql.Timestamp sang java.time.LocalDateTime)
                    Object convertedValue = convertToFieldType(value, field.getType());

                    field.setAccessible(true); // Cho phép truy cập private field
                    field.set(entity, convertedValue); // Gán giá trị đã chuyển đổi
                } else {
                    // Cột không có trong ResultSet, có thể ghi log cảnh báo
                    System.err.println("Warning: Column '" + columnName + "' (for field '" + field.getName() + "') not found in ResultSet.");
                }
            }
            return entity;
        }


        // Helper lấy tên cột từ field (dựa vào @MyColumn hoặc tên field)
        private String getColumnName(Field field) {
            if (field.isAnnotationPresent(MyColumn.class)) {
                MyColumn columnAnnotation = field.getAnnotation(MyColumn.class);
                if (!columnAnnotation.name().isEmpty()) {
                    return columnAnnotation.name();
                }
            }
            return field.getName(); // Mặc định dùng tên trường
        }

        // Helper lấy danh sách các field cần map (có thể loại trừ ID)
        private List<Field> getMappedFields(boolean includeId) {
            List<Field> fields = new ArrayList<>();
            for (Field field : entityType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(MyTransient.class)) {
                    continue; // Bỏ qua static và transient
                }
                if (!includeId && field.isAnnotationPresent(MyId.class)) {
                    continue; // Bỏ qua ID nếu includeId = false
                }
                // Có thể thêm kiểm tra @MyColumn nếu muốn chỉ map các trường có @MyColumn rõ ràng
                // if (!field.isAnnotationPresent(MyColumn.class) && !field.isAnnotationPresent(MyId.class)) continue;
                fields.add(field);
            }
            return fields;
        }

        // Helper chuyển đổi kiểu dữ liệu từ JDBC sang Java (cơ bản)
        private Object convertToFieldType(Object dbValue, Class<?> fieldType) {
            if (dbValue == null) {
                return null;
            }
            // Nếu kiểu đã khớp thì trả về luôn
            if (fieldType.isAssignableFrom(dbValue.getClass())) {
                return dbValue;
            }

            // Ví dụ chuyển đổi: java.sql.Timestamp -> java.time.LocalDateTime
            if (dbValue instanceof java.sql.Timestamp && fieldType == java.time.LocalDateTime.class) {
                return ((java.sql.Timestamp) dbValue).toLocalDateTime();
            }
            // Ví dụ chuyển đổi: java.sql.Date -> java.time.LocalDate
            if (dbValue instanceof java.sql.Date && fieldType == java.time.LocalDate.class) {
                return ((java.sql.Date) dbValue).toLocalDate();
            }
            // Ví dụ chuyển đổi: java.sql.Time -> java.time.LocalTime
            if (dbValue instanceof java.sql.Time && fieldType == java.time.LocalTime.class) {
                return ((java.sql.Time) dbValue).toLocalTime();
            }

            // Chuyển đổi số (ví dụ: DB trả về BigDecimal nhưng field là Integer/Long)
            if (dbValue instanceof Number) {
                Number numValue = (Number) dbValue;
                if (fieldType == Integer.class || fieldType == int.class) {
                    return numValue.intValue();
                } else if (fieldType == Long.class || fieldType == long.class) {
                    return numValue.longValue();
                } else if (fieldType == Double.class || fieldType == double.class) {
                    return numValue.doubleValue();
                } else if (fieldType == Float.class || fieldType == float.class) {
                    return numValue.floatValue();
                } else if (fieldType == Short.class || fieldType == short.class) {
                    return numValue.shortValue();
                } else if (fieldType == Byte.class || fieldType == byte.class) {
                    return numValue.byteValue();
                } else if (fieldType == java.math.BigDecimal.class && dbValue instanceof java.math.BigDecimal) {
                    return dbValue; // Đã là BigDecimal
                } else if (fieldType == java.math.BigInteger.class && dbValue instanceof java.math.BigInteger) {
                    return dbValue; // Đã là BigInteger
                } else if (fieldType == java.math.BigDecimal.class) {
                    return new java.math.BigDecimal(numValue.toString()); // Chuyển từ Number khác sang BigDecimal
                } else if (fieldType == java.math.BigInteger.class && numValue instanceof java.math.BigDecimal) {
                    return ((java.math.BigDecimal)numValue).toBigInteger(); // Từ BigDecimal sang BigInteger
                } else if (fieldType == java.math.BigInteger.class) {
                    return java.math.BigInteger.valueOf(numValue.longValue()); // Từ Number khác sang BigInteger
                }
            }

            // Chuyển đổi Boolean (DB có thể là số 0/1 hoặc boolean)
            if ((fieldType == Boolean.class || fieldType == boolean.class)) {
                if (dbValue instanceof Boolean) return dbValue;
                if (dbValue instanceof Number) return ((Number)dbValue).intValue() != 0;
                if (dbValue instanceof String) { // Ví dụ 'T'/'F', 'Y'/'N', 'true'/'false'
                    String sVal = ((String)dbValue).trim().toLowerCase();
                    return "true".equals(sVal) || "t".equals(sVal) || "y".equals(sVal) || "1".equals(sVal);
                }
            }


            // Nếu không có chuyển đổi nào phù hợp, trả về giá trị gốc hoặc ném lỗi
            System.err.println("Warning: No specific conversion found for DB type " + dbValue.getClass().getName() + " to field type " + fieldType.getName() + ". Returning original value.");
            return dbValue;
        }
    }
}