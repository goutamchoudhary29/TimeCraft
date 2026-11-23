package com.timecraft.security;

import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.authentication.entity.Role;
import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class TimeCraftPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final boolean emailVerified;
    private final AccountStatus accountStatus;
    private final boolean locked;
    private final Set<RoleName> roleNames;
    private final Collection<? extends GrantedAuthority> authorities;

    public TimeCraftPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.emailVerified = user.isEmailVerified();
        this.accountStatus = user.getAccountStatus();
        this.locked = user.isLocked();
        this.roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
        this.authorities = this.roleNames.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasRole(RoleName role) {
        return roleNames.contains(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}
