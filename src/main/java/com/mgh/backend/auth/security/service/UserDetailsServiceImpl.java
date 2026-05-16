package com.mgh.backend.auth.security.service;

import com.mgh.backend.auth.domain.entity.UserAuth;
import com.mgh.backend.auth.repository.UserAuthRepo;
import com.mgh.backend.auth.security.adapter.UserAuthAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserAuthRepo userRepository;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        UserAuth userAuth = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        UserAuthAdapter userAuthAdapter = new UserAuthAdapter(userAuth);

        return userAuthAdapter;
    }
}
