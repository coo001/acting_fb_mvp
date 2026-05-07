package com.loadingmid.feedback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FileState;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.google.genai.types.UploadFileConfig;
import com.loadingmid.feedback.dto.AnalysisResult;
import com.loadingmid.feedback.dto.AnalysisResult.GeminiRaw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String MODEL = "gemini-2.5-pro";
    private static final long ACTIVE_WAIT_MS = 5L * 60 * 1000;
    private static final long POLL_INTERVAL_MS = 5_000;

    private final String apiKey;
    private final PromptLoader promptLoader;
    private final MetricsProcessor metricsProcessor;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiService(@Value("${gemini.api-key:}") String apiKey,
                         PromptLoader promptLoader,
                         MetricsProcessor metricsProcessor) {
        this.apiKey = apiKey;
        this.promptLoader = promptLoader;
        this.metricsProcessor = metricsProcessor;
    }

    public AnalysisResult analyze(Path videoPath, String mimeType, String label, Consumer<String> onPhase) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY 환경변수가 설정되지 않았음");
        }

        Client client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout(600_000).build())
                .build();

        String fileName = null;
        try {
            onPhase.accept("Gemini 업로드 중");
            log.info("Files API 업로드 시작 mime={}", mimeType);
            com.google.genai.types.File uploaded = client.files.upload(
                    videoPath.toString(),
                    UploadFileConfig.builder().mimeType(mimeType).build()
            );
            fileName = uploaded.name().orElseThrow(() -> new IllegalStateException("업로드 응답에 name 없음"));
            log.info("업로드 완료 name={} 초기 state={}", fileName,
                    uploaded.state().map(FileState::toString).orElse("null"));

            onPhase.accept("Gemini 처리 대기 중");
            uploaded = waitForActive(client, uploaded);
            String fileUri = uploaded.uri().orElseThrow(() -> new IllegalStateException("업로드 응답에 uri 없음"));

            String userPrompt = "라벨: " + label
                    + "\n위 영상에 대해 systemInstruction에 정의된 메트릭(durationSec, emotionIntensity, transcript)을 JSON으로 출력하라.";

            Content content = Content.fromParts(
                    Part.fromUri(fileUri, mimeType),
                    Part.fromText(userPrompt)
            );

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(promptLoader.load())))
                    .responseMimeType("application/json")
                    .responseSchema(buildResponseSchema())
                    .build();

            onPhase.accept("분석 중 (보통 1~2분)");
            log.info("generateContent 호출 model={}", MODEL);
            GenerateContentResponse response = client.models.generateContent(MODEL, content, config);
            String json = response.text();
            if (json == null || json.isBlank()) {
                throw new IllegalStateException("Gemini 응답이 비어있음");
            }
            log.info("Gemini 응답 수신 (len={})", json.length());

            onPhase.accept("메트릭 계산 중");
            GeminiRaw raw = mapper.readValue(json, GeminiRaw.class);
            MetricsProcessor.Metrics metrics = metricsProcessor.process(raw.transcript(), raw.durationSec());

            return new AnalysisResult(
                    UUID.randomUUID().toString(),
                    Instant.now().toString(),
                    label,
                    raw.durationSec(),
                    raw.emotionIntensity(),
                    metrics.speechRate(),
                    metrics.silences()
            );
        } finally {
            if (fileName != null) {
                try {
                    client.files.delete(fileName, null);
                    log.info("Gemini 파일 삭제 완료 name={}", fileName);
                } catch (Exception e) {
                    log.warn("Gemini 파일 삭제 실패 name={}: {}", fileName, e.getMessage());
                }
            }
            try {
                client.close();
            } catch (Exception ignore) {
            }
        }
    }

    private com.google.genai.types.File waitForActive(Client client, com.google.genai.types.File initial) throws InterruptedException {
        com.google.genai.types.File current = initial;
        long deadline = System.currentTimeMillis() + ACTIVE_WAIT_MS;
        while (true) {
            FileState.Known state = current.state()
                    .map(FileState::knownEnum)
                    .orElse(FileState.Known.STATE_UNSPECIFIED);
            if (state == FileState.Known.ACTIVE) {
                log.info("Files API ACTIVE 도달");
                return current;
            }
            if (state == FileState.Known.FAILED) {
                throw new IllegalStateException("Files API FAILED");
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException("Files API ACTIVE 대기 timeout (5분)");
            }
            log.info("Files API state={}, {}ms 대기", state, POLL_INTERVAL_MS);
            Thread.sleep(POLL_INTERVAL_MS);
            String name = current.name().orElseThrow();
            current = client.files.get(name, null);
        }
    }

    private Schema buildResponseSchema() {
        Schema number = Schema.builder().type(Type.Known.NUMBER).build();
        Schema string = Schema.builder().type(Type.Known.STRING).build();

        Map<String, Schema> emotionProps = new LinkedHashMap<>();
        emotionProps.put("t", number);
        emotionProps.put("value", number);
        Schema emotionItem = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(emotionProps)
                .required("t", "value")
                .build();

        Map<String, Schema> transcriptProps = new LinkedHashMap<>();
        transcriptProps.put("start", number);
        transcriptProps.put("end", number);
        transcriptProps.put("text", string);
        Schema transcriptItem = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(transcriptProps)
                .required("start", "end", "text")
                .build();

        Schema emotionArray = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(emotionItem)
                .build();

        Schema transcriptArray = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(transcriptItem)
                .build();

        Map<String, Schema> rootProps = new LinkedHashMap<>();
        rootProps.put("durationSec", number);
        rootProps.put("emotionIntensity", emotionArray);
        rootProps.put("transcript", transcriptArray);

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(rootProps)
                .required("durationSec", "emotionIntensity", "transcript")
                .build();
    }
}
