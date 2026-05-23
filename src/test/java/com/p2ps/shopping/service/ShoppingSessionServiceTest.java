package com.p2ps.shopping.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.service.StorePriceService;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ListCollaborator;
import com.p2ps.lists.model.ListRole;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ShoppingListRepository;
import com.p2ps.shopping.dto.ShoppingSessionDTO;
import com.p2ps.shopping.dto.StartShoppingRequest;
import com.p2ps.shopping.model.ShoppingSession;
import com.p2ps.shopping.model.ShoppingSessionStatus;
import com.p2ps.shopping.model.StoreCandidateSubmission;
import com.p2ps.shopping.repository.ShoppingSessionRepository;
import com.p2ps.shopping.repository.StoreCandidateSubmissionRepository;
import com.p2ps.store.model.StoreGeofence;
import com.p2ps.store.repository.StoreGeofenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingSessionServiceTest {

    @Mock
    private ShoppingSessionRepository shoppingSessionRepository;
    @Mock
    private StoreCandidateSubmissionRepository storeCandidateSubmissionRepository;
    @Mock
    private ShoppingListRepository shoppingListRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreGeofenceRepository storeGeofenceRepository;
    @Mock
    private StorePriceService storePriceService;

    @InjectMocks
    private ShoppingSessionService shoppingSessionService;

    @Test
    void startShopping_withOfficialStore_createsActiveSession() {
        UUID listId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String email = "user@example.com";
        Users user = user(1, email);
        ShoppingList list = ownedList(listId, user);
        StoreGeofence store = new StoreGeofence();
        store.setId(storeId);
        store.setName("Mega");

        StartShoppingRequest request = new StartShoppingRequest();
        request.setListId(listId);
        request.setStoreId(storeId);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(shoppingSessionRepository.findByShoppingList_IdAndStatus(listId, ShoppingSessionStatus.ACTIVE))
                .thenReturn(List.of());
        when(storeGeofenceRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(shoppingSessionRepository.save(any(ShoppingSession.class))).thenAnswer(inv -> {
            ShoppingSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            s.setStartedAt(LocalDateTime.now());
            return s;
        });

        ShoppingSessionDTO dto = shoppingSessionService.startShopping(request, email);

        assertTrue(dto.isOfficialStore());
        assertEquals(storeId, dto.getStoreId());
        assertEquals("Mega", dto.getStoreName());
        assertEquals(ShoppingSessionStatus.ACTIVE, dto.getStatus());
    }

    @Test
    void startShopping_withCustomStoreAndCoordinates_createsPlaceholderStore() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";
        Users user = user(1, email);
        ShoppingList list = ownedList(listId, user);
        StartShoppingRequest request = new StartShoppingRequest();
        request.setListId(listId);
        request.setCustomStoreName("  Fresh Market  ");
        request.setCustomStoreAddress(" Str. Test ");
        request.setCustomStoreNotes(" Notes ");
        request.setLatitude(44.43);
        request.setLongitude(26.10);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(shoppingSessionRepository.findByShoppingList_IdAndStatus(listId, ShoppingSessionStatus.ACTIVE))
                .thenReturn(List.of());
        when(storeGeofenceRepository.save(any(StoreGeofence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storeCandidateSubmissionRepository.save(any(StoreCandidateSubmission.class))).thenAnswer(inv -> {
            StoreCandidateSubmission s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(shoppingSessionRepository.save(any(ShoppingSession.class))).thenAnswer(inv -> {
            ShoppingSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            s.setStartedAt(LocalDateTime.now());
            return s;
        });

        ShoppingSessionDTO dto = shoppingSessionService.startShopping(request, email);

        assertTrue(dto.isOfficialStore());
        assertEquals("Fresh Market", dto.getStoreName());
        verify(storeGeofenceRepository).save(any(StoreGeofence.class));
        verify(storeCandidateSubmissionRepository).save(any(StoreCandidateSubmission.class));
    }

    @Test
    void startShopping_withInvalidStoreSelection_throws() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";
        Users user = user(1, email);
        ShoppingList list = ownedList(listId, user);
        StartShoppingRequest request = new StartShoppingRequest();
        request.setListId(listId);
        request.setStoreId(UUID.randomUUID());
        request.setCustomStoreName("Custom");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> shoppingSessionService.startShopping(request, email));
    }

    @Test
    void startShopping_withMissingUser_throws() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";
        Users owner = user(1, email);
        ShoppingList list = ownedList(listId, owner);
        StartShoppingRequest request = new StartShoppingRequest();
        request.setListId(listId);
        request.setStoreId(UUID.randomUUID());

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(ListUserNotFoundException.class, () -> shoppingSessionService.startShopping(request, email));
    }

    @Test
    void finishShopping_recordsPricesOnlyForEligibleItems_andSetsFinalStore() {
        UUID listId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String email = "user@example.com";
        Users owner = user(1, email);
        ShoppingList list = ownedList(listId, owner);

        ProductCatalog catalog = new ProductCatalog();
        catalog.setId(UUID.randomUUID());

        Item good = new Item();
        good.setChecked(true);
        good.setCatalogItem(catalog);
        good.setPrice(new BigDecimal("10.50"));

        Item unchecked = new Item();
        unchecked.setChecked(false);
        unchecked.setCatalogItem(catalog);
        unchecked.setPrice(new BigDecimal("11.00"));

        Item nullCatalog = new Item();
        nullCatalog.setChecked(true);
        nullCatalog.setCatalogItem(null);
        nullCatalog.setPrice(new BigDecimal("9.00"));

        Item negative = new Item();
        negative.setChecked(true);
        negative.setCatalogItem(catalog);
        negative.setPrice(new BigDecimal("-1.00"));

        list.getItems().addAll(List.of(good, unchecked, nullCatalog, negative));

        StoreGeofence store = new StoreGeofence();
        store.setId(storeId);
        store.setName("Kaufland");

        ShoppingSession session = new ShoppingSession();
        session.setShoppingList(list);
        session.setStatus(ShoppingSessionStatus.ACTIVE);
        session.setStore(store);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(shoppingSessionRepository.findFirstByShoppingList_IdAndStatusOrderByStartedAtDesc(listId, ShoppingSessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(shoppingSessionRepository.save(any(ShoppingSession.class))).thenAnswer(inv -> {
            ShoppingSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            if (s.getStartedAt() == null) {
                s.setStartedAt(LocalDateTime.now().minusHours(1));
            }
            return s;
        });

        ShoppingSessionDTO dto = shoppingSessionService.finishShopping(listId, email);

        assertEquals(ShoppingSessionStatus.FINISHED, dto.getStatus());
        assertNotNull(dto.getFinishedAt());
        assertEquals(store, list.getFinalStore());
        verify(storePriceService, times(1)).recordStorePrice(catalog, storeId, new BigDecimal("10.50"));
    }

    @Test
    void getActiveSession_whenMissing_returnsEmpty() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";
        Users owner = user(1, email);
        ShoppingList list = ownedList(listId, owner);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(shoppingSessionRepository.findFirstByShoppingList_IdAndStatusOrderByStartedAtDesc(listId, ShoppingSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        Optional<ShoppingSessionDTO> result = shoppingSessionService.getActiveSession(listId, email);

        assertTrue(result.isEmpty());
    }

    @Test
    void getActiveSession_whenUserIsCollaborator_allowsAccess() {
        UUID listId = UUID.randomUUID();
        Users owner = user(1, "owner@example.com");
        Users collaborator = user(2, "collab@example.com");
        ShoppingList list = ownedList(listId, owner);
        list.getCollaborators().add(new ListCollaborator(list, collaborator, ListRole.EDITOR));

        ShoppingSession active = new ShoppingSession();
        active.setId(UUID.randomUUID());
        active.setShoppingList(list);
        active.setStatus(ShoppingSessionStatus.ACTIVE);
        active.setStartedAt(LocalDateTime.now());

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(shoppingSessionRepository.findFirstByShoppingList_IdAndStatusOrderByStartedAtDesc(listId, ShoppingSessionStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        Optional<ShoppingSessionDTO> result = shoppingSessionService.getActiveSession(listId, collaborator.getEmail());

        assertTrue(result.isPresent());
        assertEquals(active.getId(), result.get().getSessionId());
    }

    @Test
    void getActiveSession_whenListNotFound_throws() {
        UUID listId = UUID.randomUUID();
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.empty());
        assertThrows(ShoppingListNotFoundException.class,
                () -> shoppingSessionService.getActiveSession(listId, "user@example.com"));
    }

    @Test
    void getActiveSession_whenUnauthorized_throws() {
        UUID listId = UUID.randomUUID();
        Users owner = user(1, "owner@example.com");
        ShoppingList list = ownedList(listId, owner);
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));

        assertThrows(ListAccessDeniedException.class,
                () -> shoppingSessionService.getActiveSession(listId, "other@example.com"));
    }

    @Test
    void startShopping_cancelsPreviousActiveSessions() {
        UUID listId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String email = "user@example.com";
        Users user = user(1, email);
        ShoppingList list = ownedList(listId, user);
        StoreGeofence store = new StoreGeofence();
        store.setId(storeId);

        ShoppingSession old = new ShoppingSession();
        old.setStatus(ShoppingSessionStatus.ACTIVE);
        old.setShoppingList(list);

        StartShoppingRequest request = new StartShoppingRequest();
        request.setListId(listId);
        request.setStoreId(storeId);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(shoppingSessionRepository.findByShoppingList_IdAndStatus(listId, ShoppingSessionStatus.ACTIVE))
                .thenReturn(List.of(old));
        when(storeGeofenceRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(shoppingSessionRepository.save(any(ShoppingSession.class))).thenAnswer(inv -> inv.getArgument(0));

        shoppingSessionService.startShopping(request, email);

        ArgumentCaptor<List<ShoppingSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(shoppingSessionRepository).saveAll(captor.capture());
        assertEquals(ShoppingSessionStatus.CANCELLED, captor.getValue().getFirst().getStatus());
        assertNotNull(captor.getValue().getFirst().getFinishedAt());
    }

    @Test
    void startShopping_withNullRequestOrListId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> shoppingSessionService.startShopping(null, "user@example.com"));
        StartShoppingRequest request = new StartShoppingRequest();
        assertThrows(IllegalArgumentException.class,
                () -> shoppingSessionService.startShopping(request, "user@example.com"));
    }

    @Test
    void finishShopping_whenNoActiveSession_throws() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";
        Users owner = user(1, email);
        ShoppingList list = ownedList(listId, owner);
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(shoppingSessionRepository.findFirstByShoppingList_IdAndStatusOrderByStartedAtDesc(listId, ShoppingSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> shoppingSessionService.finishShopping(listId, email));
        verify(storePriceService, never()).recordStorePrice(any(), any(UUID.class), any());
    }

    private Users user(int id, String email) {
        Users user = new Users(email, "pass", "Test", "User");
        user.setId(id);
        return user;
    }

    private ShoppingList ownedList(UUID id, Users owner) {
        ShoppingList list = new ShoppingList();
        list.setId(id);
        list.setTitle("List");
        list.setUser(owner);
        return list;
    }
}
