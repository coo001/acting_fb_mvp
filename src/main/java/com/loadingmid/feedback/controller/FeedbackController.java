package com.loadingmid.feedback.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loadingmid.feedback.dto.AnalysisResult;
import com.loadingmid.feedback.dto.AnalysisResult.IndexEntry;
import com.loadingmid.feedback.service.GeminiService;
import com.loadingmid.feedback.service.ResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);
    private static final long MAX_BYTES = 500L * 1024 * 1024;

    private final GeminiService geminiService;
    private final ResultStore resultStore;
    private final ObjectMapper mapper = new ObjectMapper();

    public FeedbackController(GeminiService geminiService, ResultStore resultStore) {
        this.geminiService = geminiService;
        this.resultStore = resultStore;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("recent", resultStore.listRecent(10));
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam("file") MultipartFile file,
                          @RequestParam("label") String label,
                          RedirectAttributes redirect) {
        try {
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

            log.info("분석 시작 label={} size={}bytes", label, file.getSize());
            AnalysisResult result = geminiService.analyze(file, label.trim());
            resultStore.save(result);
            log.info("분석 완료 id={}", result.videoId());
            return "redirect:/result/" + result.videoId();
        } catch (Exception e) {
            log.error("분석 실패", e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            redirect.addFlashAttribute("errorMessage", "분석 실패: " + msg);
            return "redirect:/";
        }
    }

    @GetMapping("/result/{id}")
    public String result(@PathVariable String id,
                         @RequestParam(value = "compareWith", required = false) String compareWith,
                         Model model) {
        Optional<AnalysisResult> currentOpt = resultStore.load(id);
        if (currentOpt.isEmpty()) {
            model.addAttribute("errorMessage", "결과를 찾을 수 없음: " + id);
            model.addAttribute("recent", resultStore.listRecent(10));
            return "index";
        }
        AnalysisResult current = currentOpt.get();
        model.addAttribute("current", current);
        model.addAttribute("currentJson", toJson(current));

        List<IndexEntry> all = resultStore.listAll();
        List<IndexEntry> others = all.stream().filter(e -> !e.id().equals(id)).toList();
        model.addAttribute("others", others);

        if (compareWith != null && !compareWith.isBlank()) {
            Optional<AnalysisResult> compareOpt = resultStore.load(compareWith);
            if (compareOpt.isPresent()) {
                model.addAttribute("compare", compareOpt.get());
                model.addAttribute("compareJson", toJson(compareOpt.get()));
            }
            model.addAttribute("compareWithId", compareWith);
        }

        return "result";
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
