package com.p2ps.ai.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
public class RecipeRequest {
   //The user has the option to add the AI parsed list to an existing one or create a new one
   private UUID listId;
   @Size(max = 255, message = "List title is too long. Maximum permitted number of characters is 255.")
   private String newListTitle;

   @NotBlank(message = "Recipe text cannot be null.")
   @Size(max = 5000, message = "Text is too long. Maximum permitted number of characters is 5000.")
   private String text;
}
