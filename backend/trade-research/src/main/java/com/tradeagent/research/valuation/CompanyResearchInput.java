package com.tradeagent.research.valuation;

import java.util.List;

import com.tradeagent.research.dto.AdapterPayloads.ConsensusForecast;
import com.tradeagent.research.dto.AdapterPayloads.Constituent;
import com.tradeagent.research.dto.AdapterPayloads.Disclosure;
import com.tradeagent.research.dto.AdapterPayloads.FinancialHistory;
import com.tradeagent.research.dto.AdapterPayloads.Industry;
import com.tradeagent.research.dto.AdapterPayloads.MarketSnapshot;

/** All deterministic inputs used to value one company in a research run. */
public record CompanyResearchInput(
        Industry industry,
        Constituent constituent,
        List<Disclosure> disclosures,
        FinancialHistory financialHistory,
        MarketSnapshot marketSnapshot,
        ConsensusForecast consensusForecast) {
}
