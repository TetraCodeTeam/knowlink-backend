package com.knowlink.api.users.controllers.interfaces;

import com.knowlink.api.users.controllers.requests.UpdateUserRequest;
import com.knowlink.api.users.controllers.responses.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Gestión de usuarios")
public interface IUserController {

    @GetMapping("/{userId}")
    @Operation(summary = "Obtener usuario por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId);

    @GetMapping
    @Operation(summary = "Listar todos los usuarios (admin)")
    ResponseEntity<Page<UserResponse>> getAllUsers(Pageable pageable);

    @PatchMapping("/{userId}")
    @Operation(summary = "Actualizar usuario")
    ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID userId,
            @RequestBody @Valid UpdateUserRequest request
    );

    @DeleteMapping("/{userId}")
    @ResponseStatus(NO_CONTENT)
    @Operation(summary = "Eliminar usuario")
    void deleteUser(@PathVariable UUID userId);
}
