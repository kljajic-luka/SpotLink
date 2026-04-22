package com.spotlink.user;

import com.spotlink.core.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferences extends AuditableEntity {

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String locale = "en-US";

    @Column(nullable = false)
    private boolean marketingOptIn;

    @Column(nullable = false)
    private boolean reservationAlerts = true;

    @Column(nullable = false)
    private boolean paymentAlerts = true;

    @Column(nullable = false)
    private boolean supportAlerts = true;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public boolean isMarketingOptIn() {
        return marketingOptIn;
    }

    public void setMarketingOptIn(boolean marketingOptIn) {
        this.marketingOptIn = marketingOptIn;
    }

    public boolean isReservationAlerts() {
        return reservationAlerts;
    }

    public void setReservationAlerts(boolean reservationAlerts) {
        this.reservationAlerts = reservationAlerts;
    }

    public boolean isPaymentAlerts() {
        return paymentAlerts;
    }

    public void setPaymentAlerts(boolean paymentAlerts) {
        this.paymentAlerts = paymentAlerts;
    }

    public boolean isSupportAlerts() {
        return supportAlerts;
    }

    public void setSupportAlerts(boolean supportAlerts) {
        this.supportAlerts = supportAlerts;
    }
}
