package com.tailorcards.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePokemontcgIdRequest(
        @NotBlank(message = "pokemontcgId is required")
        String pokemontcgId
) {}
