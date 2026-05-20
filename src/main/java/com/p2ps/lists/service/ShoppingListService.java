package com.p2ps.lists.service;


import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.lists.dto.CollaboratorDTO;
import com.p2ps.lists.dto.ImportItemsRequestDTO;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ListInvitationDTO;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.InvitationStatus;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ListCategory;
import com.p2ps.lists.model.ListCollaborator;
import com.p2ps.lists.model.ListInvitation;
import com.p2ps.lists.model.ListRole;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ItemRepository;
import com.p2ps.lists.repo.ListCollaboratorRepository;
import com.p2ps.lists.repo.ListInvitationRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final UserRepository userRepository;
    private final ListInvitationRepository invitationRepository;
    private final ListCollaboratorRepository listCollaboratorRepository;
    private static final String SHOPPING_LIST_NOT_FOUND = "Shopping list not found";
    private static final String EMAIL_MASK_REGEX = "(^.)[^@]*(@.*$)";
    private static final String EMAIL_MASK_REPLACEMENT = "$1***$2";
    private final ItemRepository itemRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ShoppingListService(ShoppingListRepository shoppingListRepository, UserRepository userRepository, ItemRepository itemRepository, ListInvitationRepository invitationRepository, ListCollaboratorRepository listCollaboratorRepository, SimpMessagingTemplate messagingTemplate) {
        this.shoppingListRepository = shoppingListRepository;
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.invitationRepository = invitationRepository;
        this.listCollaboratorRepository = listCollaboratorRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public ShoppingListDTO createList(String title, String userEmail, ListCategory category, String subcategory) {
        //userul curent pe baza emailului din JWT
        Users currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ListUserNotFoundException("User not found"));

        ShoppingList newList = new ShoppingList();
        newList.setTitle(title);
        newList.setUser(currentUser);
        if (category != null) {
            newList.setCategory(category);
        }
        newList.setSubcategory(subcategory);
        ShoppingList savedList = shoppingListRepository.save(newList);

        return mapToDTO(savedList, userEmail);
    }

    @Transactional
    public ShoppingListDTO updateList(UUID listId, ShoppingListDTO updateDto, String userEmail) {
        ShoppingList list = getListEntityByIdAndUser(listId, userEmail);

        if (updateDto.getTitle() != null) {
            list.setTitle(updateDto.getTitle());
        }
        if (updateDto.getCategory() != null) {
            list.setCategory(updateDto.getCategory());
        }
        // Permite resetarea valorilor optionale (subcategory si finalStore) doar
        // daca se trimit in DTO. Intrucat `UpdateListRequest` e JSON, DTO-ul ar putea sa foloseasca
        // JsonNullable pt a distinge intre explicit null si camp lipsa, dar
        // momentan vom updata doar daca valoarea nu e null, as-is din cerinta sau
        // lasam asa cum e (se va accepta ca null in DTO nu face update la acele campuri)
        if (updateDto.getSubcategory() != null) {
            list.setSubcategory(updateDto.getSubcategory().isEmpty() ? null : updateDto.getSubcategory());
        }
        if (updateDto.getFinalStore() != null) {
            list.setFinalStore(updateDto.getFinalStore().isEmpty() ? null : updateDto.getFinalStore());
        }

        ShoppingList savedList = shoppingListRepository.save(list);
        return mapToDTO(savedList, userEmail);
    }

    @Transactional(readOnly = true)
    public List<ShoppingListDTO> getUserLists(String userEmail) {
        return shoppingListRepository.findAccessibleByEmail(userEmail)
                .stream()
                .map(l -> mapToDTO(l, userEmail))
                .toList();
    }

    @Transactional
    public void deleteList(java.util.UUID listId, String userEmail) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (!list.getUser().getEmail().equals(userEmail)) {
            throw new ListAccessDeniedException("Only the owner can delete this list");
        }

        shoppingListRepository.delete(list);
    }

    @Transactional(readOnly = true)
    public ShoppingListDTO getListById(java.util.UUID listId, String userEmail) {
        ShoppingList list = getListEntityByIdAndUser(listId, userEmail);
        return mapToDTO(list, userEmail);
    }

    @Transactional
    public ShoppingListDTO importItems(UUID currentListId, ImportItemsRequestDTO request, String userEmail) {
        if (request.getSourceListId() == null) {
            throw new IllegalArgumentException("Source list ID cannot be null");
        }

        if (currentListId.equals(request.getSourceListId())) {
            throw new IllegalArgumentException("Cannot import items from the same list into itself");
        }

        ShoppingList currentList = getListEntityByIdAndUser(currentListId, userEmail);
        ShoppingList sourceList = getListEntityByIdAndUser(request.getSourceListId(), userEmail);

        List<Item> itemsToImport = sourceList.getItems();

        if (request.getItemIds() != null && !request.getItemIds().isEmpty()) {
            itemsToImport = itemsToImport.stream()
                .filter(item -> request.getItemIds().contains(item.getId()))
                .toList();
        }

        for (Item item : itemsToImport) {
            Item newItem = new Item();
            newItem.setName(item.getName());
            newItem.setBrand(item.getBrand());
            newItem.setQuantity(item.getQuantity());
            newItem.setPrice(item.getPrice());
            newItem.setCategory(item.getCategory());
            newItem.setRecurrent(item.isRecurrent());
            newItem.setCatalogItem(item.getCatalogItem());
            newItem.setExternalItemId(item.getExternalItemId());
            newItem.setShoppingList(currentList);
            newItem.setLastUpdatedTimestamp(System.currentTimeMillis());

            itemRepository.save(newItem);
            currentList.getItems().add(newItem);
        }

        return mapToDTO(shoppingListRepository.save(currentList), userEmail);
    }

    @Transactional
    public ShoppingListDTO finishShopping(UUID listId, String storeName, String userEmail) {
        if (storeName == null || storeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Store name cannot be empty");
        }

        ShoppingList list = getListEntityByIdAndUser(listId, userEmail);
        list.setFinalStore(storeName.trim());

        return mapToDTO(shoppingListRepository.save(list), userEmail);
    }

    @Transactional
    public void markReceiptItemPurchased(
            UUID listId,
            ParsedItemResponse receiptItem,
            ProductCatalog catalogProduct,
            String userEmail) {
        if (receiptItem == null) {
            return;
        }

        ShoppingList list = getListEntityByIdAndUser(listId, userEmail);
        Item matchedItem = list.getItems().stream()
                .filter(item -> !item.isChecked())
                .filter(item -> matchesReceiptItem(item, receiptItem, catalogProduct))
                .findFirst()
                .orElseGet(() -> list.getItems().stream()
                        .filter(item -> matchesReceiptItem(item, receiptItem, catalogProduct))
                        .findFirst()
                        .orElse(null));

        if (matchedItem == null) {
            return;
        }

        matchedItem.setChecked(true);
        matchedItem.setLastUpdatedTimestamp(System.currentTimeMillis());

        if (catalogProduct != null) {
            matchedItem.setCatalogItem(catalogProduct);
        }
        if (receiptItem.getPrice() != null && receiptItem.getPrice().compareTo(java.math.BigDecimal.ZERO) >= 0) {
            matchedItem.setPrice(receiptItem.getPrice());
        }
        if ((matchedItem.getBrand() == null || matchedItem.getBrand().isBlank()) && receiptItem.getBrand() != null) {
            matchedItem.setBrand(receiptItem.getBrand().trim());
        }
        if ((matchedItem.getCategory() == null || matchedItem.getCategory().isBlank()) && receiptItem.getCategory() != null) {
            matchedItem.setCategory(receiptItem.getCategory().trim());
        }

        itemRepository.save(matchedItem);
    }

    private boolean matchesReceiptItem(Item item, ParsedItemResponse receiptItem, ProductCatalog catalogProduct) {
        String itemName = normalize(item.getName());
        String itemBrand = normalize(item.getBrand());
        String receiptSpecific = normalize(firstNonBlank(
                receiptItem.getSpecificName(),
                catalogProduct != null ? catalogProduct.getSpecificName() : null
        ));
        String receiptGeneric = normalize(firstNonBlank(
                receiptItem.getGenericName(),
                catalogProduct != null ? catalogProduct.getGenericName() : null
        ));
        String receiptBrand = normalize(firstNonBlank(
                receiptItem.getBrand(),
                catalogProduct != null ? catalogProduct.getBrand() : null
        ));

        boolean brandMatches = receiptBrand.isBlank() || itemBrand.isBlank() || itemBrand.equals(receiptBrand);
        if (!brandMatches) {
            return false;
        }

        return containsEither(itemName, receiptSpecific)
                || containsEither(itemName, receiptGeneric)
                || containsEither(receiptSpecific, itemName)
                || containsEither(receiptGeneric, itemName);
    }

    private boolean containsEither(String left, String right) {
        return !left.isBlank() && !right.isBlank() && (left.contains(right) || right.contains(left));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private ShoppingList getListEntityByIdAndUser(UUID listId, String userEmail) {
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

    @Transactional
    public void shareList(java.util.UUID listId, String collaboratorEmail, String ownerEmail) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (!list.getUser().getEmail().equals(ownerEmail)) {
            throw new ListAccessDeniedException("Only the owner can share this list");
        }

        if (collaboratorEmail.equals(ownerEmail)) {
            throw new IllegalArgumentException("Cannot share list with owner");
        }

        Users collaborator = userRepository.findByEmail(collaboratorEmail)
                .orElseThrow(() -> new ListUserNotFoundException("Collaborator user not found"));

        if (list.getCollaborators().stream().anyMatch(c -> c.getUser().getId().equals(collaborator.getId()))) {
            throw new IllegalArgumentException("User is already a collaborator on this list");
        }

        Optional<ListInvitation> existingInvitation = invitationRepository.findByShoppingListIdAndInviteeId(
                listId, collaborator.getId());

        if (existingInvitation.isPresent()) {
            ListInvitation existing = existingInvitation.get();
            if (existing.getStatus() == InvitationStatus.PENDING) {
                throw new IllegalArgumentException("Invitation already pending for this user");
            }
            if (existing.getStatus() == InvitationStatus.ACCEPTED) {
                throw new IllegalArgumentException("User has already accepted an invitation for this list");
            }
            existing.setInviter(list.getUser());
            existing.setStatus(InvitationStatus.PENDING);
            invitationRepository.save(existing);
            notifyInvitee(existing);
            return;
        }

        ListInvitation invitation = new ListInvitation();
        invitation.setShoppingList(list);
        invitation.setInviter(list.getUser());
        invitation.setInvitee(collaborator);
        invitation.setStatus(InvitationStatus.PENDING);
        invitationRepository.save(invitation);
        notifyInvitee(invitation);
    }

    @Transactional
    public void removeCollaborator(UUID listId, Integer userId, String ownerEmail) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (!list.getUser().getEmail().equals(ownerEmail)) {
            throw new ListAccessDeniedException("Only the owner can remove collaborators");
        }

        if (!listCollaboratorRepository.existsByListIdAndUserId(listId, userId)) {
            throw new ListUserNotFoundException("Collaborator not found on this list");
        }

        listCollaboratorRepository.deleteByListIdAndUserId(listId, userId);
        invitationRepository.findByShoppingListIdAndInviteeId(listId, userId)
                .ifPresent(invitationRepository::delete);
        broadcastMembershipChange(listId);
    }

    @Transactional
    public void leaveList(UUID listId, String userEmail) {
        Users user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ListUserNotFoundException("User not found"));

        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (list.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("Owner cannot leave their own list");
        }

        if (!listCollaboratorRepository.existsByListIdAndUserId(listId, user.getId())) {
            throw new ListUserNotFoundException("You are not a member of this list");
        }

        listCollaboratorRepository.deleteByListIdAndUserId(listId, user.getId());
        invitationRepository.findByShoppingListIdAndInviteeId(listId, user.getId())
                .ifPresent(invitationRepository::delete);
        broadcastMembershipChange(listId);
    }

    private void broadcastMembershipChange(UUID listId) {
        messagingTemplate.convertAndSend("/topic/lists/" + listId + "/members", "changed");
    }

    private void notifyInvitee(ListInvitation invitation) {
        String inviteeEmail = invitation.getInvitee().getEmail();
        UUID id = invitation.getId();
        UUID listId = invitation.getShoppingList().getId();
        String listTitle = invitation.getShoppingList().getTitle();
        String inviterName = (invitation.getInviter().getFirstName() + " " + invitation.getInviter().getLastName()).trim();
        String maskedEmail = invitation.getInviter().getEmail().replaceAll(EMAIL_MASK_REGEX, EMAIL_MASK_REPLACEMENT);
        InvitationStatus status = invitation.getStatus();
        LocalDateTime createdAt = invitation.getCreatedAt();

        Runnable sendNotification = () -> {
            try {
                ListInvitationDTO dto = new ListInvitationDTO();
                dto.setId(id);
                dto.setListId(listId);
                dto.setListTitle(listTitle);
                dto.setInviterName(inviterName);
                dto.setInviterEmail(maskedEmail);
                dto.setStatus(status);
                dto.setCreatedAt(createdAt);
                messagingTemplate.convertAndSend("/topic/invitations/" + inviteeEmail, dto);
            } catch (Exception _) {
                // Notification failure should not break the share flow
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendNotification.run();
                }
            });
        } else {
            sendNotification.run();
        }
    }

    private ShoppingListDTO mapToDTO(ShoppingList list, String currentUserEmail) {
        ShoppingListDTO dto = new ShoppingListDTO();
        dto.setId(list.getId());
        dto.setTitle(list.getTitle());
        dto.setCategory(list.getCategory());
        dto.setSubcategory(list.getSubcategory());
        dto.setFinalStore(list.getFinalStore());
        if (list.getUser() != null) {
            dto.setUserId(list.getUser().getId() != null ? list.getUser().getId().toString() : null);
            dto.setOwnerEmail(list.getUser().getEmail());
            String fullName = list.getUser().getFirstName() + " " + list.getUser().getLastName();
            dto.setOwnerName(fullName.trim());
            dto.setOwnerId(list.getUser().getId());
        }

        if (list.getItems() != null) {
            dto.setItems(list.getItems().stream()
                    .map(item -> {
                        ItemDTO itemDto = new ItemDTO();
                        itemDto.setId(item.getId());
                        itemDto.setName(item.getName());
                        itemDto.setChecked(item.isChecked());
                        itemDto.setBrand(item.getBrand());
                        itemDto.setPrice(item.getPrice());
                        itemDto.setQuantity(item.getQuantity());
                        itemDto.setCategory(item.getCategory());
                        itemDto.setRecurrent(item.isRecurrent());
                        itemDto.setLastUpdatedTimestamp(item.getLastUpdatedTimestamp());
                        itemDto.setCreatedAt(item.getCreatedAt());
                        // expose catalogId and externalItemId so frontend can send catalog-based matching
                        if (item.getCatalogItem() != null) {
                            itemDto.setCatalogId(item.getCatalogItem().getId());
                        }
                        itemDto.setExternalItemId(item.getExternalItemId());
                        return itemDto;
                    })
                    .toList());
        } else {
            dto.setItems(new ArrayList<>());
        }

        List<CollaboratorDTO> collaboratorDTOs = new ArrayList<>();

        if (list.getUser() != null) {
            CollaboratorDTO ownerDto = new CollaboratorDTO();
            ownerDto.setUserId(list.getUser().getId());
            ownerDto.setName((list.getUser().getFirstName() + " " + list.getUser().getLastName()).trim());
            String ownerEmail = list.getUser().getEmail();
            ownerDto.setEmail(ownerEmail != null ? ownerEmail.replaceAll(EMAIL_MASK_REGEX, EMAIL_MASK_REPLACEMENT) : null);
            ownerDto.setRole(ListRole.ADMIN.name());
            collaboratorDTOs.add(ownerDto);
        }

        list.getCollaborators().stream()
                .map(lc -> {
                    CollaboratorDTO cdto = new CollaboratorDTO();
                    cdto.setUserId(lc.getUser().getId());
                    cdto.setName((lc.getUser().getFirstName() + " " + lc.getUser().getLastName()).trim());
                    String email = lc.getUser().getEmail();
                    cdto.setEmail(email != null ? email.replaceAll(EMAIL_MASK_REGEX, EMAIL_MASK_REPLACEMENT) : null);
                    cdto.setRole(lc.getRole().name());
                    return cdto;
                })
                .forEach(collaboratorDTOs::add);

        dto.setCollaborators(collaboratorDTOs);

        if (currentUserEmail != null && list.getUser() != null) {
            if (list.getUser().getEmail().equals(currentUserEmail)) {
                dto.setCurrentUserRole(ListRole.ADMIN.name());
            } else {
                list.getCollaboratorByUserEmail(currentUserEmail)
                        .ifPresent(lc -> dto.setCurrentUserRole(lc.getRole().name()));
            }
        }

        return dto;
    }
}
