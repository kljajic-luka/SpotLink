package com.spotlink.security;

import com.spotlink.core.NotFoundException;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    public UUID userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SpotLinkPrincipal principal)) {
            throw new NotFoundException("Authenticated user not found.");
        }
        return principal.getUserId();
    }

    public User user() {
        return users.findById(userId())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found."));
    }
}
