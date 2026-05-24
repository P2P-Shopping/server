package com.p2ps.shopping.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.service.StorePriceService;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ShoppingListRepository;
import com.p2ps.shopping.dto.ShoppingSessionDTO;
import com.p2ps.shopping.dto.StartShoppingRequest;
import com.p2ps.shopping.model.ShoppingSession;
import com.p2ps.shopping.model.ShoppingSessionStatus;
import com.p2ps.shopping.model.StoreCandidateSubmission;
import com.p2ps.shopping.model.StoreSubmissionStatus;
import com.p2ps.shopping.repository.ShoppingSessionRepository;
import com.p2ps.shopping.repository.StoreCandidateSubmissionRepository;
import com.p2ps.store.model.StoreGeofence;
import com.p2ps.store.repository.StoreGeofenceRepository;
import com.p2ps.store.service.OverpassService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShoppingSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ShoppingSessionService.class);
    private static final String SHOPPING_LIST_NOT_FOUND = "Shopping list not found";
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private final ShoppingSessionRepository shoppingSessionRepository;
    private final StoreCandidateSubmissionRepository storeCandidateSubmissionRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final UserRepository userRepository;
    private final StoreGeofenceRepository storeGeofenceRepository;
    private final StorePriceService storePriceService;
    private final OverpassService overpassService;

    public ShoppingSessionService(ShoppingSessionRepository shoppingSessionRepository,
                                  StoreCandidateSubmissionRepository storeCandidateSubmissionRepository,
                                  ShoppingListRepository shoppingListRepository,
                                  UserRepository userRepository,
                                  StoreGeofenceRepository storeGeofenceRepository,
                                  StorePriceService storePriceService,
                                  OverpassService overpassService) {
        this.shoppingSessionRepository = shoppingSessionRepository;
        this.storeCandidateSubmissionRepository = storeCandidateSubmissionRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.userRepository = userRepository;
        this.storeGeofenceRepository = storeGeofenceRepository;
        this.storePriceService = storePriceService;
        this.overpassService = overpassService;
    }

    @Transactional
    public ShoppingSessionDTO startShopping(StartShoppingRequest request, String userEmail) {
        if (request == null || request.getListId() == null) {
            throw new IllegalArgumentException("List ID is required");
        }

        ShoppingList list = getAccessibleList(request.getListId(), userEmail);
        Users user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ListUserNotFoundException("User not found"));

        boolean hasOfficialStore = request.getStoreId() != null;
        boolean hasCustomStore = request.getCustomStoreName() != null && !request.getCustomStoreName().trim().isEmpty();
        if (hasOfficialStore == hasCustomStore) {
            throw new IllegalArgumentException("Choose either an official store or a custom store proposal");
        }

        cancelExistingActiveSessions(list.getId());

        ShoppingSession session = new ShoppingSession();
        session.setShoppingList(list);
        session.setStartedBy(user);
        session.setStatus(ShoppingSessionStatus.ACTIVE);
        session.setFinishedAt(null);

        if (hasOfficialStore) {
            StoreGeofence store = storeGeofenceRepository.findById(request.getStoreId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected store does not exist"));
            session.setStore(store);
        } else {
            StoreCandidateSubmission submission = new StoreCandidateSubmission();
            submission.setSubmittedName(request.getCustomStoreName().trim());
            submission.setSubmittedAddress(blankToNull(request.getCustomStoreAddress()));
            submission.setSubmissionNotes(blankToNull(request.getCustomStoreNotes()));
            submission.setSubmittedBy(user);
            submission.setSourceList(list);
            submission.setStatus(StoreSubmissionStatus.PENDING);

            // Create placeholder store if coordinates are provided
            if (request.getLatitude() != null && request.getLongitude() != null) {
                logger.info("Creating placeholder store for: {} at [{}, {}]",
                        submission.getSubmittedName(), request.getLatitude(), request.getLongitude());

                StoreGeofence placeholderStore = new StoreGeofence();
                placeholderStore.setId(UUID.randomUUID());
                placeholderStore.setName(submission.getSubmittedName());

                try {
                    Polygon buildingPolygon = overpassService.fetchBuildingPolygon(
                            request.getLatitude(), request.getLongitude(),
                            submission.getSubmittedName(), submission.getSubmittedAddress());
                    placeholderStore.setBoundaryPolygon(buildingPolygon);

                    placeholderStore = storeGeofenceRepository.save(placeholderStore);
                    logger.info("Placeholder store created with OSM polygon, ID: {}", placeholderStore.getId());

                    session.setStore(placeholderStore);
                    submission.setMatchedStore(placeholderStore);
                } catch (Exception e) {
                    logger.error("Failed to create spatial boundary for placeholder store", e);
                }
            } else {
                logger.warn("Custom store coordinates missing, skipping placeholder store creation.");
            }

            session.setStoreCandidateSubmission(storeCandidateSubmissionRepository.save(submission));
        }

        return mapToDto(shoppingSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public Optional<ShoppingSessionDTO> getActiveSession(UUID listId, String userEmail) {
        getAccessibleList(listId, userEmail);
        return shoppingSessionRepository
                .findFirstByShoppingList_IdAndStatusOrderByStartedAtDesc(listId, ShoppingSessionStatus.ACTIVE)
                .map(this::mapToDto);
    }

    @Transactional
    public ShoppingSessionDTO finishShopping(UUID listId, String userEmail) {
        ShoppingList list = getAccessibleList(listId, userEmail);
        ShoppingSession session = shoppingSessionRepository
                .findFirstByShoppingList_IdAndStatusOrderByStartedAtDesc(listId, ShoppingSessionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("No active shopping session found for this list"));

        session.setStatus(ShoppingSessionStatus.FINISHED);
        session.setFinishedAt(LocalDateTime.now());

        StoreGeofence officialStore = session.getStore();
        list.setFinalStore(officialStore);

        if (officialStore != null) {
            list.getItems().stream()
                    .filter(Item::isChecked)
                    .filter(item -> item.getCatalogItem() != null)
                    .filter(item -> item.getPrice() != null && item.getPrice().compareTo(java.math.BigDecimal.ZERO) >= 0)
                    .forEach(item -> recordPrice(item, officialStore.getId()));
        }

        shoppingListRepository.save(list);
        return mapToDto(shoppingSessionRepository.save(session));
    }

    private void recordPrice(Item item, UUID storeId) {
        ProductCatalog catalogItem = item.getCatalogItem();
        if (catalogItem == null) {
            return;
        }
        storePriceService.recordStorePrice(catalogItem, storeId, item.getPrice());
    }

    private void cancelExistingActiveSessions(UUID listId) {
        List<ShoppingSession> activeSessions = shoppingSessionRepository
                .findByShoppingList_IdAndStatus(listId, ShoppingSessionStatus.ACTIVE);
        for (ShoppingSession activeSession : activeSessions) {
            activeSession.setStatus(ShoppingSessionStatus.CANCELLED);
            activeSession.setFinishedAt(LocalDateTime.now());
        }
        if (!activeSessions.isEmpty()) {
            shoppingSessionRepository.saveAll(activeSessions);
        }
    }

    private ShoppingList getAccessibleList(UUID listId, String userEmail) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        boolean isOwner = list.getUser().getEmail().equals(userEmail);
        boolean isCollaborator = list.getCollaborators().stream()
                .anyMatch(c -> c.getUser().getEmail().equals(userEmail));

        if (!isOwner && !isCollaborator) {
            throw new ListAccessDeniedException("You do not have permission to view this list");
        }
        return list;
    }

    private ShoppingSessionDTO mapToDto(ShoppingSession session) {
        ShoppingSessionDTO dto = new ShoppingSessionDTO();
        dto.setSessionId(session.getId());
        dto.setListId(session.getShoppingList().getId());
        dto.setStatus(session.getStatus());
        dto.setStartedAt(session.getStartedAt());
        dto.setFinishedAt(session.getFinishedAt());
        if (session.getStore() != null) {
            dto.setOfficialStore(true);
            dto.setStoreId(session.getStore().getId());
            dto.setStoreName(session.getStore().getName());
        } else if (session.getStoreCandidateSubmission() != null) {
            dto.setOfficialStore(false);
            dto.setStoreCandidateSubmissionId(session.getStoreCandidateSubmission().getId());
            dto.setStoreName(session.getStoreCandidateSubmission().getSubmittedName());
            dto.setStoreAddress(session.getStoreCandidateSubmission().getSubmittedAddress());
        }
        return dto;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
