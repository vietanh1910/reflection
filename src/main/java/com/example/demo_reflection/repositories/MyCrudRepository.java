package com.example.demo_reflection.repositories;

import java.util.List;
import java.util.Optional;

public interface MyCrudRepository<T, ID> {
    T save(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    void deleteById(ID id);
    long count();
}
