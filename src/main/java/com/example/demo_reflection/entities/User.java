package com.example.demo_reflection.entities;

import com.example.demo_reflection.annotations.MyColumn;
import com.example.demo_reflection.annotations.MyEntity;
import com.example.demo_reflection.annotations.MyId;
import com.example.demo_reflection.annotations.MyTransient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@MyEntity(tableName = "users")
// Hoặc có thể dùng @MyTable(name = "users") thay thế hoặc bổ sung
public class User {

    @MyId // Đánh dấu khóa chính
    @MyColumn // ID cũng là một cột, lấy tên mặc định là "id"
    private Long id; // Kiểu ID là Long

    @MyColumn(name = "user_name") // Chỉ định tên cột cụ thể
    private String username;

    @MyColumn // Không chỉ định tên, sẽ lấy tên "email" làm tên cột
    private String email;

    @MyColumn
    private int age;

    @MyTransient // Trường này sẽ không được lưu vào CSDL
    private String temporarySessionToken;

    // Cần có constructor không tham số để Reflection tạo đối tượng khi map từ ResultSet
    public User() {
    }

    // Constructor tiện lợi (không bắt buộc cho thư viện reflection)
    public User(String username, String email, int age) {
        this.username = username;
        this.email = email;
        this.age = age;
    }

    // Getters and Setters (Bắt buộc để Reflection đọc/ghi giá trị)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getTemporarySessionToken() {
        return temporarySessionToken;
    }

    public void setTemporarySessionToken(String temporarySessionToken) {
        this.temporarySessionToken = temporarySessionToken;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                // Không in trường transient ra đây trừ khi cần debug
                // ", temporarySessionToken='" + temporarySessionToken + '\'' +
                '}';
    }
}
