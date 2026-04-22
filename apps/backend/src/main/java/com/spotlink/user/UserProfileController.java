package com.spotlink.user;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping({"/users/me/profile", "/v1/users/me/profile"})
    UserDtos.UserProfileDetails mine() {
        return profileService.mine();
    }

    @GetMapping({"/users/{userId}/profile", "/v1/users/{userId}/profile"})
    UserDtos.UserProfileDetails profile(@PathVariable UUID userId) {
        return profileService.publicProfile(userId);
    }

    @PatchMapping({"/users/me/profile", "/v1/users/me/profile"})
    UserDtos.UserProfileDetails updateMine(@Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return profileService.updateMine(request);
    }
}
