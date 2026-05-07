package com.loadingmid.feedback.service;

import com.loadingmid.feedback.dto.AnalysisResult;
import com.loadingmid.feedback.dto.Job;
import com.loadingmid.feedback.dto.JobStatus;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "analysis-worker");
        t.setDaemon(true);
        return t;
    });

    private final GeminiService geminiService;
    private final ResultStore resultStore;

    public JobService(GeminiService geminiService, ResultStore resultStore) {
        this.geminiService = geminiService;
        this.resultStore = resultStore;
    }

    public String submit(Path tmpVideoPath, String mimeType, String label) {
        String jobId = UUID.randomUUID().toString();
        Job initial = new Job(jobId, label, Instant.now().toString(), JobStatus.QUEUED, "큐 대기 중", null, null);
        jobs.put(jobId, initial);
        worker.submit(() -> run(jobId, tmpVideoPath, mimeType, label));
        log.info("Job {} 큐 등록 label={}", jobId, label);
        return jobId;
    }

    public Optional<Job> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public List<Job> listActive() {
        return jobs.values().stream()
                .filter(j -> j.status() == JobStatus.QUEUED || j.status() == JobStatus.RUNNING)
                .sorted(Comparator.comparing(Job::createdAt))
                .toList();
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }

    private void run(String jobId, Path tmpVideoPath, String mimeType, String label) {
        try {
            updatePhase(jobId, JobStatus.RUNNING, "준비 중");
            AnalysisResult result = geminiService.analyze(
                    tmpVideoPath,
                    mimeType,
                    label,
                    phase -> updatePhase(jobId, JobStatus.RUNNING, phase)
            );
            resultStore.save(result);
            jobs.computeIfPresent(jobId, (k, v) -> v.done(result.videoId()));
            log.info("Job {} 완료 → result {}", jobId, result.videoId());
        } catch (Exception e) {
            log.error("Job {} 실패", jobId, e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            jobs.computeIfPresent(jobId, (k, v) -> v.failed(msg));
        } finally {
            try {
                Files.deleteIfExists(tmpVideoPath);
            } catch (Exception ignore) {
            }
        }
    }

    private void updatePhase(String jobId, JobStatus status, String phase) {
        jobs.computeIfPresent(jobId, (k, v) -> v.withStatus(status, phase));
    }
}
