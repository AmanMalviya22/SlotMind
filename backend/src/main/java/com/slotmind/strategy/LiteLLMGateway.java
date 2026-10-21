package com.slotmind.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.entity.Appointment;
import com.slotmind.entity.Slot;
import com.slotmind.enums.TriggerReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.Optional;

@Component
public class LiteLLMGateway implements LLMGateway {
    private static final Logger log = LoggerFactory.getLogger(LiteLLMGateway.class);
    private static final String SYSTEM_PROMPT = "You are an appointment rescheduling advisor. Recommend only an eligible candidate slot and return only the requested JSON object. Do not invent IDs.";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String product;
    private final String cookie;

    public LiteLLMGateway(RestClient.Builder builder, ObjectMapper objectMapper,
                          org.springframework.core.env.Environment environment) {
        String baseUrl = value(environment, "SLOTMIND_LLM_BASE_URL", "https://litellm-qc.zycus.net/v1");
        this.apiKey = value(environment, "SLOTMIND_LLM_API_KEY", "");
        this.model = value(environment, "SLOTMIND_LLM_MODEL", "qwen-cursor");
        this.product = value(environment, "SLOTMIND_LLM_PRODUCT", "PC1");
        this.cookie = value(environment, "SLOTMIND_LLM_COOKIE", "");
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(8000);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public Optional<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> candidates) {
        if (apiKey.isBlank() || candidates.isEmpty()) return Optional.empty();
        try {
            ChatRequest request = new ChatRequest(model, List.of(
                    new Message("system", SYSTEM_PROMPT),
                    new Message("user", prompt(appointment, reason, candidates))));
            RestClient.RequestHeadersSpec<?> call = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("product", product)
                    .headers(headers -> { if (!cookie.isBlank()) headers.set("Cookie", cookie); })
                    .body(request);
            JsonNode response = call.retrieve().body(JsonNode.class);
            return parse(response);
        } catch (Exception e) {
            log.warn("LLM scheduling request failed; using rule-based fallback: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static String prompt(Appointment appointment, TriggerReason reason, List<Slot> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Trigger: ").append(reason).append(". Appointment type: ").append(appointment.getType())
                .append(". Original provider specialty: ").append(appointment.getProvider().getSpecialty())
                .append(". Original provider ID: ").append(appointment.getProvider().getId()).append(".\n");
        if (reason == TriggerReason.PROVIDER_OFFLINE) {
            prompt.append("The original provider is offline. Do not recommend that provider.\n");
        } else if (reason == TriggerReason.SLOT_CANCELLED) {
            prompt.append("The original slot was cancelled. Prefer the same eligible provider when possible; otherwise choose an eligible alternative.\n");
        }
        prompt.append("Eligible candidates (choose one exact slot ID):\n");
        candidates.forEach(slot -> prompt.append("- slotId=").append(slot.getId())
                .append(", providerId=").append(slot.getProvider().getId())
                .append(", providerSpecialty=").append(slot.getProvider().getSpecialty())
                .append(", startTime=").append(slot.getStartTime())
                .append(", durationMinutes=").append(slot.getDurationMinutes()).append('\n'));
        prompt.append("Return exactly this JSON shape: {\"slotId\":\"actual candidate ID\",\"alternateProviderId\":\"actual provider ID or null\",\"confidence\":0.0,\"reasoning\":\"short patient-friendly explanation\"}");
        return prompt.toString();
    }

    private Optional<Recommendation> parse(JsonNode response) {
        try {
            if (response == null) return Optional.empty();
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) return Optional.empty();
            String json = content.asText().trim();
            if (json.startsWith("```")) json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
            JsonNode result = objectMapper.readTree(json);
            if (!result.isObject() || !result.hasNonNull("slotId") || !result.has("confidence") || !result.hasNonNull("reasoning")) return Optional.empty();
            String alternate = result.path("alternateProviderId").isNull() || result.path("alternateProviderId").isMissingNode() ? null : result.path("alternateProviderId").asText(null);
            return Optional.of(new Recommendation(result.path("slotId").asText(), alternate, result.path("confidence").asDouble(Double.NaN), result.path("reasoning").asText()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String value(org.springframework.core.env.Environment environment, String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private record Message(String role, String content) {}
    private record ChatRequest(String model, List<Message> messages) {}
}