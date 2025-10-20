package com.example.taskapi;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

// This gives us methods like save(), findById(), findAll(), deleteById()
public interface TaskRepository extends MongoRepository<Task, String> {
    
    // This is a custom method we're defining. Spring Data is smart 
    // enough to build the query just from the method name.
    List<Task> findByNameContaining(String name);
}