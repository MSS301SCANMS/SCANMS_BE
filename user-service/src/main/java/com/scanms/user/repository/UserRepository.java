package com.scanms.user.repository;

import com.scanms.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdentitySubject(String identitySubject);
}
