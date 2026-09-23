package com.xdpsx.ecommerce.dtos.category;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.xdpsx.ecommerce.entities.Category;

public record CategoryTreeFilter(@Min(1) @Max(Category.MAX_DEPTH) Integer maxLevel, String sort) {}
