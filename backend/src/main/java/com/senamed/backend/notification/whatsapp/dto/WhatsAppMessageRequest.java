package com.senamed.backend.notification.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Mirrors the JSON body expected by the Meta Cloud API's {@code POST /{phone-number-id}/messages}. */
public record WhatsAppMessageRequest(
        @JsonProperty("messaging_product") String messagingProduct,
        String to,
        String type,
        Template template) {

    public record Template(String name, Language language, List<Component> components) {

        public record Language(String code) {
        }

        public record Component(String type, List<Parameter> parameters) {

            public record Parameter(String type, String text) {

                public static Parameter text(String text) {
                    return new Parameter("text", text);
                }
            }
        }
    }
}
