package com.tailorcards.api.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuylistEntityTest {

    @Test
    void defaultStatus_shouldBePending() {
        BuylistSubmission submission = BuylistSubmission.builder()
                .customerEmail("test@example.com")
                .cardName("Black Lotus")
                .build();

        assertEquals("PENDING", submission.getStatus());
    }

    @Test
    void prePersist_shouldGenerateTrackingTokenAndTimestamp() {
        BuylistSubmission submission = new BuylistSubmission();
        submission.prePersist();

        assertNotNull(submission.getTrackingToken());
        assertFalse(submission.getTrackingToken().isBlank());
        assertNotNull(submission.getCreatedAt());
        assertEquals("PENDING", submission.getStatus());
    }

    @Test
    void validateStatus_shouldAllowValidStatuses() {
        for (String status : BuylistSubmission.VALID_STATUSES) {
            BuylistSubmission submission = new BuylistSubmission();
            submission.setStatus(status);
            assertEquals(status, submission.getStatus());
            assertDoesNotThrow(submission::validateAndNormalizeStatus);
        }
    }

    @Test
    void setStatus_shouldThrowExceptionForInvalidStatus() {
        BuylistSubmission submission = new BuylistSubmission();
        assertThrows(IllegalArgumentException.class, () -> submission.setStatus("DISCARDED"));
    }

    @Test
    void submissionMessage_prePersist_shouldSetDefaults() {
        SubmissionMessage message = SubmissionMessage.builder()
                .senderEmail("customer@example.com")
                .message("Hello")
                .build();

        message.prePersist();

        assertNotNull(message.getCreatedAt());
        assertEquals("CUSTOMER", message.getSenderRole());
    }

    @Test
    void submissionMessage_invalidRole_shouldThrowException() {
        SubmissionMessage message = SubmissionMessage.builder()
                .senderEmail("bot@example.com")
                .senderRole("BOT")
                .message("Hello")
                .build();

        assertThrows(IllegalArgumentException.class, message::prePersist);
    }
}
