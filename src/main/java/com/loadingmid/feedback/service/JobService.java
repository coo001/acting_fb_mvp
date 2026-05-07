package com.loadingmid.feedback.service;

import com.loadingmid.feedback.dto.AnalysisResult;
import com.loadingmid.feedback.dto.AnalysisResult.GeminiRaw;
import com.loadingmid.feedback.dto.Job;
import com.loadingmid.feedback.dto.JobStatus;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private final Path videosDir;

    public JobService(GeminiService geminiService,
                      ResultStore resultStore,
                      @Value("${app.data-dir}") String dataDirPath) throws java.io.IOException {
        this.geminiService = geminiService;
        this.resultStore = resultStore;
        this.videosDir = Paths.get(dataDirPath).resolve("videos");
        Files.createDirectories(this.videosDir);
    }

    public String submit(Path tmpVideoPath, String mimeType, String extension, String label) {
        String jobId = UUID.randomUUID().toString();
        Job initial = new Job(jobId, label, Instant.now().toString(), JobStatus.QUEUED, "큐 대기 중", null, null);
        jobs.put(jobId, initial);
        worker.submit(() -> run(jobId, tmpVideoPath, mimeType, extension, label));
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

    private void run(String jobId, Path tmpVideoPath, String mimeType, String extension, String label) {
        boolean videoMoved = false;
        try {
            updatePhase(jobId, JobStatus.RUNNING, "준비 중");
            GeminiRaw raw = geminiService.analyze(
                    tmpVideoPath,
                    mimeType,
                    label,
                    phase -> updatePhase(jobId, JobStatus.RUNNING, phase)
            );
            String resultId = UUID.randomUUID().toString();
            String videoFilename = resultId + extension;
            Path videoTarget = videosDir.resolve(videoFilename);
            Files.move(tmpVideoPath, videoTarget, StandardCopyOption.REPLACE_EXISTING);
            videoMoved = true;
            log.info("영상 보존: {}", videoTarget);

            AnalysisResult result = new AnalysisResult(
                    resultId,
                    Instant.now().toString(),
                    label,
                    raw.durationSec(),
                    videoFilename,
                    raw.observations(),
                    raw.transcript()
            );
            resultStore.save(result);
            jobs.computeIfPresent(jobId, (k, v) -> v.done(resultId));
            log.info("Job {} 완료 → result {}", jobId, resultId);
        } catch (Exception e) {
            log.error("Job {} 실패", jobId, e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            jobs.computeIfPresent(jobId, (k, v) -> v.failed(msg));
        } finally {
            if (!videoMoved) {
                try {
                    Files.deleteIfExists(tmpVideoPath);
                } catch (Exception ignore) {
                }
            }
        }
    }

    private void updatePhase(String jobId, JobStatus status, String phase) {
        jobs.computeIfPresent(jobId, (k, v) -> v.withStatus(status, phase));
    }
}
