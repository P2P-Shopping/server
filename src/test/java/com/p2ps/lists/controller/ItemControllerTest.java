package com.p2ps.lists.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.exception.GlobalExceptionHandler;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.service.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ItemControllerTest {

    @Mock
    private ItemService itemService;

    @InjectMocks
    private ItemController itemController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(itemController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addItemShouldReturnCreatedItem() throws Exception {
        UUID listId = UUID.randomUUID();
        ItemRequest request = buildRequest();

        ItemDTO response = new ItemDTO();
        UUID itemId = UUID.randomUUID();
        response.setId(itemId);
        response.setName("Milk");
        response.setPrice(new BigDecimal("12.50"));
        response.setChecked(false);

        when(itemService.addItemToList(listId, request, "ana@example.com")).thenReturn(response);

        mockMvc.perform(post("/api/lists/{listId}/items", listId)
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(itemId.toString()))
                .andExpect(jsonPath("$.name").value("Milk"))
                .andExpect(jsonPath("$.price").value(12.50));

        verify(itemService).addItemToList(listId, request, "ana@example.com");
    }

    @Test
    void addItemShouldReturnBadRequestWhenPriceIsNegative() throws Exception {
        UUID listId = UUID.randomUUID();
        ItemRequest request = buildRequest();
        request.setPrice(new BigDecimal("-2.00"));

        mockMvc.perform(post("/api/lists/{listId}/items", listId)
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath("$.details").value("Price must be zero or positive"));
    }

    @Test
    void addItemShouldReturnNotFoundWhenListDoesNotExist() throws Exception {
        UUID listId = UUID.randomUUID();
        ItemRequest request = buildRequest();

        when(itemService.addItemToList(listId, request, "ana@example.com"))
                .thenThrow(new ShoppingListNotFoundException("Shopping list not found"));

        mockMvc.perform(post("/api/lists/{listId}/items", listId)
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource Not Found"))
                .andExpect(jsonPath("$.details").value("Shopping list not found"));
    }

    @Test
    void updateItemShouldReturnUpdatedItem() throws Exception {
        UUID itemId = UUID.randomUUID();
        ItemRequest request = buildRequest();
        request.setName("Oat Milk");

        ItemDTO response = new ItemDTO();
        response.setId(itemId);
        response.setName("Oat Milk");
        response.setChecked(true);

        when(itemService.updateItem(itemId, request, "ana@example.com")).thenReturn(response);

        mockMvc.perform(put("/api/items/{itemId}", itemId)
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString()))
                .andExpect(jsonPath("$.name").value("Oat Milk"))
                .andExpect(jsonPath("$.isChecked").value(true));

        verify(itemService).updateItem(itemId, request, "ana@example.com");
    }

    @Test
    void deleteItemShouldReturnNoContent() throws Exception {
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(delete("/api/items/{itemId}", itemId)
                        .principal(new UsernamePasswordAuthenticationToken("ana@example.com", null)))
                .andExpect(status().isNoContent());

        verify(itemService).deleteItem(itemId, "ana@example.com");
    }

    @Test
    void testItemSerializationRoundTrip() throws Exception {
        ItemDTO dto = new ItemDTO();
        dto.setName("Test Item");
        dto.setChecked(true);

        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("\"isChecked\":true"), "JSON should contain isChecked:true");

        ItemDTO deserialized = objectMapper.readValue(json, ItemDTO.class);
        assertTrue(deserialized.isChecked(), "Deserialized object should have isChecked=true");
    }


    private ItemRequest buildRequest() {
        ItemRequest request = new ItemRequest();
        request.setName("Milk");
        request.setBrand("Brand A");
        request.setQuantity("2");
        request.setPrice(new BigDecimal("12.50"));
        request.setCategory("Dairy");
        request.setIsRecurrent(true);
        request.setIsChecked(false);
        return request;
    }
}
