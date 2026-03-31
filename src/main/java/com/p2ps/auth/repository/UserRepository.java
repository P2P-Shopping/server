package com.p2ps.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.p2ps.auth.model.Users;
import java.util.Optional;

public interface UserRepository extends JpaRepository<Users, Integer> {
    Optional<Users> findByEmail(String email);
    boolean existsByEmail(String email);
}