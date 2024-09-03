```
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
</dependency>
```

```
package com.example.middleware.service;

import com.example.middleware.model.DataModel;
import com.example.middleware.repository.DataModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
public class FallbackService {

    @Autowired
    private DataModelRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey = "API-full"; // Change API key
    private final String nucleusUrl = "https://openai-nucleus-dev.azpriv-cloud.ubs.net/api/v1/openai-sandbox/chat";

    public void saveFallbackData(String method, String url, String requestBody, JsonNode responseBody) {
        Optional<DataModel> existingDataModel = repository.findByActionAndUrlAndRequest(method, url, requestBody);

        DataModel dataModel;
        if (existingDataModel.isPresent()) {
            // Update existing record
            dataModel = existingDataModel.get();
            try {
                dataModel.setResponse(objectMapper.writeValueAsString(responseBody));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Create new record
            dataModel = new DataModel();
            dataModel.setAction(method);
            dataModel.setUrl(url);
            try {
                dataModel.setRequest(objectMapper.writeValueAsString(objectMapper.readTree(requestBody)));
                dataModel.setResponse(objectMapper.writeValueAsString(responseBody));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        repository.save(dataModel);
    }

    public Optional<DataModel> getFallbackData(String action, String url, String request) {
        Optional<DataModel> fallbackData = repository.findByActionAndUrlAndRequest(action, url, request);

        if (!fallbackData.isPresent()) {
            // Generate dummy data using Nucleus ChatGPT
            try {
                String schema = "Your schema here"; // Pass schema as needed
                String generatedResponse = generateDummyData(schema);
                DataModel dataModel = new DataModel();
                dataModel.setAction(action);
                dataModel.setUrl(url);
                dataModel.setRequest(request);
                dataModel.setResponse(generatedResponse);
                repository.save(dataModel);
                return Optional.of(dataModel);
            } catch (IOException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }
        
        return fallbackData;
    }

    private String generateDummyData(String schema) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(nucleusUrl);
            post.setHeader("api-key", apiKey);
            post.setHeader("Content-Type", "application/json");

            String jsonPayload = "{ \"messages\": [ { \"role\": \"system\", \"content\": \"Assistant is a large language model trained by OpenAI.\" }, { \"role\": \"user\", \"content\": \"" + schema + "\" } ] }";
            post.setEntity(new StringEntity(jsonPayload));

            try (org.apache.http.client.methods.CloseableHttpResponse response = client.execute(post)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                return jsonResponse.get("result").get("content").asText();
            }
        }
    }
}
```

```
package com.example.middleware.controller;

import com.example.middleware.model.DataModel;
import com.example.middleware.service.BackendServiceDiscovery;
import com.example.middleware.service.FallbackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Optional;

@RestController
@RequestMapping("/proxy")
public class DynamicProxyController {

    @Autowired
    private BackendServiceDiscovery serviceDiscovery;

    @Autowired
    private FallbackService fallbackService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping("/**")
    public ResponseEntity<JsonNode> proxyRequest(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {

        // Construct the backend URL
        String backendUrl = serviceDiscovery.getBaseUrl() + request.getRequestURI().replace("/proxy", "");

        try {
            // Attempt to forward the request
            ResponseEntity<String> response = restTemplate.exchange(
                    backendUrl,
                    HttpMethod.valueOf(request.getMethod()),
                    new HttpEntity<>(body, getHeaders(request)),
                    String.class
            );

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            saveFallbackData(request, body, responseBody);
            return ResponseEntity.ok(responseBody);
        } catch (HttpClientErrorException | ResourceAccessException e) {
            // Handle errors and fallback to response
            System.err.println("Backend service error: " + e.getMessage());
            return getFallbackResponse(request, body);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private HttpHeaders getHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.add(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    private void saveFallbackData(HttpServletRequest request, String requestBody, JsonNode responseBody) {
        fallbackService.saveFallbackData(
                request.getMethod(),
                request.getRequestURI(),
                requestBody,
                responseBody
        );
    }

    private ResponseEntity<JsonNode> getFallbackResponse(HttpServletRequest request, String requestBody) {
        Optional<DataModel> fallbackData = fallbackService.getFallbackData(
                request.getMethod(),
                request.getRequestURI(),
                requestBody
        );

        if (fallbackData.isPresent()) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(fallbackData.get().getResponse());
                return ResponseEntity.ok(jsonResponse);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
    }
}
```
