package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.exception.InvalidPedigreeDocumentException;
import com.questionnaire.questionnaire_platform.exception.InvalidPedigreeFormatException;
import com.questionnaire.questionnaire_platform.exception.PedigreeRenderFailedException;
import com.questionnaire.questionnaire_platform.exception.PedigreeServiceUnavailableException;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// Proxies pedigree rendering to PedigreeDraw, a separate local HTTP service (see
// docs/superpowers/specs/2026-11-02-pedigree-viewer-and-post-completion-editing-design.md) - the
// browser never talks to it directly, only this backend does, so there's one place to translate
// its error shapes and no CORS configuration needed on PedigreeDraw's side.
@Service
public class PedigreeDiagramService {

    private static final Logger log = LoggerFactory.getLogger(PedigreeDiagramService.class);
    private static final Set<String> VALID_FORMATS = Set.of("svg", "png", "pdf");

    private final PedigreeExportService pedigreeExportService;
    private final JsonColumnMapper jsonColumnMapper;
    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    public PedigreeDiagramService(
            PedigreeExportService pedigreeExportService,
            JsonColumnMapper jsonColumnMapper,
            @Value("${pedigree.render.service.base-url}") String baseUrl,
            @Value("${pedigree.render.service.timeout-ms}") long timeoutMs) {
        this.pedigreeExportService = pedigreeExportService;
        this.jsonColumnMapper = jsonColumnMapper;
        this.baseUrl = baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public PedigreeDiagram render(UUID sessionId, String format) {
        if (!VALID_FORMATS.contains(format.toLowerCase(Locale.ROOT))) {
            throw new InvalidPedigreeFormatException(
                    "'" + format + "' is not a supported pedigree format - must be one of " + VALID_FORMATS);
        }

        JsonNode document = pedigreeExportService.export(sessionId);
        String body = jsonColumnMapper.write(document);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/render?format=" + format))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Pedigree rendering service call failed", e);
            throw new PedigreeServiceUnavailableException("Pedigree rendering service is not reachable");
        }

        int status = response.statusCode();
        if (status == 200) {
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return new PedigreeDiagram(response.body(), contentType);
        }
        if (status == 422) {
            // The raw body (JSON Pointer paths into the document, which can echo answered values)
            // is logged for debugging but never put in the exception message - that message
            // reaches the end user as-is, and a clinical patient record has no business showing up
            // in an error toast.
            log.warn("Pedigree rendering service rejected the document: {}", new String(response.body(), StandardCharsets.UTF_8));
            throw new InvalidPedigreeDocumentException("The pedigree data could not be rendered - it may be incomplete.");
        }
        throw new PedigreeRenderFailedException("Pedigree rendering service returned HTTP " + status);
    }
}
