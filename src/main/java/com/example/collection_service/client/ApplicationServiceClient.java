package com.example.collection_service.client;


import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "application-service", url = "http://localhost:8081/api/applications")
public interface ApplicationServiceClient {

    @GetMapping("/{id}")
    ApplicationDetailResponseDTO getApplicationDetails(@PathVariable("id") Long id);

}