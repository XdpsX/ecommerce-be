package com.xdpsx.ecommerce.controllers;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.dtos.common.PageResponse;
import com.xdpsx.ecommerce.dtos.product.*;
import com.xdpsx.ecommerce.services.ProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> getAllProducts(@Valid ProductParams params) {
        PageResponse<ProductResponse> response = productService.filterAllProducts(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailsDTO> getProduct(@PathVariable Long id) {
        ProductDetailsDTO product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductDetailsDTO> getProductBySlug(@PathVariable String slug) {
        ProductDetailsDTO product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(product);
    }

    @PostMapping(path = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(@Valid @ModelAttribute ProductCreateRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(path = "/{id}/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id, @Valid @ModelAttribute ProductUpdateRequest request) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/public/{status}")
    public ResponseEntity<Void> updateProductPublicStatus(@PathVariable Long id, @PathVariable boolean status) {
        productService.publishProduct(id, status);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkExistsProduct(@RequestParam String slug) {
        Map<String, Boolean> exists = productService.checkExistsProduct(slug);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/discount")
    public ResponseEntity<PageResponse<ProductResponse>> getDiscountProducts(
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "8") int pageSize) {
        PageResponse<ProductResponse> response = productService.getDiscountProducts(pageNum, pageSize);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/latest")
    public ResponseEntity<PageResponse<ProductResponse>> getLatestProducts(
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "8") int pageSize) {
        PageResponse<ProductResponse> response = productService.getLatestProducts(pageNum, pageSize);
        return ResponseEntity.ok(response);
    }
}
