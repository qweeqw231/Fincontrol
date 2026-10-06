package com.fincontrol.service;

import com.fincontrol.dto.correction.MonthlyCalculateRequest;
import com.fincontrol.dto.correction.MonthlyCalculateResponse;
import com.fincontrol.dto.correction.MonthlyRecalculateRequest;
import com.fincontrol.dto.correction.MonthlyRecalculateResponse;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.AlphaRow;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.AssetRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2a 校正求解服务单测：用四组真实校正数据做 oracle。
 *
 * <ul>
 *   <li>8/31 第二次单独 ZOH 校正：Δm≈186.81 / Δb≈282.20（operation_log #8）</li>
 *   <li>9/30 第二次 LQR-ZOH 联合校正：Δm=85.22@E=382、阶跃点 E*≈493、
 *       LQR 边界解 207.12/174.88/-12.53、IC-DRR≈67.4%（北极星计算过程 docx）</li>
 *   <li>6/30 第一次联合校正：α 七轮压缩结构（§3.6）</li>
 * </ul>
 */
class CorrectionSolveServiceTest {

    private final CorrectionSolveService service = new CorrectionSolveService();

    // ================= 低波方程组 =================

    @Test
    @DisplayName("2a-S01 · 9/30 oracle：Δm(E)=34.29+0.1333E，E=382 → 85.22 / 123.88")
    void lowVol_nineThirtyOracle() {
        CorrectionSolveService.LowVolSolution at382 = service.solveLowVol(
                bd("9525.87"), bd("988.27"), bd("1486.37"),
                bd("618"), ZERO, ZERO, bd("382"), frac("10"), frac("15"));
        assertClose(at382.deltaM, bd("85.22"), bd("0.02"));
        assertClose(at382.deltaB, bd("123.88"), bd("0.02"));

        CorrectionSolveService.LowVolSolution at0 = service.solveLowVol(
                bd("9525.87"), bd("988.27"), bd("1486.37"),
                bd("618"), ZERO, ZERO, ZERO, frac("10"), frac("15"));
        assertClose(at0.deltaM, bd("34.29"), bd("0.02"));
        assertClose(at0.deltaB, bd("47.48"), bd("0.02"));
    }

    @Test
    @DisplayName("2a-S02 · 8/31 oracle：Δm≈186.81 / Δb≈282.20（含高波定投 659）")
    void lowVol_augThirtyOneOracle() {
        CorrectionSolveService.LowVolSolution sol = service.solveLowVol(
                bd("8713.89"), bd("797.38"), bd("1194.11"),
                bd("659"), ZERO, ZERO, ZERO, frac("10"), frac("15"));
        assertClose(sol.deltaM, bd("186.81"), bd("0.05"));
        assertClose(sol.deltaB, bd("282.20"), bd("0.05"));
    }

    @Test
    @DisplayName("2a-S03 · 月度 calculate：8/31 触发 + round_up_10 建议 190/290")
    void monthlyCalculate_augThirtyOne() {
        MonthlyCalculateRequest req = new MonthlyCalculateRequest();
        req.setVCurr(bd("8713.89"));
        req.setVMonetary(bd("797.38"));
        req.setVBond(bd("1194.11"));
        req.setUHigh(bd("659"));
        req.setPMonetary(bd("10"));
        req.setPBond(bd("15"));
        req.setPurchaseThreshold(bd("100"));
        req.setBudgetLimit(bd("1000"));

        MonthlyCalculateResponse resp = service.solveMonthly(req);

        assertClose(resp.getDeltaMTheory(), bd("186.81"), bd("0.05"));
        assertClose(resp.getDeltaBTheory(), bd("282.20"), bd("0.05"));
        assertThat(resp.getZohTriggered()).isTrue();
        assertThat(resp.getRoundingSuggestion().getDeltaMSuggested()).isEqualByComparingTo("190");
        assertThat(resp.getRoundingSuggestion().getDeltaBSuggested()).isEqualByComparingTo("290");
        assertThat(resp.getRoundingSuggestion().getRoundingStrategy()).isEqualTo("round_up_10");
        // 总投入 = 659 + 186.81 + 282.20 ≈ 1128.01 > budgetLimit → 警告
        assertClose(resp.getTotalInvestment(), bd("1128.01"), bd("0.1"));
        assertThat(resp.getWarnings())
                .extracting(MonthlyCalculateResponse.WarningItem::getType)
                .contains("exceed_budget_limit");
    }

