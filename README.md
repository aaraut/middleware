```
Provide a code snippet based on the following schema. Please do not include any introductory text, explanations, or comments. Just provide the code.

Schema: {
  "type": "object",
  "properties": {
    "name": {
      "type": "string"
    },
    "age": {
      "type": "integer"
    }
  },
  "required": ["name", "age"]
}

```

```
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public String generateDummyData(String schema) throws Exception {
    // Setup and API call remains the same
    // ...

    // Convert response to JsonNode for easier processing
    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseNode = mapper.convertValue(response.getBody(), JsonNode.class);
    JsonNode resultNode = responseNode.path("result");
    String content = resultNode.path("content").asText("No content found");

    return extractCodeFromResponse(content);
}

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FallbackService {

    // ... other methods ...

    private String extractCodeFromResponse(String response) {
        // Regular expression to match and remove introductory text
        String regex = "(?i)It seems you have given schema.*?Here is the object|Here is the code|Here is the.*?code.*";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);

        // Remove unwanted text
        String cleanedResponse = matcher.replaceAll("").trim();

        // Further clean up if needed
        // e.g., remove extra new lines or spaces
        cleanedResponse = cleanedResponse.replaceAll("(?m)^\\s*$(\\n|\\r\\n)+", "").trim();

        return cleanedResponse;
    }
}


```
Map<String, Object> requestBody = new HashMap<>();
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "Assistant is a large language model trained by OpenAI.");

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", schema);

        requestBody.put("messages", new Map[]{systemMessage, userMessage});

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(nucleusUrl, entity, Map.class);
        Map<String, Object> responseBody = response.getBody();

        if (responseBody != null) {
            Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
            String content = result != null ? (String) result.get("content") : "No content found";
            return extractCodeFromResponse(content);
        } else {
            throw new Exception("No response body received from Nucleus API");
        }


    private String extractCodeFromResponse(String response) {
        // Example of basic text cleaning, adjust the pattern based on actual response structure
        // Remove any leading phrases like "Here is the object" or "Here is the code"
        // and keep only the relevant code block
        String cleanedResponse = response.replaceAll("(?i)here is the object|(?i)here is the code|(?i)\\n|(?i)\\r", "").trim();

        // Additional cleaning logic if needed
        // e.g., removing comments or unwanted text
        return cleanedResponse;
    }
}


```
package com.example.middleware.service;

import com.example.middleware.model.DataModel;
import com.example.middleware.repository.DataModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class FallbackService {

    @Autowired
    private DataModelRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey = "API-full"; // Replace with your actual API key
    private final String nucleusUrl = "https://openai-nucleus-dev.azpriv-cloud.ubs.net/api/v1/openai-sandbox/chat";

    public void saveFallbackData(String method, String url, String requestBody, JsonNode responseBody) {
        Optional<DataModel> existingDataModel = repository.findByActionAndUrlAndRequest(method, url, requestBody);

        DataModel dataModel;
        if (existingDataModel.isPresent()) {
            // Update existing record
            dataModel = existingDataModel.get();
            dataModel.setResponse(responseBody.toString());
        } else {
            // Create new record
            dataModel = new DataModel();
            dataModel.setAction(method);
            dataModel.setUrl(url);
            dataModel.setRequest(requestBody);
            dataModel.setResponse(responseBody.toString());
        }

        repository.save(dataModel);
    }

    public Optional<DataModel> getFallbackData(String action, String url, String request) {
        Optional<DataModel> fallbackData = repository.findByActionAndUrlAndRequest(action, url, request);

        if (!fallbackData.isPresent()) {
            // Generate dummy data using Nucleus ChatGPT
            try {
                String schema = "Your schema here"; // Replace with actual schema
                String generatedResponse = generateDummyData(schema);
                DataModel dataModel = new DataModel();
                dataModel.setAction(action);
                dataModel.setUrl(url);
                dataModel.setRequest(request);
                dataModel.setResponse(generatedResponse);
                repository.save(dataModel);
                return Optional.of(dataModel);
            } catch (Exception e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }

        return fallbackData;
    }

    private String generateDummyData(String schema) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "Assistant is a large language model trained by OpenAI.");

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", schema);

        requestBody.put("messages", new Map[]{systemMessage, userMessage});

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(nucleusUrl, entity, JsonNode.class);
        JsonNode jsonResponse = response.getBody();
        return jsonResponse.get("result").get("content").asText();
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
