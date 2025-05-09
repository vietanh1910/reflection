package com.example.demo_reflection.entities;

import com.example.demo_reflection.annotations.MyColumn;
import com.example.demo_reflection.annotations.MyEntity;
import com.example.demo_reflection.annotations.MyId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@MyEntity(name = "user_demo")
public class User {
    @MyId
    private Long id;

    @MyColumn(name = "user_name")
    private String name;

    @MyColumn(name = "user_age")
    private int age;

    @MyColumn(name = "user_email", unique = true, nullable = false)
    private String email;
}
