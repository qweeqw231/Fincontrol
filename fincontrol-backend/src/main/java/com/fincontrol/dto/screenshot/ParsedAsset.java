package com.fincontrol.dto.screenshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /**
     * 1a.9：合并后 totalAsset 的来源标记。
     * <ul>
     *   <li>"top" — 4 页顶部"总资产"一致，使用顶部值（7884.68）</li>
     *   <li>"visible_sum" — 4 页顶部不一致 / 全部为 null，fallback 到 deduped fund amount 加总</li>
     * </ul>
     */
    private String totalAssetSource;

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
        // 1b.3 P6 修复（P5-3 + P6-3 配套）：前端"各类小计"基金数显示依赖此字段
        // Lombok @Data 自动生成 getFundCount() / setFundCount(Integer)
        private Integer fundCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FundLine {
        private String fundName;
        private BigDecimal amount;
        /** 1a.8.7 兼容期：与 holdingProfit 同步填充。 */
        private BigDecimal profit;
        /** 严格=持有收益（不含当日浮盈）。1a.8.7。 */
        private BigDecimal holdingProfit;
        /** 含已实现盈亏。1a.8.7。 */
        private BigDecimal cumulativeProfit;
        /** 1a.8.8：来自 FundCategoryResolver；true=user_correct，false=ai_guess/未命中。 */
        private Boolean isUserConfirmed;
        /** 1a.10：仅 user_correct 映射透传已有 fund_category_map.confirmed_at。 */
        private LocalDateTime confirmedAt;
    }
}
