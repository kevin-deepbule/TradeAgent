package com.tradeagent.research.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeagent.research.dto.AdapterPayloads.Industry;
import com.tradeagent.research.model.ResearchRun;
import com.tradeagent.research.model.ValuationRunRequest;
import com.tradeagent.research.service.ResearchService;

/** Public REST API for financial-report valuation research tasks. */
@RestController
@RequestMapping("/api/research")
public class ResearchController {
    private final ResearchService researchService;

    /** Create the controller with the financial research workflow. */
    public ResearchController(ResearchService researchService) {
        this.researchService = researchService;
    }

    /** List selectable Shenwan level-three industries. */
    @GetMapping("/industries")
    public List<Industry> listIndustries() {
        return researchService.listIndustries();
    }

    /** Queue a multi-industry financial valuation run. */
    @PostMapping("/valuation-runs")
    public ResponseEntity<ResearchRun> startRun(@RequestBody ValuationRunRequest request) {
        return ResponseEntity.accepted().body(researchService.start(request));
    }

    /** Read progress, ranked results, evidence, and any task failure. */
    @GetMapping("/valuation-runs/{runId}")
    public ResearchRun getRun(@PathVariable String runId) {
        return researchService.get(runId);
    }
}
