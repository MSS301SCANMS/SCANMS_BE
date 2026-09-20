package com.scanms.user.config;

import com.scanms.user.constant.RoleName;
import com.scanms.user.entity.Role;
import com.scanms.user.repository.RoleRepository;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RoleDataInitializer implements ApplicationRunner {
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<RoleName> roleNames = EnumSet.allOf(RoleName.class);
        roleRepository.findAllByNameIn(roleNames).stream()
                .map(Role::getName)
                .forEach(roleNames::remove);

        roleRepository.saveAll(roleNames.stream()
                .map(name -> Role.builder().name(name).build())
                .toList());
    }
}
