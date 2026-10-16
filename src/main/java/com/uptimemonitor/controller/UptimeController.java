package com.uptimemonitor.controller;

import com.uptimemonitor.model.CheckResult;
import com.uptimemonitor.service.UptimeCheckerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Phase 1 controller – single-URL checker at the root path.
 * Still fully functional; Phase 2 adds /dashboard alongside it.
 */
@Controller
public class UptimeController {

    private final UptimeCheckerService checker;

    public UptimeController(UptimeCheckerService checker) {
        this.checker = checker;
    }

    /** Render the Phase 1 home page. */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /** Accept the single URL form and return results on the same page. */
    @PostMapping("/check")
    public String checkUrl(@RequestParam(value = "url", defaultValue = "") String url,
                           Model model) {

        CheckResult result = checker.check(url);
        model.addAttribute("result", result);
        model.addAttribute("submittedUrl", url);
        return "index";
    }
}
