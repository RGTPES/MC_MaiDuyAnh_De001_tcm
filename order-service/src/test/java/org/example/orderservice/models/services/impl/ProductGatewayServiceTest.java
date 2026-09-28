package org.example.orderservice.models.services.impl;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.example.orderservice.clients.ProductClient;
import org.example.orderservice.exceptions.ProductNotFoundException;
import org.example.orderservice.exceptions.ProductServiceException;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductGatewayServiceTest {

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private ProductGatewayService productGatewayService;

    private Request dummyRequest;

    @BeforeEach
    void setUp() {
        dummyRequest = Request.create(
                Request.HttpMethod.GET,
                "/api/v1/products/1",
                Collections.emptyMap(),
                null,
                new RequestTemplate()
        );
    }

    @Test
    @DisplayName("getProductById should return product when client succeeds with valid data")
    void getProductById_Success() {
        ProductResponse expected = new ProductResponse(1L, "Laptop", 1200.0);
        when(productClient.getProductById(1L)).thenReturn(expected);

        ProductResponse actual = productGatewayService.getProductById(1L);

        assertThat(actual).isNotNull();
        assertThat(actual.id()).isEqualTo(1L);
        assertThat(actual.name()).isEqualTo("Laptop");
        assertThat(actual.price()).isEqualTo(1200.0);
    }

    @Test
    @DisplayName("getProductById should throw ProductNotFoundException when Feign returns 404")
    void getProductById_Feign404_ThrowsProductNotFoundException() {
        FeignException.NotFound notFound = new FeignException.NotFound(
                "Not Found",
                dummyRequest,
                null,
                null
        );
        when(productClient.getProductById(1L)).thenThrow(notFound);

        assertThatThrownBy(() -> productGatewayService.getProductById(1L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found with id: 1");
    }

    @Test
    @DisplayName("getProductById should throw ProductNotFoundException when response is null")
    void getProductById_NullResponse_ThrowsProductNotFoundException() {
        when(productClient.getProductById(1L)).thenReturn(null);

        assertThatThrownBy(() -> productGatewayService.getProductById(1L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found with id: 1");
    }

    @Test
    @DisplayName("getProductById should throw ProductServiceException when price is null")
    void getProductById_NullPrice_ThrowsProductServiceException() {
        when(productClient.getProductById(1L)).thenReturn(new ProductResponse(1L, "Laptop", null));

        assertThatThrownBy(() -> productGatewayService.getProductById(1L))
                .isInstanceOf(ProductServiceException.class)
                .hasMessageContaining("Invalid product price received for productId: 1");
    }

    @Test
    @DisplayName("getProductFallback should rethrow ProductNotFoundException directly")
    void getProductFallback_ProductNotFound_RethrowsException() {
        ProductNotFoundException exception = new ProductNotFoundException(99L);

        assertThatThrownBy(() -> productGatewayService.getProductFallback(99L, exception))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found with id: 99");
    }

    @Test
    @DisplayName("getProductFallback should throw ProductNotFoundException when Feign 404 is caught")
    void getProductFallback_Feign404_ThrowsProductNotFoundException() {
        FeignException.NotFound notFound = new FeignException.NotFound(
                "Not Found",
                dummyRequest,
                null,
                null
        );

        assertThatThrownBy(() -> productGatewayService.getProductFallback(99L, notFound))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found with id: 99");
    }

    @Test
    @DisplayName("getProductFallback should throw ProductServiceException when circuit is OPEN (CallNotPermittedException)")
    void getProductFallback_CallNotPermittedException_ThrowsProductServiceException() {
        CircuitBreaker cb = mock(CircuitBreaker.class);
        CallNotPermittedException openException = CallNotPermittedException.createCallNotPermittedException(cb);

        assertThatThrownBy(() -> productGatewayService.getProductFallback(1L, openException))
                .isInstanceOf(ProductServiceException.class)
                .hasMessageContaining("Product service is unavailable");
    }

    @Test
    @DisplayName("getProductFallback should throw ProductServiceException for Feign 500 error")
    void getProductFallback_Feign500_ThrowsProductServiceException() {
        FeignException.InternalServerError serverError = new FeignException.InternalServerError(
                "Internal Server Error",
                dummyRequest,
                null,
                null
        );

        assertThatThrownBy(() -> productGatewayService.getProductFallback(1L, serverError))
                .isInstanceOf(ProductServiceException.class)
                .hasMessageContaining("Product service is unavailable or returned an error");
    }
}
