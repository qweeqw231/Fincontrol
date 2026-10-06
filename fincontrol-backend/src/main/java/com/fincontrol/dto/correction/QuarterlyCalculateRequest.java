package com.fincontrol.dto.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 2a `POST /api/correction/quarterly/calculate` 请求体（LQR-ZOH 联合校正求解）。
 *
 * <p>缺省值策略：{@code vCurr/categories/targetRatios/uHigh/purchaseThreshold} 缺省时
 * 后端从当前快照与 user_config 读取；{@code surplus} 与 {@code mMax} 至少给出一个。
 */
@Data
public class QuarterlyCalculateRequest {

    /** 六大类合计（缺省从当前快照取） */
    @JsonProperty("vCurr")
    private BigDecimal vCurr;

    /** 六大类金额（canonical 名 → 金额；缺省从当前快照取） */
    private Map<String, BigDecimal> categories;

    /** 当月可支配结余 S（元）；给出时 M_max 默认 = 1.8 × S */
    private BigDecimal surplus;

    /** 总投入上限 M_max（元）；缺省 = 1.8 × S */
    @JsonProperty("mMax")
    private BigDecimal mMax;

    /** M_max 来源：default / manual（默认根据是否显式传入 mMax 判定） */
    @JsonProperty("mMaxSource")
    private String mMaxSource;

    /** 当月高波定投总额（缺省从 user_config 取） */
    @JsonProperty("uHigh")
    private BigDecimal uHigh;

    /** 低波货币定投份额（元，默认 0） */
    @JsonProperty("uMonetaryDca")
    private BigDecimal uMonetaryDca;

    /** 低波固收定投份额（元，默认 0） */
    @JsonProperty("uBondDca")
    private BigDecimal uBondDca;

    /** α 初始值（小数，默认 0.20） */
    private BigDecimal alphaInit;

    /** α 递减因子（每轮 ×，默认 0.8） */
    private BigDecimal alphaDecay;

    /** ZOH 补仓触发阈值（缺省从 user_config 取，默认 100） */
    private BigDecimal purchaseThreshold;

    /** 六大类目标比例（缺省从 user_config 取） */
    private Map<String, BigDecimal> targetRatios;

    /** 求解模式：auto=α 迭代压缩（默认）；mMaxCapped=人为上限倒推 + 阶跃点分析 */
    private String mode;

    /** α 探针列表（百分数，如 [20,30,35,39,45,50]；缺省用内置默认） */
    private List<BigDecimal> alphaProbes;

    /** α 探针基准上限（默认 = 1.8 × S）；用于 9/30 式“默认上限下的分布式 α 表” */
    private BigDecimal probeBaseMmax;

    /** 币种阈值上限保护：迭代最大轮数（默认 20） */
    private Integer maxIterations;
}