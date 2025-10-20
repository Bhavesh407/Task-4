package com.example.taskapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.util.Config;

@RestController
@RequestMapping("/tasks") // All URLs in this file start with /tasks
public class TaskController {

    // Spring will automatically give us an instance of the repository
    @Autowired 
    private TaskRepository taskRepository;

    private CoreV1Api k8sApi;

    // This constructor runs when the app starts
    public TaskController() {
        try {
            // This line automatically finds your Kubernetes cluster.
            // It works inside a cluster or on your local machine.
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);
            
            // Create an API object to talk to Kubernetes
            this.k8sApi = new CoreV1Api();
        } catch (Exception e) {
            e.printStackTrace();
            // Handle initialization error
        }
    }

    @GetMapping
    public ResponseEntity<List<Task>> getTasks(@RequestParam(required = false) String id) {
        if (id != null) {
            Optional<Task> taskOpt = taskRepository.findById(id);
            if (taskOpt.isPresent()) {
                return ResponseEntity.ok(List.of(taskOpt.get()));
            } else {
                return ResponseEntity.notFound().build(); // 404
            }
        } else {
            return ResponseEntity.ok(taskRepository.findAll());
        }
    }

    // Endpoint: PUT /tasks (Create/Update a task)
    @PutMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        // Simple security check
        if (task.getCommand() == null || task.getCommand().contains("rm ") || task.getCommand().contains("sudo")) {
            return ResponseEntity.badRequest().build(); // 400 Bad Request
        }
        
        if (task.getTaskExecutions() == null) {
            task.setTaskExecutions(new ArrayList<>());
        }
        
        Task savedTask = taskRepository.save(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTask);
    }

    // Endpoint: DELETE /tasks/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        if (taskRepository.existsById(id)) {
            taskRepository.deleteById(id);
            return ResponseEntity.noContent().build(); // 204 No Content
        } else {
            return ResponseEntity.notFound().build(); // 404
        }
    }

    // Endpoint: GET /tasks/findByName?name={name}
    @GetMapping("/findByName")
    public ResponseEntity<List<Task>> findTasksByName(@RequestParam String name) {

        // This is the line we are testing again
        List<Task> tasks = taskRepository.findByNameContaining(name); 

        if (tasks.isEmpty()) {
            return ResponseEntity.notFound().build(); // Return 404 if nothing found
        }
        return ResponseEntity.ok(tasks);
    }

    // Endpoint: PUT /tasks/{id}/execute (Execute a task)
    // Endpoint: PUT /tasks/{id}/execute (Execute a task)
    // Endpoint: PUT /tasks/{id}/execute (Execute a task)
    // Endpoint: PUT /tasks/{id}/execute (Execute a task)
    @PutMapping("/{id}/execute")
    public ResponseEntity<Task> executeTask(@PathVariable String id) {
        Optional<Task> taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Task task = taskOpt.get();
        Date startTime = new Date();
        String output = "";

        // This is the unique name for our pod
        String podName = "task-runner-" + UUID.randomUUID().toString();

        try {
            // 1. Define the Pod we want to create
            V1Pod pod = new V1Pod()
                .apiVersion("v1")
                .kind("Pod")
                .metadata(new V1ObjectMeta().name(podName))
                .spec(new V1PodSpec()
                    .containers(Collections.singletonList(
                        new V1Container()
                            .name("task-container")
                            .image("busybox") // Use the busybox image
                            .command(Collections.singletonList("/bin/sh"))
                            .args(Arrays.asList("-c", task.getCommand()))
                    ))
                    .restartPolicy("Never") // Don't restart the pod when it finishes
                );

            // 2. Create the Pod in Kubernetes (in the "default" namespace)
            System.out.println("Attempting to create pod: " + podName);
            k8sApi.createNamespacedPod("default", pod, null, null, null, null);
            System.out.println("Pod creation requested.");

            // 3. Wait for the Pod to complete
            V1Pod podStatus;
            while (true) {
                podStatus = k8sApi.readNamespacedPodStatus(podName, "default", null);

                if (podStatus != null && podStatus.getStatus() != null && podStatus.getStatus().getPhase() != null) {
                    String phase = podStatus.getStatus().getPhase();
                    System.out.println("Pod " + podName + " is in phase: " + phase);
                    if ("Succeeded".equals(phase) || "Failed".equals(phase)) {
                        break;
                    }
                }
                Thread.sleep(1000);
            }

            // 4. Get the logs (output) from the Pod
            System.out.println("Pod finished. Attempting to get logs.");
            try {
                String podLog = k8sApi.readNamespacedPodLog(
                    podName, "default", null, false, null, null, null,
                    false, null, null, false
                );
                output = podLog;
                System.out.println("Pod logs retrieved successfully.");
            } catch (ApiException e) {
                System.err.println("ApiException while getting logs: " + e.getResponseBody());
                output = "Failed to get pod logs: " + e.getResponseBody();
            }

        } catch (ApiException e) {
            // --- This block now logs the full Kubernetes error ---
            System.err.println("--- KUBERNETES API EXCEPTION ---");
            System.err.println("Code: " + e.getCode());
            System.err.println("Body: " + e.getResponseBody());
            System.err.println("Headers: " + e.getResponseHeaders());
            e.printStackTrace();
            output = "Execution failed: " + e.getResponseBody();
        } catch (Exception e) {
            // --- This block now logs any other Java error ---
            System.err.println("--- GENERAL JAVA EXCEPTION ---");
            e.printStackTrace();
            output = "Execution failed: " + e.toString();
            
        } finally {
            // 5. Delete the Pod
            try {
                System.out.println("Attempting to delete pod: " + podName);
                k8sApi.deleteNamespacedPod(podName, "default", null, null, 0, null, null, null);
                System.out.println("Pod deleted.");
            } catch (Exception e) {
                System.err.println("Failed to delete pod: " + podName + " - " + e.getMessage());
            }
        }

        Date endTime = new Date();
        
        // Create and store the execution result
        TaskExecution execution = new TaskExecution();
        execution.setStartTime(startTime);
        execution.setEndTime(endTime);
        execution.setOutput(output);
        
        task.getTaskExecutions().add(execution);
        Task updatedTask = taskRepository.save(task); // Save back to DB
        
        return ResponseEntity.ok(updatedTask);
    }
}