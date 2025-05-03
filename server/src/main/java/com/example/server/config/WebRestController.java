package com.example.server.config;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.HashMap;

import com.example.server.CRDTfiles.CRDT;
import com.example.server.CRDTfiles.CRDTManager;

@RestController
public class WebRestController {

    private final CRDTManager crdtManager;

    public WebRestController(CRDTManager crdtManager) {
        // Constructor
        this.crdtManager = crdtManager;
    }

    @PostMapping("/createDocument")
    public HashMap<String, Object> createDocument() {
   
        System.out.println("Creating document: ");

        return crdtManager.CreateDocument();
    }

    @GetMapping("/JoinDocument/{documentCode}")
    public HashMap<String, String> joinDocument(@PathVariable String documentCode) {

        HashMap<String, String> response = crdtManager.joinDocument(documentCode);
        return response;
        
    }

    @GetMapping("/test")
    public String testConnection() {
        System.out.println("Testing connection to the server...");
        return "Connection successful!";
    }
}