    @Test
    @DisplayName("2a-S04 · 月度 calculate：Δm<100 不触发（7/30/9/30 口径）")
    void monthlyCalculate_notTriggered() {
        MonthlyCalculateRequest req = new MonthlyCalculateRequest();
        req.setVCurr(bd("9525.87"));
        req.setVMonetary(bd("988.27"));
        req.setVBond(bd("1486.37"));
        req.setUHigh(bd("618"));
        req.setPMonetary(bd("10"));
        req.setPBond(bd("15"));
        req.setPurchaseThreshold(bd("100"));
        req.setEHigh(bd("382")); // 9/30 联合校正：高波预算 E=382 计入低波方程组分母

        MonthlyCalculateResponse resp = service.solveMonthly(req);

        assertClose(resp.getDeltaMTheory(), bd("85.22"), bd("0.02"));
        assertThat(resp.getZohTriggered()).isFalse();
        assertThat(resp.getRoundingSuggestion().getDeltaMSuggested()).isEqualByComparingTo("0");
        // 未触发：总投入 = 高波定投 618 + 高波校正预算 382
        assertClose(resp.getTotalInvestment(), bd("1000"), bd("0.01"));
        assertThat(resp.getWarnings())
                .extracting(MonthlyCalculateResponse.WarningItem::getType)
                .contains("zoh_not_triggered");
    }

    @Test
    @DisplayName("2a-S05 · 月度 recalculate：取整后新比例与偏差")
    void monthlyRecalculate() {
        MonthlyRecalculateRequest req = new MonthlyRecalculateRequest();
        req.setVCurr(bd("8713.89"));
        req.setVMonetary(bd("797.38"));
        req.setVBond(bd("1194.11"));
        req.setUHigh(bd("659"));
        req.setDeltaMActual(bd("190"));
        req.setDeltaBActual(bd("290"));
        req.setBudgetLimit(bd("1000"));

        MonthlyRecalculateResponse resp = service.recalculateMonthly(req);

        // 新分母 = 8713.89 + 659 + 190 + 290 = 9852.89
        // 货币 (797.38+190)/9852.89 ≈ 10.02% ；固收 (1194.11+290)/9852.89 ≈ 15.06%
        assertClose(resp.getNewRatios().get("货币类"), bd("10.02"), bd("0.02"));
        assertClose(resp.getNewRatios().get("固收类"), bd("15.06"), bd("0.02"));
        assertClose(resp.getDeviations().get("货币类"), bd("0.02"), bd("0.02"));
        assertClose(resp.getTotalInvestmentActual(), bd("1139.00"), bd("0.01"));
        assertThat(resp.getOverBudgetLimit()).isTrue();
    }

    // ================= 季度 LQR-ZOH 联合校正 =================

