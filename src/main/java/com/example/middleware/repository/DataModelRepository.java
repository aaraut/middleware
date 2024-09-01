package com.example.middleware.repository;

import com.example.middleware.model.DataModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DataModelRepository extends JpaRepository<DataModel, Long> {
    Optional<DataModel> findByActionAndUrlAndRequest(String action, String url, String request);
}

