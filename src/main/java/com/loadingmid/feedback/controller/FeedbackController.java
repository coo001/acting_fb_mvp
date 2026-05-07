package com.loadingmid.feedback.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loadingmid.feedback.dto.AnalysisResult;
import com.loadingmid.feedback.dto.AnalysisResult.IndexEntry;
import com.loadingmid.feedback.dto.Job;
import com.loadingmid.feedback.service.JobService;
import com.loadingmid.feedback.service.ResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@Controller
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);
    private static final long MAX_BYTES = 500L * 1024 * 1024;

    private final JobService jobService;
    private final ResultStore resultStore;
    private final ObjectMapper mapper = new ObjectMapper();

    public FeedbackController(JobService jobService, ResultStore resultStore) {
        this.jobService = jobService;
        this.resultStore = resultStore;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("recent", resultStore.listRecent(10));
        model.addAttribute("activeJobs", jobService.listActive());
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam(value = "file", required = false) MultipartFile file,
                          @RequestParam(value = "label", required = false) String label,
                          RedirectAttributes redirect) {
        try {
            log.info("analyze 호출 file={} size={} name={} label={}",
                    file == null ? "null" : "present",
                    file == null ? -1 : file.getSize(),
                    file == null ? "n/a" : file.getOriginalFilename(),
                    label);
            if (file == null || file.isEmpty()) {
                redirect.addFlashAttribute("errorMessage", "영상 파일을 선택해줘");
                return "redirect:/";
            }
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            if (!(name.endsWith(".mp4") || name.endsWith(".mov"))) {
                redirect.addFlashAttribute("errorMessage", "mp4 또는 mov 파일만 가능");
                return "redirect:/";
            }
            if (file.getSize() > MAX_BYTES) {
                redirect.addFlashAttribute("errorMessage", "500MB 초과 영상은 못 받음");
                return "redirect:/";
            }
            if (label == null || label.isBlank()) {
                redirect.addFlashAttribute("errorMessage", "라벨을 입력해줘");
                return "redirect:/";
            }

            boolean isMov = name.endsWith(".mov");
            String ext = isMov ? ".mov" : ".mp4";
            String mimeType = isMov ? "video/quicktime" : "video/mp4";

            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "acting_uploads");
            Files.createDirectories(tmpDir);
            Path tmpFile = tmpDir.resolve(UUID.randomUUID() + ext);
            file.transferTo(tmpFile.toFile());
            log.info("tmp 영상 저장: {} ({} bytes)", tmpFile, file.getSize());

            String jobId = jobService.submit(tmpFile, mimeType, ext, label.trim());
            return "redirect:/job/" + jobId;
        } catch (Exception e) {
            log.error("작업 등록 실패", e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            redirect.addFlashAttribute("errorMessage", "분석 실패: " + msg);
            return "redirect:/";
        }
    }

    @GetMapping("/job/{id}")
    public String job(@PathVariable String id, Model model) {
        Optional<Job> jobOpt = jobService.get(id);
        if (jobOpt.isEmpty()) {
            model.addAttribute("errorMessage", "작업을 찾을 수 없음: " + id);
            model.addAttribute("recent", resultStore.listRecent(10));
            model.addAttribute("activeJobs", jobService.listActive());
            return "index";
        }
        model.addAttribute("job", jobOpt.get());
        return "job";
    }

    @GetMapping("/api/jobs/{id}")
    @ResponseBody
    public ResponseEntity<Job> jobApi(@PathVariable String id) {
        return jobService.get(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/result/{id}")
    public String result(@PathVariable String id, Model model) {
        Optional<AnalysisResult> currentOpt = resultStore.load(id);
        if (currentOpt.isEmpty()) {
            model.addAttribute("errorMessage", "결과를 찾을 수 없음: " + id);
            model.addAttribute("recent", resultStore.listRecent(10));
            model.addAttribute("activeJobs", jobService.listActive());
            return "index";
        }
        AnalysisResult current = currentOpt.get();
        model.addAttribute("current", current);
        model.addAttribute("currentJson", toJson(current));
        return "result";
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleTooLarge(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "500MB 초과 영상은 못 받음");
        return "redirect:/";
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public String handleMissingPart(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "영상 파일을 선택해줘");
        return "redirect:/";
    }

    @ExceptionHandler(MultipartException.class)
    public String handleMultipart(MultipartException e, RedirectAttributes redirect) {
        log.warn("multipart 파싱 실패: {}", e.getMessage());
        redirect.addFlashAttribute("errorMessage", "업로드 실패: " + e.getMessage());
        return "redirect:/";
    }
}
