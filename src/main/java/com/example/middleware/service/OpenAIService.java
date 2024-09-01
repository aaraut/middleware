package com.example.middleware.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public OpenAIService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateDummyData(String prompt) {
        // OpenAI API URL
        String apiUrl = "https://api.openai.com/v1/engines/davinci-codex/completions";

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");

        // Set up request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("max_tokens", 150); // Adjust the token count as needed
        requestBody.put("temperature", 0.7); // Adjust the temperature as needed

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // Make API request
        ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            // Extract and return the generated text
            Map<String, Object> responseBody = response.getBody();
            return (String) ((Map<String, Object>) ((Map<String, Object>) responseBody.get("choices")).get(0)).get("text");
        } else {
            throw new RuntimeException("Failed to generate dummy data");
        }
    }
}
