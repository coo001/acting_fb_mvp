package com.loadingmid.feedback.dto;

public record Job(
        String id,
        String label,
        String createdAt,
        JobStatus status,
        String phase,
        String resultId,
        String errorMessage
) {
    public Job withStatus(JobStatus newStatus, String newPhase) {
        return new Job(id, label, createdAt, newStatus, newPhase, resultId, errorMessage);
    }

    public Job done(String newResultId) {
        return new Job(id, label, createdAt, JobStatus.DONE, "완료", newResultId, null);
    }

    public Job failed(String message) {
        return new Job(id, label, createdAt, JobStatus.FAILED, "실패", null, message);
    }
}
