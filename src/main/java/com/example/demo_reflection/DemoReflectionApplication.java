package com.example.demo_reflection;


import com.example.demo_reflection.entities.User;
import com.example.demo_reflection.helper.RepositoryFactory;
import com.example.demo_reflection.repositories.UserRepository;

import java.util.List;

public class DemoReflectionApplication {

    public static void main(String[] args) {
        System.out.println("--- Starting Reflection Repository Demo ---");

        try {
            // 1. Khởi tạo Repository Factory (sẽ tự đọc db.properties)
            RepositoryFactory factory = new RepositoryFactory();
            System.out.println("RepositoryFactory initialized.");

            // 2. Lấy về đối tượng triển khai UserRepository từ Factory
            // Factory sẽ tạo ra một Proxy implement UserRepository
            UserRepository userRepository = factory.createRepository(UserRepository.class);
            System.out.println("UserRepository instance created (Proxy): " + userRepository.getClass().getName());

            System.out.println("\n--- Testing CRUD Operations ---");

            // 3. Đếm số lượng ban đầu
            long initialCount = userRepository.count();
            System.out.println("[COUNT] Initial user count: " + initialCount);

            // 4. Tạo và Lưu User mới (INSERT)
            User newUser = new User("alice_ Wonderland", "alice@example.com", 25);
            newUser.setTemporarySessionToken("some_temp_data"); // Dữ liệu này sẽ không được lưu
            System.out.println("[SAVE - INSERT] Attempting to save new user: " + newUser.getUsername());
            User savedUser = userRepository.save(newUser); // Gọi save, InvocationHandler xử lý INSERT
            System.out.println("[SAVE - INSERT] User saved successfully. Assigned ID: " + savedUser.getId());
            System.out.println("[SAVE - INSERT] Saved User details: " + savedUser); // ID đã được gán

            if (savedUser.getId() == null) {
                System.err.println("ERROR: Failed to retrieve generated ID after insert!");
                return;
            }
            Long currentUserId = savedUser.getId();

            // 5. Tìm User theo ID vừa tạo
            System.out.println("\n[FIND BY ID] Attempting to find user with ID: " + currentUserId);
            User foundUser = userRepository.findById(currentUserId).orElse(null); // Gọi findById
            if (foundUser != null) {
                System.out.println("[FIND BY ID] Found user: " + foundUser);
                // Kiểm tra xem trường transient có bị đọc lên không (không nên)
                if (foundUser.getTemporarySessionToken() != null) {
                    System.err.println("WARNING: Transient field was populated after findById!");
                }
            } else {
                System.err.println("[FIND BY ID] ERROR: User with ID " + currentUserId + " not found after saving!");
            }

            // 6. Cập nhật User (UPDATE)
            if (foundUser != null) {
                System.out.println("\n[SAVE - UPDATE] Attempting to update user's age...");
                foundUser.setAge(26); // Thay đổi tuổi
                foundUser.setEmail("alice.updated@example.com"); // Thay đổi email
                User updatedUser = userRepository.save(foundUser); // Gọi save với User đã có ID -> UPDATE
                System.out.println("[SAVE - UPDATE] User updated successfully.");
                System.out.println("[SAVE - UPDATE] Updated User details: " + updatedUser);

                // Xác minh lại bằng cách tìm lại
                System.out.println("[VERIFY UPDATE] Re-finding user with ID: " + currentUserId);
                User verifiedUser = userRepository.findById(currentUserId).orElse(null);
                System.out.println("[VERIFY UPDATE] Verified user data: " + verifiedUser);
                if (verifiedUser != null && verifiedUser.getAge() != 26) {
                    System.err.println("ERROR: Age was not updated correctly!");
                }
            }

            // 7. Tìm tất cả User
            System.out.println("\n[FIND ALL] Attempting to find all users...");
            List<User> allUsers = userRepository.findAll(); // Gọi findAll
            System.out.println("[FIND ALL] Found " + allUsers.size() + " users:");
            if (allUsers.isEmpty()) {
                System.out.println("[FIND ALL] No users found in the database.");
            } else {
                allUsers.forEach(user -> System.out.println("  - " + user));
            }

            // 8. Xóa User vừa tạo
            System.out.println("\n[DELETE BY ID] Attempting to delete user with ID: " + currentUserId);
            userRepository.deleteById(currentUserId); // Gọi deleteById
            System.out.println("[DELETE BY ID] Delete command executed for ID: " + currentUserId);

            // 9. Xác minh việc xóa
            System.out.println("[VERIFY DELETE] Attempting to find user with ID " + currentUserId + " again...");
            User deletedUser = userRepository.findById(currentUserId).orElse(null);
            if (deletedUser == null) {
                System.out.println("[VERIFY DELETE] User successfully deleted (findById returned null).");
            } else {
                System.err.println("[VERIFY DELETE] ERROR: User with ID " + currentUserId + " still exists after deletion!");
            }

            // 10. Đếm số lượng cuối cùng
            long finalCount = userRepository.count();
            System.out.println("\n[COUNT] Final user count: " + finalCount);
            if (finalCount == initialCount) {
                System.out.println("[COUNT] Final count matches initial count, CRUD cycle seems complete.");
            } else {
                System.err.println("[COUNT] WARNING: Final count ("+finalCount+") does not match initial count ("+initialCount+"). Check logic or other operations.");
            }


        } catch (Exception e) {
            // Bắt tất cả các lỗi (SQLException, RuntimeException từ Reflection,...)
            System.err.println("\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.err.println("!!! AN ERROR OCCURRED DURING DEMO !!!");
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.err.println("Error Type: " + e.getClass().getName());
            System.err.println("Error Message: " + e.getMessage());
            System.err.println("--- Stack Trace ---");
            e.printStackTrace(); // In chi tiết lỗi để debug
        } finally {
            System.out.println("\n--- Reflection Repository Demo Finished ---");
        }
    }
}