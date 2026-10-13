package com.scanms.user.repository;

import com.scanms.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByIdentitySubject(String identitySubject);
    Optional<User> findByEmail(String email);
}
