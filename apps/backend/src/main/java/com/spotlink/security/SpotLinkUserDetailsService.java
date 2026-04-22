package com.spotlink.security;

import com.spotlink.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SpotLinkUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public SpotLinkUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByEmailIgnoreCase(username)
                .map(SpotLinkPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }
}
