package com.example.middleware.controller;

import com.example.middleware.model.DataModel;
import com.example.middleware.repository.DataModelRepository;
import com.example.middleware.service.BackendServiceDiscovery;
import com.example.middleware.service.FallbackService;
import com.example.middleware.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
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

    @Autowired
    private DataModelRepository dataModelRepository;

    @Autowired
    private OpenAIService openAIService;

    @RequestMapping("/**")
    public ResponseEntity<String> proxyRequest(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {

        // Construct the backend URL
        String backendUrl = "http://localhost:8081" + request.getRequestURI().replace("/proxy", "");

        try {
            // Attempt to forward the request
            ResponseEntity<String> response = restTemplate.exchange(
                    backendUrl,
                    HttpMethod.valueOf(request.getMethod()),
                    new HttpEntity<>(body, getHeaders(request)),
                    String.class
            );
            // Save data for fallback scenarios
            saveFallbackData(request, body, response.getBody());
            return response;
        } catch (HttpClientErrorException e) {
            // Log the error and fallback to response
            System.err.println("Backend service error: " + e.getMessage());
            return getFallbackResponse(request, body);
        } catch (ResourceAccessException e) {
            // Handle connection refused
            System.err.println("Connection error: " + e.getMessage());
            return getFallbackResponse(request, body);
        }
    }

    private ResponseEntity<String> getFallbackResponse(HttpServletRequest request, String body) {
        // Fetch fallback data from the database
        Optional<DataModel> dataModelOpt = dataModelRepository.findByActionAndUrlAndRequest(
                request.getMethod(),
                request.getRequestURI().replace("/proxy", ""),
                body
        );

        if (dataModelOpt.isPresent()) {
            return ResponseEntity.ok(dataModelOpt.get().getResponse());
        } else {
            // Generate dummy data using OpenAI
            String prompt = "Generate dummy data for request body: " + body;
            String generatedData = openAIService.generateDummyData(prompt);
            return ResponseEntity.ok(generatedData);
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

    private void saveFallbackData(HttpServletRequest request, String requestBody, String responseBody) {
        DataModel dataModel = new DataModel();
        dataModel.setAction(request.getMethod());
        dataModel.setUrl(request.getRequestURI().replace("/proxy", ""));
        dataModel.setRequest(requestBody);
        dataModel.setResponse(responseBody);
        dataModelRepository.save(dataModel);
    }
}
