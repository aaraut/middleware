package com.example.middleware.controller;

import com.example.middleware.model.DataModel;
import com.example.middleware.service.BackendServiceDiscovery;
import com.example.middleware.service.FallbackService;
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

    @RequestMapping("/**")
    public ResponseEntity<String> proxyRequest(
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
        dataModel.setUrl(request.getRequestURI());
        dataModel.setRequest(requestBody);
        dataModel.setResponse(responseBody);
        fallbackService.saveFallbackData(dataModel);
    }

    private ResponseEntity<String> getFallbackResponse(HttpServletRequest request, String requestBody) {
        Optional<DataModel> fallbackData = fallbackService.getFallbackData(
                request.getMethod(),
                request.getRequestURI(),
                requestBody
        );
        return fallbackData.isPresent() ?
                ResponseEntity.ok(fallbackData.get().getResponse()) :
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("No fallback data available");
    }
}
