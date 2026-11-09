package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.service.PedigreeDiagram;
import com.questionnaire.questionnaire_platform.service.PedigreeDiagramService;
import com.questionnaire.questionnaire_platform.service.PedigreeExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

// Read-only derived view, available regardless of session status (IN_PROGRESS or COMPLETED) -
// same reasoning as /responses: nothing about reading a snapshot requires the session to be
// finished. See PedigreeExportService for the full mapping and its v1 scope limitations.
// /pedigree-diagram proxies the same export JSON to PedigreeDraw and hands back the rendered
// image bytes - see PedigreeDiagramService and
// docs/superpowers/specs/2026-11-02-pedigree-viewer-and-post-completion-editing-design.md.
@RestController
@RequestMapping("/api/v1/assessment/{sessionId}")
@RequiredArgsConstructor
public class PedigreeExportController {

    private final PedigreeExportService pedigreeExportService;
    private final PedigreeDiagramService pedigreeDiagramService;

    @GetMapping("/pedigree-export")
    public ResponseEntity<JsonNode> export(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(pedigreeExportService.export(sessionId));
    }

    @GetMapping("/pedigree-diagram")
    public ResponseEntity<byte[]> diagram(@PathVariable UUID sessionId,
                                           @RequestParam(defaultValue = "svg") String format) {
        PedigreeDiagram diagram = pedigreeDiagramService.render(sessionId, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, diagram.contentType())
                .body(diagram.bytes());
    }
}
