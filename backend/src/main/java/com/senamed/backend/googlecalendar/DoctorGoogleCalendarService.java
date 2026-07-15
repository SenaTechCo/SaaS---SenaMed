package com.senamed.backend.googlecalendar;

import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.doctor.Doctor;
import com.senamed.backend.doctor.DoctorRepository;
import com.senamed.backend.googlecalendar.dto.ConnectUrlResponse;
import com.senamed.backend.googlecalendar.dto.GoogleCalendarStatusResponse;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets a doctor connect/disconnect their own Google Calendar (KAN-78) - the first write action a
 * DOCTOR-role account can take, under the same {@code /api/doctors/me/**} authorization boundary
 * as their existing read-only self-service views.
 */
@Service
public class DoctorGoogleCalendarService {

    private final DoctorRepository doctorRepository;
    private final DoctorGoogleCalendarCredentialRepository credentialRepository;
    private final GoogleCalendarClient googleCalendarClient;
    private final GoogleOAuthStateService stateService;

    public DoctorGoogleCalendarService(
            DoctorRepository doctorRepository,
            DoctorGoogleCalendarCredentialRepository credentialRepository,
            GoogleCalendarClient googleCalendarClient,
            GoogleOAuthStateService stateService) {
        this.doctorRepository = doctorRepository;
        this.credentialRepository = credentialRepository;
        this.googleCalendarClient = googleCalendarClient;
        this.stateService = stateService;
    }

    @Transactional(readOnly = true)
    public GoogleCalendarStatusResponse getStatus() {
        return credentialRepository.findByDoctorId(TenantContext.currentDoctorId())
                .map(credential -> new GoogleCalendarStatusResponse(true, credential.getGoogleEmail()))
                .orElseGet(GoogleCalendarStatusResponse::disconnected);
    }

    @Transactional(readOnly = true)
    public ConnectUrlResponse getConnectUrl() {
        String state = stateService.sign(TenantContext.currentDoctorId());
        return new ConnectUrlResponse(googleCalendarClient.buildAuthorizationUrl(state));
    }

    @Transactional
    public void disconnect() {
        Long doctorId = TenantContext.currentDoctorId();
        if (!credentialRepository.existsByDoctorId(doctorId)) {
            throw new ResourceNotFoundException("Nenhuma conexão com o Google Calendar encontrada");
        }
        credentialRepository.deleteByDoctorId(doctorId);
    }

    /**
     * Handles the OAuth callback - a public endpoint (no {@code TenantContext} available, since
     * this is a browser redirect coming directly from Google). {@code state} carries the doctor id
     * instead, and its own HMAC signature (see {@link GoogleOAuthStateService}) is what makes a
     * plain {@code findById} safe here without clinic-scoping: the id was never client-supplied,
     * it only ever comes from a token this same service minted for an already-authenticated doctor.
     *
     * @return true if the connection was established successfully.
     */
    @Transactional
    public boolean handleCallback(String code, String state) {
        return stateService.verify(state)
                .map(doctorId -> {
                    Doctor doctor = doctorRepository.findById(doctorId).orElse(null);
                    if (doctor == null) {
                        return false;
                    }
                    GoogleTokenExchangeResult result = googleCalendarClient.exchangeAuthorizationCode(code);
                    credentialRepository.findByDoctorId(doctorId).ifPresentOrElse(
                            existing -> existing.reconnect(result.googleEmail(), result.refreshToken()),
                            () -> credentialRepository.save(new DoctorGoogleCalendarCredential(
                                    doctor, result.googleEmail(), result.refreshToken())));
                    return true;
                })
                .orElse(false);
    }
}
