package com.example.middleware.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BackendServiceDiscovery {

    private final String backendServiceUrl;

    public BackendServiceDiscovery(@Value("${middleware.backend-service.url}") String backendServiceUrl) {
        this.backendServiceUrl = backendServiceUrl;
    }

    public String getBaseUrl() {
        return backendServiceUrl;
    }
}
