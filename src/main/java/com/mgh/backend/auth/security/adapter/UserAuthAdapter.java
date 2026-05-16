package com.mgh.backend.auth.security.adapter;

import com.mgh.backend.auth.domain.entity.UserAuth;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserAuthAdapter implements UserDetails {

    private final UserAuth userAuth;

    public UserAuthAdapter(UserAuth userAuth) {
        this.userAuth = userAuth;
    }

    public UserAuth getUserAuth() {
        return userAuth;
    }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + userAuth.getRole().name())
        );
    }


    @Override
    public String getPassword() {
        return userAuth.getPassword();
    }

    @Override
    public String getUsername() {
        return userAuth.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !userAuth.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return userAuth.isEnabled();
    }
}