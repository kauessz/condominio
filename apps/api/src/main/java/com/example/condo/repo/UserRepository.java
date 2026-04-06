package com.example.condo.repo;

import com.example.condo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where u.tenantId = :t and lower(u.email) = lower(:email)")
    Optional<User> findByTenantAndEmail(@Param("t") String tenantId, @Param("email") String email);

    @Query("select u from User u where u.tenantId = :tenantId and lower(u.email) = lower(:email) and (:ignoreId is null or u.id <> :ignoreId)")
    Optional<User> findByTenantIdAndEmailIgnoreCaseExcludingId(
        @Param("tenantId") String tenantId,
        @Param("email") String email,
        @Param("ignoreId") Long ignoreId
    );

    /**
     * Lista paginada de todos os usuários do tenant (SUPERUSER).
     * Permite busca por nome ou e-mail.
     * Ordena por id desc (createdAt pode ser null em registros antigos).
     */
    @Query("""
        select u from User u
        where u.tenantId = :tenantId
          and (:q is null
               or lower(u.name)  like lower(concat('%', :q, '%'))
               or lower(u.email) like lower(concat('%', :q, '%')))
        order by u.id desc
        """)
    Page<User> findAllByTenant(
        @Param("tenantId") String tenantId,
        @Param("q") String query,
        Pageable pageable
    );

    @Query("""
        select u from User u
        where u.tenantId = :tenantId
        order by u.id desc
        """)
    Page<User> findAllByTenant(
        @Param("tenantId") String tenantId,
        Pageable pageable
    );

    /**
     * Lista paginada de usuários de um condomínio específico.
     * Permite busca por nome ou e-mail.
     */
    @Query("""
        select u from User u
        where u.tenantId = :tenantId
          and u.condominiumId = :condominiumId
          and (:q is null
               or lower(u.name)  like lower(concat('%', :q, '%'))
               or lower(u.email) like lower(concat('%', :q, '%')))
        order by u.id desc
        """)
    Page<User> findByTenantAndCondominium(
        @Param("tenantId") String tenantId,
        @Param("condominiumId") Long condominiumId,
        @Param("q") String query,
        Pageable pageable
    );

    @Query("""
        select u from User u
        where u.tenantId = :tenantId
          and u.condominiumId = :condominiumId
        order by u.id desc
        """)
    Page<User> findByTenantAndCondominium(
        @Param("tenantId") String tenantId,
        @Param("condominiumId") Long condominiumId,
        Pageable pageable
    );

    /**
     * Lista paginada de usuários de um condomínio filtrando por roles.
     */
    @Query("""
        select u from User u
        where u.tenantId = :tenantId
          and u.condominiumId = :condominiumId
          and u.role in :roles
          and (:q is null
               or lower(u.name)  like lower(concat('%', :q, '%'))
               or lower(u.email) like lower(concat('%', :q, '%')))
        order by u.id desc
        """)
    Page<User> findByTenantAndCondominiumAndRoles(
        @Param("tenantId") String tenantId,
        @Param("condominiumId") Long condominiumId,
        @Param("roles") Collection<com.example.condo.security.Role> roles,
        @Param("q") String query,
        Pageable pageable
    );

    @Query("""
        select u from User u
        where u.tenantId = :tenantId
          and u.condominiumId = :condominiumId
          and u.role in :roles
        order by u.id desc
        """)
    Page<User> findByTenantAndCondominiumAndRoles(
        @Param("tenantId") String tenantId,
        @Param("condominiumId") Long condominiumId,
        @Param("roles") Collection<com.example.condo.security.Role> roles,
        Pageable pageable
    );

    /**
     * Busca usuário por tenant e ID.
     */
    Optional<User> findByTenantIdAndId(String tenantId, Long id);

    /**
     * Verifica se e-mail já existe no tenant.
     */
    boolean existsByTenantIdAndEmail(String tenantId, String email);

    @Query("""
        select u from User u
        where u.tenantId = :tenantId
          and u.condominiumId = :condominiumId
          and u.role in :roles
        order by lower(u.name) asc, u.id asc
        """)
    List<User> findByTenantAndCondominiumAndRolesOrdered(
        @Param("tenantId") String tenantId,
        @Param("condominiumId") Long condominiumId,
        @Param("roles") Collection<com.example.condo.security.Role> roles
    );
}
