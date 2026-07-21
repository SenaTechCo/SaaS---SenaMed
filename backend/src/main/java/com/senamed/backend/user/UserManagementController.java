package com.senamed.backend.user;

import com.senamed.backend.user.dto.UserCreateRequest;
import com.senamed.backend.user.dto.UserManagementResponse;
import com.senamed.backend.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only management of the clinic's users (list/create/edit/delete) - see
 * {@link UserManagementService}. Separate from {@link UserSelfController}, which is
 * self-service-only ({@code /api/users/me}).
 */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public List<UserManagementResponse> listAll() {
        return userManagementService.listAll();
    }

    @PostMapping
    public ResponseEntity<UserManagementResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.create(request));
    }

    @PutMapping("/{id}")
    public UserManagementResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userManagementService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userManagementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
