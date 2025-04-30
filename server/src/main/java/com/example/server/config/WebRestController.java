package com.example.server.config;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class WebRestController {

    // Data structure to store all generated viewer and editor code pairs
    private final Map<String, String> generatedCodes = new HashMap<>();

    public WebRestController() {
        // Constructor
    }

    @PostMapping("/createDocument")
    public String createDocument() {
        String viewerCode;
        String editorCode;

        System.out.println("Creating document: ");

        // Generate unique viewer and editor codes
        do {
            viewerCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } while (generatedCodes.containsKey(viewerCode));

        do {
            editorCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } while (generatedCodes.containsValue(editorCode));

        // Add the generated codes to the map
        generatedCodes.put(viewerCode, editorCode);

        System.out.println("Viewer Code: " + viewerCode);
        System.out.println("Editor Code: " + editorCode);

        // Return the viewer and editor codes as a JSON response
        String response = viewerCode + " " + editorCode;
        return response;
    }

    @GetMapping("/test")
    public String testConnection() {
        System.out.println("Testing connection to the server...");
        return "Connection successful!";
    }
}
