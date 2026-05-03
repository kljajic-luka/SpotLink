package com.spotlink.location;

import com.spotlink.core.ApiPage;
import com.spotlink.core.NotFoundException;
import com.spotlink.core.ValidationException;
import com.spotlink.inventory.InventoryPool;
import com.spotlink.inventory.InventoryPoolService;
import com.spotlink.operator.OperatorAccount;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.reservation.BookingHoldRepository;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

    private static final double DEFAULT_RADIUS_KM = 10.0;

    private static final Collection<ReservationStatus> CONFIRMED_BLOCKING_STATUSES = List.of(
            ReservationStatus.PENDING_OPERATOR_CONFIRMATION,
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE,
            ReservationStatus.DISPUTED,
            ReservationStatus.NO_SHOW);

    private final ParkingLocationRepository locations;
    private final ParkingResourceRepository resources;
    private final OperatorAccountRepository operators;
    private final LocationHoursRepository locationHours;
    private final AvailabilityExceptionRepository availabilityExceptions;
    private final ReservationRepository reservations;
    private final BookingHoldRepository bookingHolds;
    private final CurrentUserService currentUser;
    private final LocationMapper mapper;
    private final InventoryPoolService inventoryPools;
    private final Clock clock;

    public LocationService(
            ParkingLocationRepository locations,
            ParkingResourceRepository resources,
            OperatorAccountRepository operators,
            LocationHoursRepository locationHours,
            AvailabilityExceptionRepository availabilityExceptions,
            ReservationRepository reservations,
            BookingHoldRepository bookingHolds,
            CurrentUserService currentUser,
            LocationMapper mapper,
            InventoryPoolService inventoryPools,
            Clock clock) {
        this.locations = locations;
        this.resources = resources;
        this.operators = operators;
        this.locationHours = locationHours;
        this.availabilityExceptions = availabilityExceptions;
        this.reservations = reservations;
        this.bookingHolds = bookingHolds;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.inventoryPools = inventoryPools;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ApiPage<LocationDtos.LocationSearchResult> search(LocationDtos.SearchFilters filters) {
        // Validacija: oba koordinata ili ni jedan
        boolean hasLat = filters.latitude() != null;
        boolean hasLon = filters.longitude() != null;
        if (hasLat != hasLon) {
            throw new ValidationException("coordinates", "Both latitude and longitude must be provided together.");
        }
        // Validacija: oba vremenska trenutka ili ni jedan
        boolean hasStartsAt = filters.startsAt() != null;
        boolean hasEndsAt = filters.endsAt() != null;
        if (hasStartsAt != hasEndsAt) {
            throw new ValidationException("timeWindow", "Both startsAt and endsAt must be provided together.");
        }

        boolean hasCoords = hasLat;
        double radiusKm = hasCoords
                ? (filters.radiusKm() != null ? filters.radiusKm().doubleValue() : DEFAULT_RADIUS_KM)
                : Double.MAX_VALUE;

        int page = filters.page() == null ? 0 : filters.page();
        int size = filters.size() == null ? 20 : Math.min(filters.size(), 100);

        // Dohvatamo kandidate iz DB-a: bounding-box filter (ako ima koordinate) ili tekst filter sa limitom
        List<ParkingLocation> allLocations;
        if (hasCoords) {
            // Bounding box ≈ radius / 111 stepeni po osi (gruba aproksimacija)
            double latDelta = radiusKm / 111.0;
            double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(filters.latitude().doubleValue())));
            BigDecimal minLat = filters.latitude().subtract(BigDecimal.valueOf(latDelta)).setScale(6, RoundingMode.FLOOR);
            BigDecimal maxLat = filters.latitude().add(BigDecimal.valueOf(latDelta)).setScale(6, RoundingMode.CEILING);
            BigDecimal minLon = filters.longitude().subtract(BigDecimal.valueOf(lonDelta)).setScale(6, RoundingMode.FLOOR);
            BigDecimal maxLon = filters.longitude().add(BigDecimal.valueOf(lonDelta)).setScale(6, RoundingMode.CEILING);
            allLocations = locations.searchCandidates(blankToNull(filters.query()), minLat, maxLat, minLon, maxLon);
        } else {
            // Bez koordinata – tekst filter, ograniceno na 500 zapisa
            allLocations = locations.search(blankToNull(filters.query()), PageRequest.of(0, 500)).getContent();
        }

        // Racunamo udaljenost i filtriramo po radijusu
        List<ScoredLocation> scored = allLocations.stream()
                .map(loc -> new ScoredLocation(loc, hasCoords ? distanceKmValue(loc, filters) : null))
                .filter(sl -> sl.distanceKm() == null || sl.distanceKm() <= radiusKm)
                .sorted(hasCoords
                        ? Comparator.comparingDouble(sl -> sl.distanceKm())
                        : Comparator.comparing(sl -> sl.location().getName()))
                .toList();

        // Dohvatamo resurse za filtrirane lokacije
        List<UUID> locationIds = scored.stream().map(sl -> sl.location().getId()).toList();
        Map<UUID, List<ParkingResource>> resourcesByLocation = locationIds.isEmpty()
                ? Map.of()
                : resources.findByLocationIdInAndActiveTrue(locationIds)
                        .stream()
                        .filter(resource -> matchesResourceFilters(resource, filters))
                        .collect(Collectors.groupingBy(ParkingResource::getLocationId));
                Map<UUID, InventoryPool> poolsByResourceId = inventoryPools.findByLocationIds(locationIds).stream()
                    .filter(pool -> pool.getSourceResourceId() != null)
                    .collect(Collectors.toMap(InventoryPool::getSourceResourceId, pool -> pool, (left, right) -> left));

        // Gradimo rezultate sa dostupnoscu
        List<LocationDtos.LocationSearchResult> results = new ArrayList<>();
        for (ScoredLocation sl : scored) {
            List<ParkingResource> activeResources = resourcesByLocation.getOrDefault(sl.location().getId(), List.of());

            List<ParkingResource> availableResources;
            long availableCount;
            if (hasStartsAt) {
                // Filtriramo resurse koji su dostupni u trazenom periodu
                boolean isBlacked = availabilityExceptions.countOverlapping(
                        sl.location().getId(), filters.startsAt(), filters.endsAt()) > 0;
                if (isBlacked) {
                    continue; // cela lokacija je u blokadi
                }
                // Proveravamo radno vreme
                if (!isWithinHours(sl.location(), filters.startsAt(), filters.endsAt())) {
                    continue;
                }
                availableResources = activeResources.stream()
                    .filter(r -> availableCapacity(poolsByResourceId.get(r.getId()), filters) > 0)
                        .toList();
                availableCount = availableResources.stream()
                    .mapToLong(r -> availableCapacity(poolsByResourceId.get(r.getId()), filters))
                        .sum();
            } else {
                availableResources = activeResources;
                availableCount = activeResources.stream()
                    .map(r -> poolsByResourceId.getOrDefault(r.getId(), null))
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(InventoryPool::getBaseCapacity)
                    .sum();
            }

            if (hasStartsAt && availableResources.isEmpty()) {
                continue; // nema dostupnih resursa za trazeni period
            }

            Long startingPrice = availableResources.stream()
                    .map(ParkingResource::getHourlyRateCents)
                    .min(Comparator.naturalOrder())
                    .orElse(null);

            results.add(new LocationDtos.LocationSearchResult(
                    mapper.toDto(sl.location()),
                    availableResources.stream()
                            .map(resource -> mapper.toDto(resource, poolsByResourceId.get(resource.getId())))
                            .toList(),
                    sl.distanceKm(),
                    startingPrice,
                    availableCount));
        }

        // Paginacija nad filtriranim rezultatima
        int total = results.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<LocationDtos.LocationSearchResult> pageContent = results.subList(fromIndex, toIndex);

        return ApiPage.of(pageContent, page, size, total);
    }

    private record ScoredLocation(ParkingLocation location, Double distanceKm) {}

    private long availableCapacity(InventoryPool pool, LocationDtos.SearchFilters filters) {
        if (pool == null) {
            return 0;
        }
        InventoryPoolService.AvailabilityDecision decision = inventoryPools.availabilityForWindow(pool, filters.startsAt(), filters.endsAt());
        if (decision.sellableCapacity() <= 0) {
            return 0;
        }
        long overlappingReservations = reservations.countPoolOverlaps(
                pool.getId(),
                filters.startsAt(),
                filters.endsAt(),
                CONFIRMED_BLOCKING_STATUSES);
        long overlappingHolds = bookingHolds.countActiveOverlaps(pool.getId(), filters.startsAt(), filters.endsAt(), Instant.now(clock));
        return Math.max(0, decision.sellableCapacity() - overlappingReservations - overlappingHolds);
    }

    private boolean isWithinHours(ParkingLocation location, java.time.Instant startsAt, java.time.Instant endsAt) {
        List<LocationHours> hours = locationHours.findByLocationIdOrderByDayOfWeek(location.getId());
        if (hours.isEmpty()) {
            return true; // nema definisanog radnog vremena - tretiramo kao uvek otvoreno
        }
        try {
            ZoneId zone = ZoneId.of(location.getTimezone());
            ZonedDateTime startLocal = startsAt.atZone(zone);
            ZonedDateTime endLocal = endsAt.atZone(zone);
            DayOfWeek startDay = startLocal.getDayOfWeek();
            DayOfWeek endDay = endLocal.getDayOfWeek();

            return hours.stream().anyMatch(h -> {
                DayOfWeek hourDay = DayOfWeek.valueOf(h.getDayOfWeek());
                if (hourDay != startDay && hourDay != endDay) return false;
                LocalTime open = LocalTime.parse(h.getOpenTime());
                LocalTime close = LocalTime.parse(h.getCloseTime());
                LocalTime reqStart = startLocal.toLocalTime();
                LocalTime reqEnd = endLocal.toLocalTime();
                return !reqStart.isBefore(open) && !reqEnd.isAfter(close);
            });
        } catch (Exception e) {
            return true; // ako ne mozemo da parsiramo, ne blokiramo
        }
    }

    @Transactional(readOnly = true)
    public LocationDtos.ParkingLocationDto getLocation(UUID locationId) {
        return mapper.toDto(locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found.")));
    }

    @Transactional(readOnly = true)
    public List<LocationDtos.ParkingResourceDto> getResources(UUID locationId) {
        if (!locations.existsById(locationId)) {
            throw new NotFoundException("Parking location was not found.");
        }
        Map<UUID, InventoryPool> poolsByResourceId = inventoryPools.findByLocationIds(List.of(locationId)).stream()
                .filter(pool -> pool.getSourceResourceId() != null)
                .collect(Collectors.toMap(InventoryPool::getSourceResourceId, pool -> pool, (left, right) -> left));
        return resources.findByLocationIdAndActiveTrueOrderByLabel(locationId).stream()
                .map(resource -> mapper.toDto(resource, poolsByResourceId.get(resource.getId())))
                .toList();
    }

    @Transactional
    public LocationDtos.ParkingLocationDto createLocation(LocationDtos.UpsertLocationRequest request) {
        OperatorAccount operator = currentOperator();
        ParkingLocation location = new ParkingLocation();
        location.setOperatorId(operator.getId());
        apply(location, request);
        return mapper.toDto(locations.save(location));
    }

    @Transactional
    public LocationDtos.ParkingLocationDto updateLocation(UUID locationId, LocationDtos.UpsertLocationRequest request) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        apply(location, request);
        return mapper.toDto(location);
    }

    @Transactional
    public LocationDtos.ParkingResourceDto createResource(UUID locationId, LocationDtos.UpsertResourceRequest request) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        ParkingResource resource = new ParkingResource();
        resource.setLocationId(locationId);
        apply(resource, request);
        ParkingResource saved = resources.save(resource);
        InventoryPool pool = inventoryPools.syncFromResource(saved);
        return mapper.toDto(saved, pool);
    }

    @Transactional
    public LocationDtos.ParkingResourceDto updateResource(UUID locationId, UUID resourceId, LocationDtos.UpsertResourceRequest request) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        ParkingResource resource = resources.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("Parking resource was not found."));
        if (!resource.getLocationId().equals(locationId)) {
            throw new NotFoundException("Parking resource was not found.");
        }
        apply(resource, request);
        InventoryPool pool = inventoryPools.syncFromResource(resource);
        return mapper.toDto(resource, pool);
    }

    public ParkingResource requireResource(UUID resourceId) {
        return resources.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("Parking resource was not found."));
    }

    public ParkingResource requireResourceForUpdate(UUID resourceId) {
        return resources.findByIdForUpdate(resourceId)
                .orElseThrow(() -> new NotFoundException("Parking resource was not found."));
    }

    public ParkingLocation requireLocation(UUID locationId) {
        return locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
    }

    private void apply(ParkingLocation location, LocationDtos.UpsertLocationRequest request) {
        location.setName(request.name());
        location.setAddress(mapper.toEntity(request.address()));
        location.setCoordinates(mapper.toEntity(request.coordinates()));
        location.setTimezone(request.timezone());
        location.setAccessType(request.accessType());
        location.setPublicNotes(request.publicNotes());
        location.setActive(request.active() == null || request.active());
    }

    private void apply(ParkingResource resource, LocationDtos.UpsertResourceRequest request) {
        resource.setType(request.type());
        resource.setLabel(request.label());
        resource.setFloor(request.floor());
        resource.setBayNumber(request.bayNumber());
        if (request.fitRule() != null) {
            resource.setMaxHeightMeters(request.fitRule().maxHeightMeters());
            resource.setMaxLengthMeters(request.fitRule().maxLengthMeters());
            resource.setAllowedVehicleTypes(mapper.vehicleTypesToCsv(request.fitRule().allowedVehicleTypes()));
            resource.setEvOnly(Boolean.TRUE.equals(request.fitRule().evOnly()));
        }
        resource.setHourlyRateCents(request.hourlyRateCents());
        resource.setDailyRateCents(request.dailyRateCents());
        resource.setCurrency(request.currency());
        resource.setInstantReserve(request.instantReserve() == null || request.instantReserve());
        resource.setActive(request.active() == null || request.active());
        if (request.capacity() != null) resource.setCapacity(request.capacity());
        if (request.confirmationMode() != null) resource.setConfirmationMode(request.confirmationMode());
    }

    private boolean matchesResourceFilters(ParkingResource resource, LocationDtos.SearchFilters filters) {
        if (filters.resourceTypes() != null && !filters.resourceTypes().isEmpty() && !filters.resourceTypes().contains(resource.getType())) {
            return false;
        }
        if (Boolean.TRUE.equals(filters.evChargingRequired()) && resource.getType() != ParkingResourceType.EV_CHARGER) {
            return false;
        }
        return true;
    }

    private OperatorAccount currentOperator() {
        return operators.findByUserId(currentUser.userId())
                .orElseThrow(() -> new AccessDeniedException("Operator account is required."));
    }

    private void requireOperator(ParkingLocation location) {
        OperatorAccount operator = currentOperator();
        if (!location.getOperatorId().equals(operator.getId())) {
            throw new AccessDeniedException("Parking location does not belong to the current operator.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Double distanceKmValue(ParkingLocation location, LocationDtos.SearchFilters filters) {
        if (filters.latitude() == null || filters.longitude() == null) {
            return null;
        }
        double lat1 = Math.toRadians(filters.latitude().doubleValue());
        double lat2 = Math.toRadians(location.getCoordinates().getLatitude().doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(location.getCoordinates().getLongitude().doubleValue() - filters.longitude().doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(6371.0 * c).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // Radno vreme lokacije

    @Transactional(readOnly = true)
    public List<LocationDtos.LocationHoursDto> getLocationHours(UUID locationId) {
        requireLocationExists(locationId);
        return locationHours.findByLocationIdOrderByDayOfWeek(locationId).stream()
                .map(h -> new LocationDtos.LocationHoursDto(h.getId(), h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime()))
                .toList();
    }

    @Transactional
    public List<LocationDtos.LocationHoursDto> setLocationHours(UUID locationId, LocationDtos.UpsertLocationHoursRequest request) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        for (LocationDtos.LocationHoursEntry entry : request.entries()) {
            try {
                DayOfWeek.valueOf(entry.dayOfWeek().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("dayOfWeek", "Nevalidan dan: " + entry.dayOfWeek());
            }
            LocalTime open = LocalTime.parse(entry.openTime());
            LocalTime close = LocalTime.parse(entry.closeTime());
            if (!open.isBefore(close)) {
                throw new ValidationException("closeTime", "Vreme zatvaranja mora biti posle vremena otvaranja.");
            }
        }
        locationHours.deleteByLocationId(locationId);
        List<LocationHours> saved = request.entries().stream().map(entry -> {
            LocationHours h = new LocationHours();
            h.setLocationId(locationId);
            h.setDayOfWeek(entry.dayOfWeek());
            h.setOpenTime(entry.openTime());
            h.setCloseTime(entry.closeTime());
            return locationHours.save(h);
        }).toList();
        return saved.stream()
                .map(h -> new LocationDtos.LocationHoursDto(h.getId(), h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime()))
                .toList();
    }

    // Izuzeci dostupnosti (blokade)

    @Transactional(readOnly = true)
    public List<LocationDtos.AvailabilityExceptionDto> getAvailabilityExceptions(UUID locationId) {
        requireLocationExists(locationId);
        return availabilityExceptions.findByLocationIdOrderByStartsAt(locationId).stream()
                .map(e -> new LocationDtos.AvailabilityExceptionDto(e.getId(), e.getLabel(), e.getStartsAt(), e.getEndsAt()))
                .toList();
    }

    @Transactional
    public LocationDtos.AvailabilityExceptionDto createAvailabilityException(UUID locationId, LocationDtos.CreateAvailabilityExceptionRequest request) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new ValidationException("endsAt", "Vreme zavrsetka mora biti posle vremena pocetka.");
        }
        AvailabilityException ex = new AvailabilityException();
        ex.setLocationId(locationId);
        ex.setLabel(request.label());
        ex.setStartsAt(request.startsAt());
        ex.setEndsAt(request.endsAt());
        AvailabilityException saved = availabilityExceptions.save(ex);
        return new LocationDtos.AvailabilityExceptionDto(saved.getId(), saved.getLabel(), saved.getStartsAt(), saved.getEndsAt());
    }

    @Transactional
    public void deleteAvailabilityException(UUID locationId, UUID exceptionId) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        AvailabilityException ex = availabilityExceptions.findById(exceptionId)
                .orElseThrow(() -> new NotFoundException("Availability exception was not found."));
        if (!ex.getLocationId().equals(locationId)) {
            throw new NotFoundException("Availability exception was not found.");
        }
        availabilityExceptions.delete(ex);
    }

    private void requireLocationExists(UUID locationId) {
        if (!locations.existsById(locationId)) {
            throw new NotFoundException("Parking location was not found.");
        }
    }
}
