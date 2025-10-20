package com.example.taskapi;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "tasks") // Tells MongoDB to store this in a "tasks" collection
public class Task {
    @Id // Marks this field as the unique ID
    private String id; 
    
    private String name;
    private String owner;
    private String command;
    private List<TaskExecution> taskExecutions;
}