    @Test
    @DisplayName("2a-S06 · 9/30 联合校正 oracle：E=382 倒推、ZOH 不触发、KKT 207/175/0、IC-DRR 67.4%")
    void quarterly_nineThirtyOracle() {
        QuarterlyCalculateResponse resp = service.solveQuarterly(nineThirtyInput("mMaxCapped"));

        // 最终方案：人为上限 M_max=1000，E_high=382，Δm=85.22 < 100 不触发
        assertThat(resp.getChosen().getEHigh()).isEqualByComparingTo("382");
        assertClose(resp.getChosen().getDeltaMRaw(), bd("85.22"), bd("0.02"));
        assertThat(resp.getChosen().getZohTriggered()).isFalse();
        assertClose(resp.getChosen().getTotalInvestment(), bd("1000.00"), bd("0.01"));

        // 锚定资产 = 海外权益类（+3.85pp 超配最严重）
        assertThat(resp.getAnchor()).isEqualTo("海外权益类");

        // KKT 边界解：商品 207.12 / A股 174.88 / 港股 -12.53 → 置 0；取整 207/175/0
        Map<String, AssetRow> deltas = byCategory(resp.getLqr().getDeltas());
        assertClose(deltas.get("商品类").getDeltaRaw(), bd("207.12"), bd("0.05"));
        assertThat(deltas.get("商品类").getDeltaAmount()).isEqualByComparingTo("207");
        assertClose(deltas.get("A股权益类").getDeltaRaw(), bd("174.88"), bd("0.05"));
        assertThat(deltas.get("A股权益类").getDeltaAmount()).isEqualByComparingTo("175");
        assertThat(deltas.get("港股大中华类").getDeltaAmount()).isEqualByComparingTo("0");
        assertThat(deltas.get("海外权益类").getDeltaAmount()).isEqualByComparingTo("0");
        assertThat(resp.getLqr().getKktNote()).contains("内点解不成立");

        // IC-DRR 统一百分点² 口径
        assertClose(resp.getIcDrr().getFBefore(), bd("21.41"), bd("0.05"));
        assertClose(resp.getIcDrr().getFAfter(), bd("6.97"), bd("0.05"));
        assertClose(resp.getIcDrr().getRatioPct(), bd("67.4"), bd("0.3"));

        // 阶跃点 E* ≈ 493（Δm 恰好达到 100）
        assertClose(resp.getZohStep().getEHighAtStep(), bd("493"), bd("1.0"));
        assertClose(resp.getZohStep().getDeltaMAtStep(), bd("100"), bd("0.5"));
        // 阶跃后总投入 = 618 + 493 + 100 + 146.08 ≈ 1357.08
        assertClose(resp.getZohStep().getTotalAfterStep(), bd("1357.08"), bd("1.5"));

        // α 探针表：39% 未触发、45% 触发（docx 的 20/30/35/39 场景）
        Optional<AlphaRow> probe39 = resp.getAlphaTable().stream()
                .filter(r -> r.getAlpha().compareTo(bd("0.39")) == 0).findFirst();
        assertThat(probe39).isPresent();
        assertThat(probe39.get().getZohTriggered()).isFalse();
        Optional<AlphaRow> probe45 = resp.getAlphaTable().stream()
                .filter(r -> r.getAlpha().compareTo(bd("0.45")) == 0).findFirst();
        assertThat(probe45).isPresent();
        assertThat(probe45.get().getZohTriggered()).isTrue();
    }

    @Test
    @DisplayName("2a-S07 · α 迭代压缩：8/31 数据 + M_max=1200 → 8 轮后满足约束")
    void quarterly_autoCompression() {
        QuarterlyCalculateResponse resp = service.solveQuarterly(augThirtyOneInput());

        assertThat(resp.getAlphaTable()).hasSize(8);
        assertThat(resp.getAlphaTable().get(0).getOverLimit()).isNotNull();
        assertThat(resp.getAlphaTable().get(7).getOverLimit()).isNull();
        assertThat(resp.getAlphaIterations()).isEqualTo(7);
        // 最终：E=50.33、Δm≈193.52（取整 194）、总投入≈1196.33 ≤ 1200（执行口径）
        assertClose(resp.getChosen().getEHigh(), bd("50.33"), bd("0.05"));
        assertClose(resp.getChosen().getTotalInvestment(), bd("1196.33"), bd("0.5"));
        assertThat(resp.getChosen().getDeltaMActual()).isEqualByComparingTo("194");
        assertThat(resp.getChosen().getZohTriggered()).isTrue();
    }

    @Test
    @DisplayName("2a-S08 · 现行阈值口径：6/30 数据 Δm=89.34<100 → ZOH 不触发（历史执行早于规则正式化）")
    void quarterly_junThirty_currentThreshold() {
        QuarterlyCalculateResponse resp = service.solveQuarterly(junThirtyInput());

        assertThat(resp.getChosen().getZohTriggered()).isFalse();
        // 首轮 α=20% → E=216；Δm(216) = 60.54 + 0.1333×216 ≈ 89.34（< 100 不触发）
        assertClose(resp.getChosen().getDeltaMRaw(), bd("89.34"), bd("0.3"));
        // 不触发：总投入 = 637 + 84.93 + 127.40 + 216 = 1065.33 ≤ M_max=1080
        assertThat(resp.getAlphaTable()).hasSize(1);
        assertClose(resp.getChosen().getTotalInvestment(), bd("1065.33"), bd("0.1"));
        // 校正前六大类与高波内部结构校验
        assertThat(resp.getPreSix()).hasSize(6);
        assertThat(resp.getHighVolPre()).hasSize(4);
        assertClose(resp.getVHighVol(), bd("5268.54"), bd("0.01"));
    }

    // ================= helpers =================

