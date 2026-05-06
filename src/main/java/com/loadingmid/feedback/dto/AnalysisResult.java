package com.loadingmid.feedback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResult(
        String videoId,
        String createdAt,
        String label,
        double durationSec,
        List<EmotionPoint> emotionIntensity,
        List<SpeechRatePoint> speechRate,
        List<Silence> silences
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmotionPoint(double t, double value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpeechRatePoint(double t, int wpm) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Silence(double start, double end, double duration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TranscriptItem(double start, double end, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiRaw(double durationSec,
                            List<EmotionPoint> emotionIntensity,
                            List<TranscriptItem> transcript) {}

    public record IndexEntry(String id, String label, String createdAt, double durationSec) {}
}
