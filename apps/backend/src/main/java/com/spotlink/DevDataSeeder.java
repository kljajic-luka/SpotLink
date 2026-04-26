package com.spotlink;

import com.spotlink.inventory.InventoryPoolRepository;
import com.spotlink.inventory.InventoryPoolService;
import com.spotlink.location.Address;
import com.spotlink.location.GeoCoordinates;
import com.spotlink.location.ParkingAccessType;
import com.spotlink.location.ParkingLocation;
import com.spotlink.location.ParkingLocationRepository;
import com.spotlink.location.ParkingResource;
import com.spotlink.location.ParkingResourceRepository;
import com.spotlink.location.ParkingResourceType;
import com.spotlink.operator.OperatorAccount;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.partner.ConfirmationMode;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import com.spotlink.user.UserRole;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed demo podaci za lokalni razvoj (profil: dev).
 * Idempotentno – preskace unos ako entiteti vec postoje.
 *
 * Demo nalozi:
 *   admin@spotlink.rs    / Demo1234!   (ADMIN + CUSTOMER)
 *   operator@spotlink.rs / Demo1234!   (OPERATOR + CUSTOMER)
 *   korisnik@spotlink.rs / Demo1234!   (CUSTOMER)
 *
 * Demo lokacija: Parking Trg Republike – Demo, Beograd
 *   Mesto A-01 – samo online placanje
 *   Mesto B-01 – placanje po dolasku
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final String ADMIN_EMAIL    = "admin@spotlink.rs";
    private static final String OPERATOR_EMAIL = "operator@spotlink.rs";
    private static final String CUSTOMER_EMAIL = "korisnik@spotlink.rs";
    private static final String DEMO_PASSWORD  = "Demo1234!";

    private final UserRepository users;
    private final OperatorAccountRepository operatorAccounts;
    private final ParkingLocationRepository locations;
    private final ParkingResourceRepository resources;
    private final InventoryPoolService inventoryPools;
    private final InventoryPoolRepository inventoryPoolRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(
            UserRepository users,
            OperatorAccountRepository operatorAccounts,
            ParkingLocationRepository locations,
            ParkingResourceRepository resources,
            InventoryPoolService inventoryPools,
            InventoryPoolRepository inventoryPoolRepository,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.operatorAccounts = operatorAccounts;
        this.locations = locations;
        this.resources = resources;
        this.inventoryPools = inventoryPools;
        this.inventoryPoolRepository = inventoryPoolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        User operatorUser = seedOperator();
        OperatorAccount account = seedOperatorAccount(operatorUser);
        seedCustomer();
        seedDemoLocation(account);
        log.info("[dev-seed] Demo nalozi su inicijalizovani: {} / {} / {} (lozinka: {})",
                ADMIN_EMAIL, OPERATOR_EMAIL, CUSTOMER_EMAIL, DEMO_PASSWORD);
    }

    // ── korisnici ─────────────────────────────────────────────────────────────

    private void seedAdmin() {
        if (users.existsByEmailIgnoreCase(ADMIN_EMAIL)) {
            return;
        }
        User user = new User();
        user.setEmail(ADMIN_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setFirstName("Admin");
        user.setLastName("SpotLink");
        user.setRoles(Set.of(UserRole.ADMIN, UserRole.CUSTOMER));
        user.setRegistrationStatus(RegistrationStatus.ACTIVE);
        users.save(user);
    }

    private User seedOperator() {
        return users.findByEmailIgnoreCase(OPERATOR_EMAIL).orElseGet(() -> {
            User user = new User();
            user.setEmail(OPERATOR_EMAIL);
            user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            user.setFirstName("Operater");
            user.setLastName("Demo");
            user.setRoles(Set.of(UserRole.OPERATOR, UserRole.CUSTOMER));
            user.setRegistrationStatus(RegistrationStatus.ACTIVE);
            return users.save(user);
        });
    }

    private OperatorAccount seedOperatorAccount(User operatorUser) {
        return operatorAccounts.findByUserId(operatorUser.getId()).orElseGet(() -> {
            OperatorAccount account = new OperatorAccount();
            account.setUserId(operatorUser.getId());
            account.setDisplayName("Demo Parking Beograd");
            account.setLegalName("Demo Parking d.o.o.");
            account.setSupportEmail(OPERATOR_EMAIL);
            account.setActive(true);
            return operatorAccounts.save(account);
        });
    }

    private void seedCustomer() {
        if (users.existsByEmailIgnoreCase(CUSTOMER_EMAIL)) {
            return;
        }
        User user = new User();
        user.setEmail(CUSTOMER_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setFirstName("Korisnik");
        user.setLastName("Demo");
        user.setRoles(Set.of(UserRole.CUSTOMER));
        user.setRegistrationStatus(RegistrationStatus.ACTIVE);
        users.save(user);
    }

    // ── parking lokacija ──────────────────────────────────────────────────────

    private void seedDemoLocation(OperatorAccount operator) {
        if (!locations.findByOperatorIdAndActiveTrueOrderByName(operator.getId()).isEmpty()) {
            return;
        }

        ParkingLocation location = new ParkingLocation();
        location.setOperatorId(operator.getId());
        location.setName("Parking Trg Republike – Demo");
        location.setTimezone("Europe/Belgrade");
        location.setAccessType(ParkingAccessType.SELF_PARK);
        location.setActive(true);

        Address address = new Address();
        address.setLine1("Trg Republike 5");
        address.setCity("Beograd");
        address.setRegion("Beograd");
        address.setPostalCode("11000");
        address.setCountry("RS");
        address.setFormattedAddress("Trg Republike 5, 11000 Beograd, Srbija");
        location.setAddress(address);

        GeoCoordinates coords = new GeoCoordinates();
        coords.setLatitude(new BigDecimal("44.817540"));
        coords.setLongitude(new BigDecimal("20.456180"));
        location.setCoordinates(coords);

        ParkingLocation saved = locations.save(location);
        seedResources(saved.getId());
    }

    private void seedResources(UUID locationId) {
        // Mesto A – samo online placanje (payOnArrivalEnabled = false po defaultu)
        ParkingResource spotA = buildResource(locationId, "Mesto A-01", 30_000L, "RSD");
        ParkingResource savedA = resources.save(spotA);
        var poolA = inventoryPools.syncFromResource(savedA);
        poolA.setPayOnArrivalEnabled(false);
        inventoryPoolRepository.save(poolA);

        // Mesto B – placanje po dolasku (payOnArrivalEnabled = true)
        ParkingResource spotB = buildResource(locationId, "Mesto B-01", 30_000L, "RSD");
        ParkingResource savedB = resources.save(spotB);
        var poolB = inventoryPools.syncFromResource(savedB);
        poolB.setPayOnArrivalEnabled(true);
        inventoryPoolRepository.save(poolB);
    }

    private ParkingResource buildResource(UUID locationId, String label, long hourlyRateCents, String currency) {
        ParkingResource resource = new ParkingResource();
        resource.setLocationId(locationId);
        resource.setType(ParkingResourceType.PARKING_SPOT);
        resource.setLabel(label);
        resource.setHourlyRateCents(hourlyRateCents);
        resource.setCurrency(currency);
        resource.setInstantReserve(true);
        resource.setActive(true);
        resource.setCapacity(1);
        resource.setConfirmationMode(ConfirmationMode.INSTANT);
        return resource;
    }
}
