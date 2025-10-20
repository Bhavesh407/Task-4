package com.example.taskapi;

import java.util.Date;

import lombok.Data; // This auto-generates getters/setters

@Data // Asks Lombok to create standard methods for us
public class TaskExecution {
    private Date startTime;
    private Date endTime;
    private String output;
}