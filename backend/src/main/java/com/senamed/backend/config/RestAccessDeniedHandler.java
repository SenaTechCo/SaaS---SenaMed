package com.senamed.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senamed.backend.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ensures requests rejected by a role-based rule (e.g. {@code hasRole("ADMIN")}) get a 403
 * response using the same {@link ApiError} JSON shape as the rest of the API, mirroring
 * {@link RestAuthenticationEntryPoint}'s handling of the 401 case.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = ApiError.of(HttpStatus.FORBIDDEN.value(), "Forbidden", "Access denied");
        objectMapper.writeValue(response.getWriter(), error);
    }
}
