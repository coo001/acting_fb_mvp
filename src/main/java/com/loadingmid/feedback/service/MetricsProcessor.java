package com.loadingmid.feedback.service;

import com.loadingmid.feedback.dto.AnalysisResult.Silence;
import com.loadingmid.feedback.dto.AnalysisResult.SpeechRatePoint;
import com.loadingmid.feedback.dto.AnalysisResult.TranscriptItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class MetricsProcessor {

    public record Metrics(List<SpeechRatePoint> speechRate, List<Silence> silences) {}

    public Metrics process(List<TranscriptItem> transcript, double durationSec) {
        List<TranscriptItem> sorted = (transcript == null ? List.<TranscriptItem>of() : transcript)
                .stream()
                .sorted(Comparator.comparingDouble(TranscriptItem::start))
                .toList();
        return new Metrics(computeSpeechRate(sorted, durationSec), computeSilences(sorted));
    }

    private List<SpeechRatePoint> computeSpeechRate(List<TranscriptItem> items, double durationSec) {
        List<SpeechRatePoint> result = new ArrayList<>();
        int maxT = (int) Math.floor(Math.max(0.0, durationSec));
        for (int t = 0; t <= maxT; t++) {
            double winStart = Math.max(0.0, t - 2.5);
            double winEnd = Math.min(durationSec, t + 2.5);
            int words = 0;
            for (TranscriptItem item : items) {
                if (item.end() < winStart || item.start() > winEnd) continue;
                String text = item.text() == null ? "" : item.text().trim();
                if (text.isEmpty()) continue;
                words += text.split("\\s+").length;
            }
            result.add(new SpeechRatePoint(t, words * 12));
        }
        return result;
    }

    private List<Silence> computeSilences(List<TranscriptItem> items) {
        List<Silence> result = new ArrayList<>();
        for (int i = 0; i < items.size() - 1; i++) {
            double gap = items.get(i + 1).start() - items.get(i).end();
            if (gap >= 0.8) {
                double start = items.get(i).end();
                double end = items.get(i + 1).start();
                result.add(new Silence(round1(start), round1(end), round1(gap)));
            }
        }
        return result;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
