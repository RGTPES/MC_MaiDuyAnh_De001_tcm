package org.example.orderservice.models.services.impl;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.clients.ProductClient;
import org.example.orderservice.exceptions.ProductNotFoundException;
import org.example.orderservice.exceptions.ProductServiceException;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductGatewayService {

    private final ProductClient productClient;

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    public ProductResponse getProductById(Long productId) {
        ProductResponse response;
        try {
            response = productClient.getProductById(productId);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new ProductNotFoundException(productId);
            }
            throw e;
        }

        if (response == null || response.id() == null) {
            throw new ProductNotFoundException(productId);
        }

        if (response.price() == null || response.price() < 0) {
            throw new ProductServiceException("Invalid product price received for productId: " + productId);
        }

        return response;
    }

    public ProductResponse getProductFallback(Long productId, ProductNotFoundException ex) {
        throw ex;
    }

    public ProductResponse getProductFallback(Long productId, Throwable throwable) {
        log.error("Product service fallback triggered for productId: {}. Error: {}", productId, throwable.getMessage());

        if (throwable instanceof ProductNotFoundException pnfe) {
            throw pnfe;
        }
        if (throwable instanceof FeignException fe && fe.status() == 404) {
            throw new ProductNotFoundException(productId);
        }
        if (throwable.getCause() instanceof ProductNotFoundException pnfe) {
            throw pnfe;
        }
        if (throwable.getCause() instanceof FeignException fe && fe.status() == 404) {
            throw new ProductNotFoundException(productId);
        }
        if (throwable instanceof ProductServiceException pse) {
            throw pse;
        }

        throw new ProductServiceException("Product service is unavailable or returned an error: " + throwable.getMessage(), throwable);
    }
}
