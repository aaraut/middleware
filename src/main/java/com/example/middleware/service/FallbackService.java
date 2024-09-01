package com.example.middleware.service;

import com.example.middleware.model.DataModel;
import com.example.middleware.repository.DataModelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FallbackService {

    @Autowired
    private DataModelRepository repository;

    public void saveFallbackData(DataModel dataModel) {
        repository.save(dataModel);
    }

    public Optional<DataModel> getFallbackData(String action, String url, String request) {
        return repository.findByActionAndUrlAndRequest(action, url, request);
    }
}