    private static QuarterlyCalculateResponse.AssetRow findDelta(QuarterlyCalculateResponse resp, String cat) {
        return byCategory(resp.getLqr().getDeltas()).get(cat);
    }

    private static Map<String, AssetRow> byCategory(List<AssetRow> rows) {
        Map<String, AssetRow> map = new LinkedHashMap<>();
        for (AssetRow row : rows) {
            map.put(row.getCategory(), row);
        }
        return map;
    }

    /** 9/30 输入：M_max=1000 人为上限，α 探针基准 = 默认上限 1260 */
    private static CorrectionSolveService.QuarterlyInput nineThirtyInput(String mode) {
        CorrectionSolveService.QuarterlyInput in = new CorrectionSolveService.QuarterlyInput();
        in.vCurr = bd("9525.87");
        in.categories = sixMap("988.27", "1486.37", "2207.71", "2239.95", "2152.13", "451.44");
        in.surplus = bd("700");
        in.mMax = bd("1000");
        in.mMaxExplicit = true;
        in.uHigh = bd("618");
        in.purchaseThreshold = bd("100");
        in.targetRatios = targetRatios();
        in.mode = mode;
        in.probeBaseMmax = bd("1260");
        return in;
    }

    /** 8/31 输入 + M_max=1200（验证 α 压缩迭代结构） */
    private static CorrectionSolveService.QuarterlyInput augThirtyOneInput() {
        CorrectionSolveService.QuarterlyInput in = new CorrectionSolveService.QuarterlyInput();
        in.vCurr = bd("8713.89");
        in.categories = sixMap("797.38", "1194.11", "2208.28", "2143.89", "1936.47", "433.76");
        in.surplus = bd("666.67"); // 1.8 × 666.67 ≈ 1200
        in.mMax = bd("1200");
        in.mMaxExplicit = true;
        in.uHigh = bd("659");
        in.purchaseThreshold = bd("100");
        in.targetRatios = targetRatios();
        in.mode = "auto";
        in.alphaInit = bd("0.20");
        in.alphaDecay = bd("0.8");
        return in;
    }

    /** 6/30 输入：602 结余 → 默认 M_max=1080；低波份额 84.93/127.40（§3.6） */
    private static CorrectionSolveService.QuarterlyInput junThirtyInput() {
        CorrectionSolveService.QuarterlyInput in = new CorrectionSolveService.QuarterlyInput();
        in.vCurr = bd("6866.65");
        in.categories = sixMap("641.94", "956.17", "1510.25", "1842.41", "1563.15", "352.73");
        in.surplus = bd("600");
        in.mMax = bd("1080"); // 1.8 × 600
        in.uHigh = bd("637");
        in.uMonetaryDca = bd("84.93");
        in.uBondDca = bd("127.40");
        in.purchaseThreshold = bd("100");
        in.targetRatios = targetRatios();
        in.mode = "auto";
        in.alphaInit = bd("0.20");
        in.alphaDecay = bd("0.8");
        return in;
    }

    private static Map<String, BigDecimal> sixMap(String m, String b, String g, String a, String o, String h) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("货币类", bd(m));
        map.put("固收类", bd(b));
        map.put("商品类", bd(g));
        map.put("A股权益类", bd(a));
        map.put("海外权益类", bd(o));
        map.put("港股大中华类", bd(h));
        return map;
    }

    private static Map<String, BigDecimal> targetRatios() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("货币类", bd("10"));
        map.put("固收类", bd("15"));
        map.put("商品类", bd("25"));
        map.put("A股权益类", bd("25"));
        map.put("海外权益类", bd("20"));
        map.put("港股大中华类", bd("5"));
        return map;
    }

    private static BigDecimal frac(String pct) {
        return bd(pct).divide(new BigDecimal("100"), 10, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static void assertClose(BigDecimal actual, BigDecimal expected, BigDecimal tolerance) {
        assertThat(actual).as("expect %s ± %s but was %s", expected.toPlainString(),
                        tolerance.toPlainString(), actual == null ? "null" : actual.toPlainString())
                .isNotNull();
        assertThat(actual.subtract(expected).abs().compareTo(tolerance) <= 0)
                .as("|%s - %s| ≤ %s", actual.toPlainString(), expected.toPlainString(), tolerance.toPlainString())
                .isTrue();
    }
}