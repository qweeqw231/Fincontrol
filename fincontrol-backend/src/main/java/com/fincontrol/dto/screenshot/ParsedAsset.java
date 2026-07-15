package com.fincontrol.dto.screenshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 2.2 parse 响应 data 的子结构（[api-contract.md §2.2](#)）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParsedAsset {

    private String conversationId;
    private String snapshotDate;
    private BigDecimal totalAsset;
    private BigDecimal sixCategoriesTotal;
    private BigDecimal balanceFund;
    private List<CategoryBlock> categories;
    private List<String> matchedFunds;
    private List<String> unmatchedFunds;
    private String aiMarkdownReport;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CategoryBlock {
        private String categoryName;
        private List<FundLine> funds;
        private BigDecimal categoryTotal;
        private BigDecimal categoryPercentage;
        private BigDecimal targetRatio;
        private BigDecimal deviation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FundLine {
        private String fundName;
        private BigDecimal amount;
        private BigDecimal profit;
    }
}
