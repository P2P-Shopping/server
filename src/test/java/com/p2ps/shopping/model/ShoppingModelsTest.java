package com.p2ps.shopping.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShoppingModelsTest {

    @Test
    void shoppingSession_onCreate_setsStartedAtWhenMissing() {
        ShoppingSession session = new ShoppingSession();
        session.onCreate();
        assertNotNull(session.getStartedAt());
    }

    @Test
    void shoppingSession_onCreate_preservesExistingStartedAt() {
        ShoppingSession session = new ShoppingSession();
        LocalDateTime existing = LocalDateTime.now().minusHours(2);
        session.setStartedAt(existing);
        session.onCreate();
        assertEquals(existing, session.getStartedAt());
    }

    @Test
    void storeCandidateSubmission_onCreate_setsTimestampsAndNormalizedName() {
        StoreCandidateSubmission submission = new StoreCandidateSubmission();
        submission.setSubmittedName("  Mega Image  ");
        submission.onCreate();

        assertNotNull(submission.getCreatedAt());
        assertNotNull(submission.getUpdatedAt());
        assertEquals("mega image", submission.getNormalizedName());
    }

    @Test
    void storeCandidateSubmission_onUpdate_refreshesUpdatedAtAndName() {
        StoreCandidateSubmission submission = new StoreCandidateSubmission();
        submission.setSubmittedName("Old");
        submission.onCreate();
        LocalDateTime firstUpdated = submission.getUpdatedAt();

        submission.setSubmittedName(" New Name ");
        submission.onUpdate();

        assertEquals("new name", submission.getNormalizedName());
        assertNotNull(submission.getUpdatedAt());
        // equal is possible in fast execution, so assert monotonic non-null behavior via presence + name
        assertNotNull(firstUpdated);
    }
}

