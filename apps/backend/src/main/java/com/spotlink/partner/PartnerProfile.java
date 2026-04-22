package com.spotlink.partner;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "partner_profiles")
public class PartnerProfile extends AuditableEntity {

    @Column(nullable = false, unique = true)
    private UUID operatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerType partnerType = PartnerType.PILOT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OnboardingStatus onboardingStatus = OnboardingStatus.PENDING;

    @Column
    private Integer pilotFitScore;

    @Column(length = 160)
    private String contactName;

    @Column(length = 320)
    private String contactEmail;

    @Column(length = 50)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConfirmationMode defaultConfirmationMode = ConfirmationMode.INSTANT;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public UUID getOperatorId() { return operatorId; }
    public void setOperatorId(UUID operatorId) { this.operatorId = operatorId; }

    public PartnerType getPartnerType() { return partnerType; }
    public void setPartnerType(PartnerType partnerType) { this.partnerType = partnerType; }

    public OnboardingStatus getOnboardingStatus() { return onboardingStatus; }
    public void setOnboardingStatus(OnboardingStatus onboardingStatus) { this.onboardingStatus = onboardingStatus; }

    public Integer getPilotFitScore() { return pilotFitScore; }
    public void setPilotFitScore(Integer pilotFitScore) { this.pilotFitScore = pilotFitScore; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public ConfirmationMode getDefaultConfirmationMode() { return defaultConfirmationMode; }
    public void setDefaultConfirmationMode(ConfirmationMode defaultConfirmationMode) { this.defaultConfirmationMode = defaultConfirmationMode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
