package com.tradeagent.research.valuation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Loads immutable first-version valuation policies from the module classpath. */
@Component
public class ValuationPolicyProvider {
    private final PolicyFile policyFile;

    /** Load and validate the checked-in policy file at application startup. */
    public ValuationPolicyProvider(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource("valuation-policy-v1.json");
        try (InputStream input = resource.getInputStream()) {
            this.policyFile = objectMapper.readValue(input, PolicyFile.class);
        } catch (IOException exc) {
            throw new IllegalStateException("无法加载财报估值规则", exc);
        }
    }

    /** Select the first keyword-matched industry policy or the default policy. */
    public ValuationPolicy select(String industryName, String parentName) {
        String text = (industryName == null ? "" : industryName) + " "
                + (parentName == null ? "" : parentName);
        PolicyRule selected = policyFile.rules().stream()
                .filter(rule -> rule.keywords().stream().anyMatch(text::contains))
                .findFirst()
                .orElse(policyFile.defaultPolicy());
        return new ValuationPolicy(
                policyFile.version(),
                selected.basePe(),
                selected.minPe(),
                selected.maxPe(),
                policyFile.bands().deepValue(),
                policyFile.bands().value(),
                policyFile.bands().fair(),
                policyFile.bands().expensive());
    }

    /** On-disk configuration for one immutable policy version. */
    private record PolicyFile(String version, PolicyRule defaultPolicy, List<PolicyRule> rules, Bands bands) {
    }

    /** Keyword-matched reasonable-PE boundaries. */
    private record PolicyRule(List<String> keywords, double basePe, double minPe, double maxPe) {
    }

    /** Shared current-price to fair-price boundaries for the five valuation bands. */
    private record Bands(double deepValue, double value, double fair, double expensive) {
    }
}
