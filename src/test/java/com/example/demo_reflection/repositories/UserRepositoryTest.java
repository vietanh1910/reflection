package com.example.demo_reflection.repositories;

import com.example.demo_reflection.entities.User;
import com.example.demo_reflection.helper.RepositoryFactory;
import org.junit.jupiter.api.*; // Import các annotation của JUnit 5

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.Optional; // Sử dụng Optional có thể làm code rõ ràng hơn

import static org.junit.jupiter.api.Assertions.*; // Import các phương thức assert

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Chạy @BeforeAll/@AfterAll không cần static
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // Sắp xếp test theo @Order (tùy chọn)
class UserRepositoryTest {

    private static RepositoryFactory factory;
    private static UserRepository userRepository;
    private static Properties testDbProps;

    // --- Cấu hình cho H2 In-Memory Database ---
    private static final String H2_JDBC_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    // DB_CLOSE_DELAY=-1: Giữ DB tồn tại trong suốt quá trình test
    // MODE=MySQL: Chạy H2 ở chế độ tương thích MySQL
    private static final String H2_JDBC_USER = "sa";
    private static final String H2_JDBC_PASSWORD = "";
    private static final String H2_JDBC_DRIVER = "org.h2.Driver";


    @BeforeAll
    void setupAll() throws SQLException {
        System.out.println("--- Setting up Test Environment (H2 Database) ---");
        // 1. Tạo Properties cho H2
        testDbProps = new Properties();
        testDbProps.setProperty("db.driver", H2_JDBC_DRIVER);
        testDbProps.setProperty("db.url", H2_JDBC_URL);
        testDbProps.setProperty("db.username", H2_JDBC_USER);
        testDbProps.setProperty("db.password", H2_JDBC_PASSWORD);

        // 2. Khởi tạo Factory với cấu hình H2
        factory = new RepositoryFactory(testDbProps);

        // 3. Lấy Repository Instance
        userRepository = factory.createRepository(UserRepository.class);
        assertNotNull(userRepository, "UserRepository should be created by factory");

        // 4. Tạo bảng 'users' trong H2 Database
        // Sử dụng try-with-resources để đảm bảo connection/statement được đóng
        try (Connection conn = DriverManager.getConnection(H2_JDBC_URL, H2_JDBC_USER, H2_JDBC_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Xóa bảng cũ nếu tồn tại (đảm bảo môi trường sạch)
            stmt.execute("DROP TABLE IF EXISTS users");
            System.out.println("Dropped existing 'users' table (if any).");

            // Tạo bảng mới - dùng cú pháp tương thích H2/MySQL
            String createTableSql = "CREATE TABLE users (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_name VARCHAR(255) NOT NULL UNIQUE, " +
                    "email VARCHAR(255) UNIQUE, " +
                    "age INT" +
                    ")";
            stmt.execute(createTableSql);
            System.out.println("'users' table created successfully in H2.");
        } catch (SQLException e) {
            System.err.println("Failed to initialize H2 database schema!");
            throw e; // Ném lại lỗi để test fail nếu không setup được DB
        }
    }

    @BeforeEach
    void setupEach() throws SQLException {
        // Xóa toàn bộ dữ liệu trong bảng trước mỗi test để đảm bảo độc lập
        try (Connection conn = DriverManager.getConnection(H2_JDBC_URL, H2_JDBC_USER, H2_JDBC_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM users");
        }
        // System.out.println("--- Cleaned 'users' table before test ---");
    }

    @Test
    @Order(1)
    @DisplayName("1. Save new user and verify ID generation")
    void testSaveNewUser() {
        User newUser = new User("test_user_1", "test1@example.com", 30);
        final String originalTransientValue = "should_be_ignored"; // Lưu lại giá trị gốc
        newUser.setTemporarySessionToken(originalTransientValue);

        User savedUser = assertDoesNotThrow(() -> userRepository.save(newUser),
                "userRepository.save should not throw an exception");

        assertNotNull(savedUser, "Saved user should not be null");
        assertNotNull(savedUser.getId(), "Saved user ID should be generated (not null)");
        assertTrue(savedUser.getId() > 0, "Saved user ID should be positive");
        assertEquals("test_user_1", savedUser.getUsername());
        assertEquals("test1@example.com", savedUser.getEmail());
        assertEquals(30, savedUser.getAge());
        assertEquals(originalTransientValue, savedUser.getTemporarySessionToken(),
                "Transient field on the returned object from save() should retain its original value.");

        Optional<User> userOptional = assertDoesNotThrow(
                () -> userRepository.findById(savedUser.getId()),
                "findById should not throw an exception");

        assertTrue(userOptional.isPresent(), "User should be retrievable from DB after save (Optional should not be empty)");

        userOptional.ifPresent(userFromDb -> {
            assertNull(userFromDb.getTemporarySessionToken(),
                    "Transient field should be null when object is re-fetched from DB.");
            assertEquals(savedUser.getId(), userFromDb.getId());
            assertEquals(savedUser.getUsername(), userFromDb.getUsername());
        });
    }

    @Test
    @Order(2)
    @DisplayName("2. Find user by existing ID")
    void testFindById_Existing() {
        // Arrange: Tạo và lưu một user trước
        User userToSave = new User("findme", "findme@example.com", 40);
        User savedUser = userRepository.save(userToSave);
        Long existingId = savedUser.getId();
        assertNotNull(existingId, "Need a valid ID for findById test");

        // Act: Tìm user bằng ID đó
        User foundUser = assertDoesNotThrow(() -> userRepository.findById(existingId).orElse(null),
                "findById should not throw an exception for existing ID");

        // Assert: Kiểm tra user tìm được
        assertNotNull(foundUser, "User should be found for existing ID");
        assertEquals(existingId, foundUser.getId());
        assertEquals("findme", foundUser.getUsername());
        assertEquals("findme@example.com", foundUser.getEmail());
        assertEquals(40, foundUser.getAge());
        assertNull(foundUser.getTemporarySessionToken(), "Transient field should be null when found");
    }

    @Test
    @Order(3)
    @DisplayName("3. Find user by non-existing ID")
    void testFindById_NonExisting() {
        Long nonExistingId = 9999L; // Giả sử ID này không tồn tại

        User foundUser = assertDoesNotThrow(() -> userRepository.findById(nonExistingId).orElse(null),
                "findById should not throw an exception for non-existing ID");

        assertNull(foundUser, "User should not be found for non-existing ID");
    }

    @Test
    @Order(4)
    @DisplayName("4. Update existing user")
    void testUpdateUser() {
        // Arrange: Tạo và lưu user
        User userToSave = new User("update_me", "update@example.com", 50);
        User savedUser = userRepository.save(userToSave);
        Long userId = savedUser.getId();
        assertNotNull(userId, "Need a saved user to update");

        // Act: Thay đổi thông tin và gọi save lần nữa
        savedUser.setUsername("updated_user");
        savedUser.setAge(51);
        User updatedUser = assertDoesNotThrow(() -> userRepository.save(savedUser),
                "Saving an existing user (update) should not throw");

        // Assert: Kiểm tra user đã được cập nhật
        assertNotNull(updatedUser, "Updated user should not be null");
        assertEquals(userId, updatedUser.getId(), "ID should remain the same after update");
        assertEquals("updated_user", updatedUser.getUsername());
        assertEquals(51, updatedUser.getAge());
        assertEquals("update@example.com", updatedUser.getEmail(), "Email should not change if not set"); // Email giữ nguyên

        // Verify bằng cách đọc lại từ DB
        User foundAfterUpdate = userRepository.findById(userId).orElse(null);
        assertNotNull(foundAfterUpdate);
        assertEquals("updated_user", foundAfterUpdate.getUsername());
        assertEquals(51, foundAfterUpdate.getAge());
    }

    @Test
    @Order(5)
    @DisplayName("5. Find all users")
    void testFindAll() {
        // Arrange: Lưu nhiều users
        userRepository.save(new User("user_a", "a@example.com", 21));
        userRepository.save(new User("user_b", "b@example.com", 22));
        userRepository.save(new User("user_c", "c@example.com", 23));

        // Act: Gọi findAll
        List<User> allUsers = assertDoesNotThrow(() -> userRepository.findAll(),
                "findAll should not throw an exception");

        // Assert
        assertNotNull(allUsers, "Result list should not be null");
        assertEquals(3, allUsers.size(), "Should find 3 users");

        // Kiểm tra xem có đúng các user đã tạo không (ví dụ kiểm tra username)
        assertTrue(allUsers.stream().anyMatch(u -> "user_a".equals(u.getUsername())));
        assertTrue(allUsers.stream().anyMatch(u -> "user_b".equals(u.getUsername())));
        assertTrue(allUsers.stream().anyMatch(u -> "user_c".equals(u.getUsername())));
    }

    @Test
    @Order(6)
    @DisplayName("6. Count users")
    void testCount() {
        // Arrange: Lưu users
        assertEquals(0, userRepository.count(), "Initial count should be 0 after cleanup");
        userRepository.save(new User("count_1", "c1@example.com", 60));
        userRepository.save(new User("count_2", "c2@example.com", 61));

        // Act: Gọi count
        long userCount = assertDoesNotThrow(() -> userRepository.count(),
                "count should not throw an exception");

        // Assert
        assertEquals(2, userCount, "Should count 2 users");

        // Thêm 1 user nữa và kiểm tra lại
        userRepository.save(new User("count_3", "c3@example.com", 62));
        assertEquals(3, userRepository.count(), "Should count 3 users after adding one more");
    }


    @Test
    @Order(7)
    @DisplayName("7. Delete user by ID")
    void testDeleteById() {
        // Arrange: Lưu user
        User userToDelete = new User("delete_me", "delete@example.com", 70);
        User savedUser = userRepository.save(userToDelete);
        Long userId = savedUser.getId();
        assertNotNull(userId, "Need user ID to delete");

        // Kiểm tra là user tồn tại trước khi xóa
        assertNotNull(userRepository.findById(userId), "User should exist before deletion");
        assertEquals(1, userRepository.count(), "Count should be 1 before deletion");

        // Act: Gọi deleteById
        assertDoesNotThrow(() -> userRepository.deleteById(userId),
                "deleteById should not throw an exception");

        // Assert: Kiểm tra user không còn tồn tại
        assertNull(userRepository.findById(userId), "User should be null after deletion");
        assertEquals(0, userRepository.count(), "Count should be 0 after deletion");
    }

    @Test
    @Order(8)
    @DisplayName("8. Delete non-existing user")
    void testDeleteById_NonExisting() {
        Long nonExistingId = 8888L;
        // Đảm bảo không có user nào
        assertEquals(0, userRepository.count(), "Table should be empty");

        // Act & Assert: Gọi deleteById cho ID không tồn tại, không nên ném lỗi
        assertDoesNotThrow(() -> userRepository.deleteById(nonExistingId),
                "deleteById for non-existing ID should not throw an exception");

        // Đảm bảo không có gì thay đổi
        assertEquals(0, userRepository.count(), "Count should remain 0");
    }


    @AfterAll
    void tearDownAll() throws SQLException {
        System.out.println("--- Tearing down Test Environment ---");
        // Không cần làm gì nhiều với H2 in-memory và DB_CLOSE_DELAY=-1
        // Nó sẽ tự xóa khi JVM tắt.
        // Nếu cần, có thể drop table ở đây.
        try (Connection conn = DriverManager.getConnection(H2_JDBC_URL, H2_JDBC_USER, H2_JDBC_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users");
            System.out.println("Dropped 'users' table.");
        } catch (SQLException e) {
            System.err.println("Error during teardown: " + e.getMessage());
        }
        factory = null; // Giúp GC
        userRepository = null;
    }
}