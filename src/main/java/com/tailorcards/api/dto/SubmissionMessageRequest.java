package com.tailorcards.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SubmissionMessageRequest(
    @Email(message = "Sender email must be a valid email address")
    String senderEmail,

    String senderRole,

    @NotBlank(message = "Message content is required")
    String message
) {}
