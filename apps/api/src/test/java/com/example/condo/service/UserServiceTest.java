package com.example.condo.service;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.user.UserResponse;
import com.example.condo.entity.User;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.UserRepository;
import com.example.condo.security.Role;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CondominiumRepository condominiumRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Captor
    private ArgumentCaptor<Collection<Role>> roleSetCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    void superuserShouldListAllUsersFromTenant() {
        UserContext.set(new UserContext.Data("SUPERUSER", null, null, 1L));
        when(userRepository.findAllByTenant(eq("tenant-a"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user(1L, Role.ADMIN, 10L), user(2L, Role.MORADOR, 20L))));

        PageResponse<UserResponse> response = userService.list(null, 0, 20);

        assertEquals(2, response.content().size());
        verify(userRepository).findAllByTenant(eq("tenant-a"), any(Pageable.class));
        verify(userRepository, never()).findByTenantAndCondominium(anyString(), anyLong(), anyString(), any(Pageable.class));
    }

    @Test
    void adminShouldListOnlyUsersFromOwnCondominium() {
        UserContext.set(new UserContext.Data("ADMIN", 10L, null, 2L));
        when(userRepository.findByTenantAndCondominium(eq("tenant-a"), eq(10L), eq("ana"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user(3L, Role.ADMIN, 10L))));

        PageResponse<UserResponse> response = userService.list("ana", 0, 20);

        assertEquals(1, response.content().size());
        assertTrue(response.content().stream().allMatch(user -> Long.valueOf(10L).equals(user.condominiumId())));
        verify(userRepository).findByTenantAndCondominium(eq("tenant-a"), eq(10L), eq("ana"), any(Pageable.class));
        verify(userRepository, never()).findAllByTenant(anyString(), anyString(), any(Pageable.class));
    }

    @Test
    void sindicoShouldListOnlyResidentsFromOwnCondominium() {
        UserContext.set(new UserContext.Data("SINDICO", 10L, 101L, 3L));
        when(userRepository.findByTenantAndCondominiumAndRoles(eq("tenant-a"), eq(10L), anyCollection(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user(4L, Role.MORADOR, 10L))));

        PageResponse<UserResponse> response = userService.list(null, 0, 20);

        assertEquals(1, response.content().size());
        assertEquals("MORADOR", response.content().getFirst().role());
        verify(userRepository).findByTenantAndCondominiumAndRoles(eq("tenant-a"), eq(10L), roleSetCaptor.capture(), any(Pageable.class));
        assertEquals(Set.of(Role.MORADOR), roleSetCaptor.getValue());
    }

    @Test
    void shouldPreserveTenantIsolationForAdminListing() {
        UserContext.set(new UserContext.Data("ADMIN", 55L, null, 5L));
        when(userRepository.findByTenantAndCondominium(eq("tenant-a"), eq(55L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user(5L, Role.PORTARIA, 55L), user(6L, Role.MORADOR, 55L))));

        PageResponse<UserResponse> response = userService.list(null, 0, 20);

        assertTrue(response.content().stream().allMatch(user -> Long.valueOf(55L).equals(user.condominiumId())));
        verify(userRepository).findByTenantAndCondominium(eq("tenant-a"), eq(55L), any(Pageable.class));
    }

    @Test
    void shouldSanitizePaginationParameters() {
        UserContext.set(new UserContext.Data("SUPERUSER", null, null, 1L));
        when(userRepository.findAllByTenant(eq("tenant-a"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        userService.list(null, -4, 500);

        verify(userRepository).findAllByTenant(eq("tenant-a"), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void shouldUseFilteredQueryWhenSearchIsPresent() {
        UserContext.set(new UserContext.Data("SUPERUSER", null, null, 1L));
        when(userRepository.findAllByTenant(eq("tenant-a"), eq("joao"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user(7L, Role.MORADOR, 10L))));

        PageResponse<UserResponse> response = userService.list("joao", 0, 20);

        assertEquals(1, response.content().size());
        verify(userRepository).findAllByTenant(eq("tenant-a"), eq("joao"), any(Pageable.class));
        verify(userRepository, never()).findAllByTenant(eq("tenant-a"), any(Pageable.class));
    }

    private User user(Long id, Role role, Long condominiumId) {
        User user = new User();
        user.setId(id);
        user.setTenantId("tenant-a");
        user.setName("User " + id);
        user.setEmail("user" + id + "@test.com");
        user.setRole(role);
        user.setCondominiumId(condominiumId);
        user.setMustChangePassword(false);
        return user;
    }
}
