package com.spotlink.partner;

import com.spotlink.core.NotFoundException;
import com.spotlink.operator.OperatorAccount;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.security.CurrentUserService;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerService {

    private final PartnerProfileRepository partnerProfiles;
    private final OperatorAccountRepository operators;
    private final CurrentUserService currentUser;

    public PartnerService(
            PartnerProfileRepository partnerProfiles,
            OperatorAccountRepository operators,
            CurrentUserService currentUser) {
        this.partnerProfiles = partnerProfiles;
        this.operators = operators;
        this.currentUser = currentUser;
    }

    // Kreira podrazumevani pilot partner profil za novog operatora
    @Transactional
    public PartnerProfile createDefaultProfile(UUID operatorId) {
        PartnerProfile profile = new PartnerProfile();
        profile.setOperatorId(operatorId);
        profile.setPartnerType(PartnerType.PILOT);
        profile.setOnboardingStatus(OnboardingStatus.PENDING);
        profile.setDefaultConfirmationMode(ConfirmationMode.INSTANT);
        profile.setActive(true);
        return partnerProfiles.save(profile);
    }

    @Transactional(readOnly = true)
    public PartnerDtos.PartnerProfileDto getMyProfile() {
        return toDto(requireCurrentProfile());
    }

    @Transactional
    public PartnerDtos.PartnerProfileDto updateMyProfile(PartnerDtos.UpdatePartnerProfileRequest request) {
        PartnerProfile profile = requireCurrentProfile();
        applyOperatorUpdatableFields(profile, request);
        return toDto(profile);
    }

    @Transactional(readOnly = true)
    public PartnerDtos.PartnerProfileDto getProfileById(UUID partnerProfileId) {
        return toDto(partnerProfiles.findById(partnerProfileId)
                .orElseThrow(() -> new NotFoundException("Partner profile was not found.")));
    }

    @Transactional
    public PartnerDtos.PartnerProfileDto updateProfileById(UUID partnerProfileId, PartnerDtos.UpdatePartnerProfileRequest request) {
        PartnerProfile profile = partnerProfiles.findById(partnerProfileId)
                .orElseThrow(() -> new NotFoundException("Partner profile was not found."));
        applyAllFields(profile, request);
        return toDto(profile);
    }

    private void applyOperatorUpdatableFields(PartnerProfile profile, PartnerDtos.UpdatePartnerProfileRequest request) {
        if (request.contactName() != null) profile.setContactName(request.contactName());
        if (request.contactEmail() != null) profile.setContactEmail(request.contactEmail());
        if (request.contactPhone() != null) profile.setContactPhone(request.contactPhone());
        if (request.defaultConfirmationMode() != null) profile.setDefaultConfirmationMode(request.defaultConfirmationMode());
        if (request.notes() != null) profile.setNotes(request.notes());
    }

    private void applyAllFields(PartnerProfile profile, PartnerDtos.UpdatePartnerProfileRequest request) {
        applyOperatorUpdatableFields(profile, request);
        if (request.partnerType() != null) profile.setPartnerType(request.partnerType());
        if (request.onboardingStatus() != null) profile.setOnboardingStatus(request.onboardingStatus());
        if (request.pilotFitScore() != null) profile.setPilotFitScore(request.pilotFitScore());
        if (request.active() != null) profile.setActive(request.active());
    }

    private PartnerProfile requireCurrentProfile() {
        OperatorAccount operator = operators.findByUserId(currentUser.userId())
                .orElseThrow(() -> new AccessDeniedException("Operator account is required."));
        return partnerProfiles.findByOperatorId(operator.getId())
                .orElseThrow(() -> new NotFoundException("Partner profile was not found."));
    }

    private PartnerDtos.PartnerProfileDto toDto(PartnerProfile profile) {
        return new PartnerDtos.PartnerProfileDto(
                profile.getId(),
                profile.getOperatorId(),
                profile.getPartnerType(),
                profile.getOnboardingStatus(),
                profile.getPilotFitScore(),
                profile.getContactName(),
                profile.getContactEmail(),
                profile.getContactPhone(),
                profile.getDefaultConfirmationMode(),
                profile.getNotes(),
                profile.isActive(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
