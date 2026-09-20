package com.scanms.user.repository;

import com.scanms.user.constant.RoleName;
import com.scanms.user.entity.Role;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Set<Role> findAllByNameIn(Collection<RoleName> names);
}
