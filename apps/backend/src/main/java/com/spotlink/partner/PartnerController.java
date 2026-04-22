package com.spotlink.partner;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartnerController {

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    // Operator: pregled i izmena sopstvenog profila
    @GetMapping({"/operator/partner-profile", "/v1/operator/partner-profile"})
    PartnerDtos.PartnerProfileDto getMyProfile() {
        return partnerService.getMyProfile();
    }

    @PatchMapping({"/operator/partner-profile", "/v1/operator/partner-profile"})
    PartnerDtos.PartnerProfileDto updateMyProfile(@Valid @RequestBody PartnerDtos.UpdatePartnerProfileRequest request) {
        return partnerService.updateMyProfile(request);
    }

    // Admin: pregled i izmena bilo kog partner profila
    @GetMapping({"/admin/partners/{partnerProfileId}", "/v1/admin/partners/{partnerProfileId}"})
    PartnerDtos.PartnerProfileDto getProfile(@PathVariable UUID partnerProfileId) {
        return partnerService.getProfileById(partnerProfileId);
    }

    @PatchMapping({"/admin/partners/{partnerProfileId}", "/v1/admin/partners/{partnerProfileId}"})
    PartnerDtos.PartnerProfileDto updateProfile(
            @PathVariable UUID partnerProfileId,
            @Valid @RequestBody PartnerDtos.UpdatePartnerProfileRequest request) {
        return partnerService.updateProfileById(partnerProfileId, request);
    }
}
