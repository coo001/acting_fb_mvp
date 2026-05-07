package com.loadingmid.feedback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResult(
        String videoId,
        String createdAt,
        String label,
        double durationSec,
        String videoFilename,
        List<Observation> observations,
        List<TranscriptItem> transcript
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Observation(double startSec, double endSec, String category, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TranscriptItem(double start, double end, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiRaw(double durationSec,
                            List<Observation> observations,
                            List<TranscriptItem> transcript) {}

    public record IndexEntry(String id, String label, String createdAt, double durationSec) {}
}
