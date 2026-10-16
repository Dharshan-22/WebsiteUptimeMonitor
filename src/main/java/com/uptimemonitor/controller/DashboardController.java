package com.uptimemonitor.controller;

import com.uptimemonitor.model.CheckHistoryItem;
import com.uptimemonitor.model.MonitoredSite;
import com.uptimemonitor.service.CheckHistoryRepository;
import com.uptimemonitor.service.SiteRepository;
import com.uptimemonitor.service.UptimeCheckerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles all dashboard routes (Phase 2, 3, and 4):
 *
 * <pre>
 *   GET  /dashboard                – show monitored sites and check history
 *   POST /dashboard/add            – add a new site (saved to MySQL)
 *   POST /dashboard/remove/{id}    – remove a site (cascades history)
 *   POST /dashboard/check-now/{id} – run an immediate check and persist result
 * </pre>
 *
 * All mutating actions redirect back to GET /dashboard (PRG pattern).
 */
@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private final SiteRepository        siteRepo;
    private final CheckHistoryRepository historyRepo;
    private final UptimeCheckerService  checker;

    public DashboardController(SiteRepository siteRepo,
                                CheckHistoryRepository historyRepo,
                                UptimeCheckerService checker) {
        this.siteRepo    = siteRepo;
        this.historyRepo = historyRepo;
        this.checker     = checker;
    }

    // -------------------------------------------------------------------------
    //  GET /dashboard
    // -------------------------------------------------------------------------

    @GetMapping
    public String dashboard(@RequestParam(value = "siteId", required = false) String selectedSiteId,
                            Model model) {
        // Load all sites from MySQL
        List<MonitoredSite> sites = siteRepo.findAll();

        // Hydrate each site with its most recent check result from check_history
        for (MonitoredSite site : sites) {
            historyRepo.loadLastResultInto(site);
        }
        model.addAttribute("sites", sites);

        // Fetch check history (either for a selected site or across all sites)
        List<CheckHistoryItem> history;
        MonitoredSite selectedSite = null;

        if (selectedSiteId != null && !selectedSiteId.isBlank()) {
            selectedSite = siteRepo.findById(selectedSiteId);
            if (selectedSite != null) {
                history = historyRepo.findBySiteId(selectedSiteId);
            } else {
                // If an invalid or deleted siteId was requested, fall back gracefully
                selectedSiteId = null;
                history = historyRepo.findAllRecent();
            }
        } else {
            history = historyRepo.findAllRecent();
        }

        model.addAttribute("history", history);
        model.addAttribute("selectedSiteId", selectedSiteId);
        model.addAttribute("selectedSite", selectedSite);

        return "dashboard";
    }

    // -------------------------------------------------------------------------
    //  POST /dashboard/add
    // -------------------------------------------------------------------------

    /**
     * Validates the URL (security checks included), creates a MonitoredSite,
     * saves it to MySQL, then redirects to the dashboard.
     */
    @PostMapping("/add")
    public String addSite(@RequestParam("name") String name,
                          @RequestParam("url")  String url,
                          RedirectAttributes    redirectAttrs) {

        // Trim inputs
        name = name == null ? "" : name.trim();
        url  = url  == null ? "" : url.trim();

        // Validate name
        if (name.isBlank()) {
            redirectAttrs.addFlashAttribute("addError", "Site name must not be empty.");
            redirectAttrs.addFlashAttribute("addName", name);
            redirectAttrs.addFlashAttribute("addUrl",  url);
            return "redirect:/dashboard";
        }
        if (name.length() > 100) {
            redirectAttrs.addFlashAttribute("addError", "Site name is too long (max 100 characters).");
            redirectAttrs.addFlashAttribute("addName", name);
            redirectAttrs.addFlashAttribute("addUrl",  url);
            return "redirect:/dashboard";
        }

        // Validate URL — all security checks (localhost, private IP, scheme) run here
        String[] errorOut = new String[1];
        String normalizedUrl = checker.validateUrl(url, errorOut);
        if (normalizedUrl == null) {
            redirectAttrs.addFlashAttribute("addError", errorOut[0]);
            redirectAttrs.addFlashAttribute("addName", name);
            redirectAttrs.addFlashAttribute("addUrl",  url);
            return "redirect:/dashboard";
        }

        // Persist to MySQL
        MonitoredSite site = new MonitoredSite(name, normalizedUrl);
        siteRepo.save(site);

        redirectAttrs.addFlashAttribute("addSuccess",
            "\"" + name + "\" added successfully. Click Check Now to run the first check.");
        return "redirect:/dashboard";
    }

    // -------------------------------------------------------------------------
    //  POST /dashboard/remove/{id}
    // -------------------------------------------------------------------------

    /**
     * Deletes the site from MySQL. The ON DELETE CASCADE foreign key on
     * check_history automatically removes all history rows for this site.
     */
    @PostMapping("/remove/{id}")
    public String removeSite(@PathVariable("id") String id,
                             RedirectAttributes redirectAttrs) {

        MonitoredSite site = siteRepo.findById(id);
        if (site != null) {
            siteRepo.deleteById(id);   // history deleted by CASCADE
            redirectAttrs.addFlashAttribute("removeSuccess",
                "\"" + site.getName() + "\" has been removed.");
        }
        return "redirect:/dashboard";
    }

    // -------------------------------------------------------------------------
    //  POST /dashboard/check-now/{id}
    // -------------------------------------------------------------------------

    /**
     * Performs an HTTP check and persists the result to check_history.
     * Redirects to the dashboard focused on this site's history so the newly
     * recorded check is immediately visible.
     */
    @PostMapping("/check-now/{id}")
    public String checkNow(@PathVariable("id") String id,
                           RedirectAttributes redirectAttrs) {

        MonitoredSite site = siteRepo.findById(id);
        if (site == null) {
            redirectAttrs.addFlashAttribute("globalError", "Site not found.");
            return "redirect:/dashboard";
        }

        // performCheck() re-validates every redirect target for SSRF safety
        var result = checker.performCheck(site.getUrl());
        LocalDateTime now = LocalDateTime.now();

        // Persist the result to MySQL
        historyRepo.save(site.getId(), now, result);

        return "redirect:/dashboard?siteId=" + site.getId() + "#history";
    }
}
