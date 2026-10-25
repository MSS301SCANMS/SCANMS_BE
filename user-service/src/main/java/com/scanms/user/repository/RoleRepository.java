package com.scanms.user.repository;

import com.scanms.user.constant.RoleName;
import com.scanms.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Set;

public interface RoleRepository extends JpaRepository<Role, String> {
    Set<Role> findAllByNameIn(Collection<RoleName> names);
}
