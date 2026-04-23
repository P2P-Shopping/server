package com.p2ps.lists.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.exception.GlobalExceptionHandler;
import com.p2ps.lists.dto.CreateListRequest;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.service.ShoppingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShoppingListControllerTest {

    @Mock
    private ShoppingListService shoppingListService;

    @InjectMocks
    private ShoppingListController shoppingListController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(shoppingListController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createListShouldReturnCreatedList() throws Exception {
        ShoppingListDTO response = new ShoppingListDTO();
        UUID listId = UUID.randomUUID();
        response.setId(listId);
        response.setTitle("Weekly groceries");

        CreateListRequest request = new CreateListRequest();
        request.setTitle("Weekly groceries");

        when(shoppingListService.createList("Weekly groceries", "ana@example.com")).thenReturn(response);

        mockMvc.perform(post("/api/lists")
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(listId.toString()))
                .andExpect(jsonPath("$.title").value("Weekly groceries"));

        verify(shoppingListService).createList("Weekly groceries", "ana@example.com");
    }

    @Test
    void createListShouldReturnBadRequestWhenTitleIsBlank() throws Exception {
        CreateListRequest request = new CreateListRequest();
        request.setTitle(" ");

        mockMvc.perform(post("/api/lists")
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"));

        verify(shoppingListService, never()).createList(eq(" "), eq("ana@example.com"));
    }

    @Test
    void createListShouldReturnUnauthorizedWhenUserMappingIsMissing() throws Exception {
        CreateListRequest request = new CreateListRequest();
        request.setTitle("Weekly groceries");

        when(shoppingListService.createList("Weekly groceries", "ana@example.com"))
                .thenThrow(new ListUserNotFoundException("User not found"));

        mockMvc.perform(post("/api/lists")
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.details").value("User not found"));
    }

    @Test
    void getMyListsShouldReturnUserLists() throws Exception {
        ShoppingListDTO first = new ShoppingListDTO();
        first.setId(UUID.randomUUID());
        first.setTitle("Groceries");

        ShoppingListDTO second = new ShoppingListDTO();
        second.setId(UUID.randomUUID());
        second.setTitle("Hardware");

        when(shoppingListService.getUserLists("ana@example.com")).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/lists")
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Groceries"))
                .andExpect(jsonPath("$[1].title").value("Hardware"));

        verify(shoppingListService).getUserLists("ana@example.com");
    }

    @Test
    void deleteListShouldReturnNoContent() throws Exception {
        UUID listId = UUID.randomUUID();

        mockMvc.perform(delete("/api/lists/{listId}", listId)
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null)))
                .andExpect(status().isNoContent());

        verify(shoppingListService).deleteList(listId, "ana@example.com");
    }

    @Test
    void deleteListShouldReturnNotFoundWhenListDoesNotExist() throws Exception {
        UUID listId = UUID.randomUUID();
        doThrow(new ShoppingListNotFoundException("Shopping list not found"))
                .when(shoppingListService)
                .deleteList(listId, "ana@example.com");

        mockMvc.perform(delete("/api/lists/{listId}", listId)
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource Not Found"))
                .andExpect(jsonPath("$.details").value("Shopping list not found"));
    }

    @Test
    void deleteListShouldReturnForbiddenWhenUserDoesNotOwnList() throws Exception {
        UUID listId = UUID.randomUUID();
        doThrow(new ListAccessDeniedException("You do not have permission to delete this list"))
                .when(shoppingListService)
                .deleteList(listId, "ana@example.com");

        mockMvc.perform(delete("/api/lists/{listId}", listId)
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden"))
                .andExpect(jsonPath("$.details").value("You do not have permission to delete this list"));
    }
}
