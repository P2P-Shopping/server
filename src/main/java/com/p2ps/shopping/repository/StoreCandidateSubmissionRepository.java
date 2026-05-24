package com.p2ps.shopping.repository;

import com.p2ps.shopping.model.StoreCandidateSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StoreCandidateSubmissionRepository extends JpaRepository<StoreCandidateSubmission, UUID> {
}
