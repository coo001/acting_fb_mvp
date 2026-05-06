package com.loadingmid.feedback.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loadingmid.feedback.dto.AnalysisResult;
import com.loadingmid.feedback.dto.AnalysisResult.IndexEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ResultStore {

    private static final Logger log = LoggerFactory.getLogger(ResultStore.class);

    private final Path resultsDir;
    private final Path indexFile;
    private final ObjectMapper mapper;

    public ResultStore(@Value("${app.data-dir}") String dataDirPath) throws IOException {
        Path dataDir = Paths.get(dataDirPath);
        this.resultsDir = dataDir.resolve("results");
        this.indexFile = dataDir.resolve("index.json");
        Files.createDirectories(resultsDir);
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        log.info("ResultStore 초기화: data dir = {}", dataDir.toAbsolutePath());
    }

    public void save(AnalysisResult result) throws IOException {
        Path file = resultsDir.resolve(result.videoId() + ".json");
        mapper.writeValue(file.toFile(), result);
        List<IndexEntry> index = readIndex();
        index.add(0, new IndexEntry(result.videoId(), result.label(), result.createdAt(), result.durationSec()));
        mapper.writeValue(indexFile.toFile(), index);
        log.info("결과 저장 완료: {}", file);
    }

    public Optional<AnalysisResult> load(String id) {
        Path file = resultsDir.resolve(id + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file.toFile(), AnalysisResult.class));
        } catch (IOException e) {
            log.warn("결과 로드 실패 id={}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public List<IndexEntry> listRecent(int n) {
        List<IndexEntry> all = readIndex();
        return all.stream().limit(n).toList();
    }

    public List<IndexEntry> listAll() {
        return readIndex();
    }

    private List<IndexEntry> readIndex() {
        if (!Files.exists(indexFile)) return new ArrayList<>();
        try {
            return new ArrayList<>(
                    mapper.readValue(indexFile.toFile(), new TypeReference<List<IndexEntry>>() {})
            );
        } catch (IOException e) {
            log.warn("index.json 읽기 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
