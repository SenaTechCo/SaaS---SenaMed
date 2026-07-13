package com.senamed.backend.auth;

import com.senamed.backend.clinic.ClinicRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Slugifies clinic names into URL-friendly, unique identifiers (RF-002 / RN-011).
 */
@Service
public class SlugService {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");

    private final ClinicRepository clinicRepository;

    public SlugService(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    /**
     * @return a slug derived from {@code name}, guaranteed to be unique among existing clinics.
     * On collision, appends an incremental numeric suffix: {@code my-clinic}, {@code my-clinic-2},
     * {@code my-clinic-3}, ...
     */
    public String generateUniqueSlug(String name) {
        String base = slugify(name);
        if (base.isBlank()) {
            base = "clinic";
        }

        String candidate = base;
        int suffix = 2;
        while (clinicRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // strip diacritics/accents
        String lower = normalized.toLowerCase();
        String hyphenated = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");
        return EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
    }
}
