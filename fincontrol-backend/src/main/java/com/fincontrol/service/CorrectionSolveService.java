package com.fincontrol.service;

import com.fincontrol.common.BusinessException;
import com.fincontrol.common.ErrorCode;
import com.fincontrol.dto.correction.MonthlyCalculateRequest;
import com.fincontrol.dto.correction.MonthlyCalculateResponse;
import com.fincontrol.dto.correction.MonthlyRecalculateRequest;
import com.fincontrol.dto.correction.MonthlyRecalculateResponse;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.AlphaRow;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.AssetRow;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.Chosen;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.IcDrr;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.LqrSolve;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.Params;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.PlanItem;
import com.fincontrol.dto.correction.QuarterlyCalculateResponse.ZohStep;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2a 校正求解服务（纯计算，不访问 DB）。
 *
 * <p>算法来源（mcf 微观控制金融学静态站）：
 * <ul>
 *   <li>低波前馈校正：二元一次方程组精确解（§2.5）；Δm/Δb 对 E_high 线性，
 *       斜率 p_m/(1-p_m-p_b) 与 p_b/(1-p_m-p_b)。</li>
 *   <li>高波内部归一化 LQR：KKT 条件求解（§2.10），内点解不成立时 active set
 *       边界解（锚定资产 Δ=0），取整后计算 IC-DRR（§2.13）。</li>
 *   <li>总约束迭代：α ← α × decay 直到总投入 ≤ M_max（6/30 七轮压缩）；
 *       或人为 M_max 倒推 + ZOH 阶跃点分析（9/30）。</li>
 * </ul>
 *
 * <p>公式已用 4/30、6/30、8/31、9/30 四组真实校正数据反推验证（见单测 oracle）。
 */
@Service
public class CorrectionSolveService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;

    /** 高波组合（canonical 顺序） */
    public static final List<String> HIGH_VOL_CATEGORIES =
            List.of("商品类", "A股权益类", "海外权益类", "港股大中华类");

    private static final BigDecimal EPS = new BigDecimal("0.000001");

    // ========================================================================
    // 低波：二元一次方程组精确解（mcf §2.5）
    // ========================================================================

    /**
     * 低波前馈校正精确解。
     *
     * <p>W0 = V + U_high + U_m,dca + U_b,dca + E_high（分母中与 Δ 无关的部分）；
     * 目标：(M + U_m,dca + Δm)/W = p_m，(B + U_b,dca + Δb)/W = p_b，W = W0 + Δm + Δb。
     */
    public LowVolSolution solveLowVol(BigDecimal v, BigDecimal m, BigDecimal b,
                                      BigDecimal uHigh, BigDecimal uM, BigDecimal uB,
                                      BigDecimal eHigh,
                                      BigDecimal pMFrac, BigDecimal pBFrac) {
        BigDecimal det = ONE.subtract(pMFrac).subtract(pBFrac);
        if (det.compareTo(EPS) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_CORRECTION_PARAM,
                    "目标比例无效：1 - p_m - p_b ≤ 0");
        }
        BigDecimal w0 = nz(v).add(nz(uHigh)).add(nz(uM)).add(nz(uB)).add(nz(eHigh));
        BigDecimal a = nz(m).add(nz(uM)).subtract(pMFrac.multiply(w0));
        BigDecimal c = nz(b).add(nz(uB)).subtract(pBFrac.multiply(w0));
        // x = (−A(1−p_b) − p_m·C)/det ； y = (−(1−p_m)·C − p_b·A)/det
        BigDecimal x = a.negate().multiply(ONE.subtract(pBFrac))
                .subtract(pMFrac.multiply(c)).divide(det, 6, RoundingMode.HALF_UP);
        BigDecimal y = ONE.subtract(pMFrac).multiply(c).negate()
                .subtract(pBFrac.multiply(a)).divide(det, 6, RoundingMode.HALF_UP);
        return new LowVolSolution(x, y, det);
    }

    /** 低波解：理论值 + det（斜率由调用方按 p/det 计算）。 */
    public static class LowVolSolution {
        public final BigDecimal deltaM;
        public final BigDecimal deltaB;
        public final BigDecimal det;

        LowVolSolution(BigDecimal deltaM, BigDecimal deltaB, BigDecimal det) {
            this.deltaM = deltaM;
            this.deltaB = deltaB;
            this.det = det;
        }
    }

    /**
     * 月度台计算（契约 §5.2 + 2a 扩展字段）。
     */
    public MonthlyCalculateResponse solveMonthly(MonthlyCalculateRequest req) {
        BigDecimal pM = frac(req.getPMonetary(), new BigDecimal("10"));
        BigDecimal pB = frac(req.getPBond(), new BigDecimal("15"));
        BigDecimal uM = nz(req.getUMonetaryDca());
        BigDecimal uB = nz(req.getUBondDca());
        BigDecimal eHigh = nz(req.getEHigh());
        BigDecimal uHigh = nz(req.getUHigh());
        BigDecimal v = nz(req.getVCurr());

        LowVolSolution sol = solveLowVol(v, req.getVMonetary(), req.getVBond(),
                uHigh, uM, uB, eHigh, pM, pB);

        BigDecimal slopeM = pM.divide(sol.det, 10, RoundingMode.HALF_UP);
        BigDecimal slopeB = pB.divide(sol.det, 10, RoundingMode.HALF_UP);

        // 理论反推低波定投份额（契约公式：U_high × p/(1-p_m-p_b)）
        BigDecimal uMTheory = uHigh.multiply(slopeM).setScale(2, RoundingMode.HALF_UP);
        BigDecimal uBTheory = uHigh.multiply(slopeB).setScale(2, RoundingMode.HALF_UP);

        BigDecimal deltaM = sol.deltaM.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deltaB = sol.deltaB.setScale(2, RoundingMode.HALF_UP);

        BigDecimal threshold = nz(req.getPurchaseThreshold()).compareTo(BigDecimal.ZERO) > 0
                ? req.getPurchaseThreshold() : new BigDecimal("100");
        boolean zohTriggered = deltaM.compareTo(threshold) >= 0;

        // 取整建议：纯低波 ZOH 放宽到整十（mcf §3.6 6.7 取整规则）
        BigDecimal mSuggested = roundUpTo(deltaM, new BigDecimal("10"));
        BigDecimal bSuggested = roundUpTo(deltaB, new BigDecimal("10"));

        // 总投入按理论值口径（契约 §5.2 示例口径）；实际执行后由 recalculate 重算
        BigDecimal total = totalOf(uHigh, uM, uB,
                zohTriggered ? deltaM : BigDecimal.ZERO,
                zohTriggered ? deltaB : BigDecimal.ZERO, eHigh);

        List<MonthlyCalculateResponse.WarningItem> warnings = new ArrayList<>();
        if (!zohTriggered) {
            warnings.add(MonthlyCalculateResponse.WarningItem.builder()
                    .type("zoh_not_triggered")
                    .message("Δm = " + deltaM.toPlainString() + " 未达阈值 " + threshold.toPlainString()
                            + "，本次不触发低波补仓（实际补仓为 0）")
                    .build());
        }
        if (req.getBudgetLimit() != null && total.compareTo(req.getBudgetLimit()) > 0) {
            warnings.add(MonthlyCalculateResponse.WarningItem.builder()
                    .type("exceed_budget_limit")
                    .message("理论总投入 " + total.toPlainString() + " 超出 budgetLimit "
                            + req.getBudgetLimit().toPlainString() + "，建议削减高波校正预算")
                    .build());
        }

        BigDecimal[] dev = deviations(v, req.getVMonetary(), req.getVBond(),
                uHigh, uM, uB, eHigh, pM, pB,
                zohTriggered ? mSuggested : BigDecimal.ZERO,
                zohTriggered ? bSuggested : BigDecimal.ZERO);

        return MonthlyCalculateResponse.builder()
                .deltaMTheory(deltaM)
                .deltaBTheory(deltaB)
                .uMonetaryDca(uM)
                .uBondDca(uB)
                .uMonetaryDcaTheory(uMTheory)
                .uBondDcaTheory(uBTheory)
                .totalInvestment(total)
                .zohTriggered(zohTriggered)
                .eHigh(eHigh)
                .roundingSuggestion(MonthlyCalculateResponse.RoundingSuggestion.builder()
                        .deltaMSuggested(zohTriggered ? mSuggested : zero2())
                        .deltaBSuggested(zohTriggered ? bSuggested : zero2())
                        .roundingStrategy("round_up_10")
                        .deviationMSuggested(dev[0])
                        .deviationBSuggested(dev[1])
                        .build())
                .warnings(warnings)
                .build();
    }

    /**
     * 取整弹窗实时重算（契约 §5.3）。
     */
    public MonthlyRecalculateResponse recalculateMonthly(MonthlyRecalculateRequest req) {
        BigDecimal pM = frac(req.getPMonetary(), new BigDecimal("10"));
        BigDecimal pB = frac(req.getPBond(), new BigDecimal("15"));
        BigDecimal uM = nz(req.getUMonetaryDcaActual());
        BigDecimal uB = nz(req.getUBondDcaActual());
        BigDecimal eHigh = nz(req.getEHigh());
        BigDecimal uHigh = nz(req.getUHigh());
        BigDecimal deltaM = nz(req.getDeltaMActual());
        BigDecimal deltaB = nz(req.getDeltaBActual());
        BigDecimal v = nz(req.getVCurr());

        BigDecimal[] dev = deviations(v, req.getVMonetary(), req.getVBond(),
                uHigh, uM, uB, eHigh, pM, pB, deltaM, deltaB);

        BigDecimal newV = v.add(uHigh).add(uM).add(uB).add(eHigh).add(deltaM).add(deltaB);
        BigDecimal ratioM = pct(nz(req.getVMonetary()).add(uM).add(deltaM), newV);
        BigDecimal ratioB = pct(nz(req.getVBond()).add(uB).add(deltaB), newV);

        BigDecimal total = totalOf(uHigh, uM, uB, deltaM, deltaB, eHigh);
        boolean over = req.getBudgetLimit() != null && total.compareTo(req.getBudgetLimit()) > 0;

        Map<String, BigDecimal> ratios = new LinkedHashMap<>();
        ratios.put("货币类", ratioM);
        ratios.put("固收类", ratioB);
        Map<String, BigDecimal> deviations = new LinkedHashMap<>();
        deviations.put("货币类", dev[0]);
        deviations.put("固收类", dev[1]);

        return MonthlyRecalculateResponse.builder()
                .newRatios(ratios)
                .deviations(deviations)
                .totalInvestmentActual(total)
                .overBudgetLimit(over)
                .build();
    }

    // ========================================================================
    // 季度：LQR-ZOH 联合校正（mcf §2.10 五步算法）
    // ========================================================================

    /**
     * 季度 LQR-ZOH 联合校正求解（入参缺省值由上层 CorrectionService 补齐）。
     */
    public QuarterlyCalculateResponse solveQuarterly(QuarterlyInput in) {
        BigDecimal pM = frac(in.targetRatios.get("货币类"), new BigDecimal("10"));
        BigDecimal pB = frac(in.targetRatios.get("固收类"), new BigDecimal("15"));
        BigDecimal det = ONE.subtract(pM).subtract(pB);
        if (det.compareTo(EPS) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_CORRECTION_PARAM,
                    "目标比例无效：1 - p_m - p_b ≤ 0");
        }

        BigDecimal v = nz(in.vCurr);
        BigDecimal m = nz(in.categories.get("货币类"));
        BigDecimal b = nz(in.categories.get("固收类"));
        BigDecimal uHigh = nz(in.uHigh);
        BigDecimal uM = nz(in.uMonetaryDca);
        BigDecimal uB = nz(in.uBondDca);
        BigDecimal threshold = nz(in.purchaseThreshold).compareTo(BigDecimal.ZERO) > 0
                ? in.purchaseThreshold : new BigDecimal("100");
        BigDecimal mMax = nz(in.mMax);
        if (mMax.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_CORRECTION_PARAM,
                    "M_max 必须 > 0（请提供 surplus 或 mMax）");
        }
        String mode = in.mode == null || in.mode.isBlank() ? "auto" : in.mode;

        // ---------- 校正前状态 ----------
        List<AssetRow> preSix = new ArrayList<>();
        for (String cat : SnapshotQueryService.CANONICAL_SIX_CATEGORIES) {
            BigDecimal amt = nz(in.categories.get(cat));
            BigDecimal ratio = pct(amt, v);
            BigDecimal target = nz(in.targetRatios.get(cat));
            preSix.add(AssetRow.builder()
                    .category(cat).amount(d2(amt))
                    .ratioActual(ratio)
                    .ratioTarget(d2(target))
                    .deviation(d2(ratio.subtract(target)))
                    .build());
        }

        BigDecimal vHigh = BigDecimal.ZERO;
        BigDecimal highTargetSum = BigDecimal.ZERO;
        for (String cat : HIGH_VOL_CATEGORIES) {
            vHigh = vHigh.add(nz(in.categories.get(cat)));
            highTargetSum = highTargetSum.add(nz(in.targetRatios.get(cat)));
        }
        // 高波内部归一化目标 q_i = p_i / Σ_high p_i
        Map<String, BigDecimal> q = new LinkedHashMap<>();
        for (String cat : HIGH_VOL_CATEGORIES) {
            BigDecimal pi = nz(in.targetRatios.get(cat));
            q.put(cat, highTargetSum.compareTo(BigDecimal.ZERO) > 0
                    ? pi.divide(highTargetSum, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }

        List<AssetRow> highVolPre = new ArrayList<>();
        String anchor = null;
        BigDecimal anchorDev = null;
        List<String> betaTriggered = new ArrayList<>();
        for (String cat : HIGH_VOL_CATEGORIES) {
            BigDecimal amt = nz(in.categories.get(cat));
            BigDecimal ratio = pct(amt, vHigh);
            BigDecimal target = q.get(cat).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            BigDecimal devPp = ratio.subtract(target);
            highVolPre.add(AssetRow.builder()
                    .category(cat).amount(d2(amt))
                    .ratioActual(d2(ratio)).ratioTarget(d2(target))
                    .deviation(d2(devPp))
                    .build());
            if (anchorDev == null || devPp.compareTo(anchorDev) > 0) {
                anchorDev = devPp;
                anchor = cat;
            }
            // Beta 极端超配检查：r_i = actual/target - 1 ≥ 0.20
            if (target.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal r = ratio.divide(target, 10, RoundingMode.HALF_UP).subtract(ONE);
                if (r.compareTo(new BigDecimal("0.20")) >= 0) {
                    betaTriggered.add(cat);
                }
            }
        }

        // ---------- 低波线性函数：Δm(E)/Δb(E) ----------
        LowVolSolution base = solveLowVol(v, m, b, uHigh, uM, uB, BigDecimal.ZERO, pM, pB);
        BigDecimal slopeM = pM.divide(det, 10, RoundingMode.HALF_UP);
        BigDecimal slopeB = pB.divide(det, 10, RoundingMode.HALF_UP);

        BigDecimal probeBase = in.probeBaseMmax != null
                ? in.probeBaseMmax
                : (in.surplus != null ? in.surplus.multiply(new BigDecimal("1.8")) : mMax);

        List<MonthlyCalculateResponse.WarningItem> warnings = new ArrayList<>();
        for (String cat : betaTriggered) {
            warnings.add(MonthlyCalculateResponse.WarningItem.builder()
                    .type("beta_triggered")
                    .message(cat + " 相对偏差 ≥ 20%，触发 Beta 极端超配检查（系统不主动给出卖出建议）")
                    .build());
        }

        // ---------- α 迭代 / 探针 ----------
        List<AlphaRow> alphaTable = new ArrayList<>();
        BigDecimal alphaFinal;
        BigDecimal eHigh;
        Integer iterations = null;

        if ("mMaxCapped".equals(mode)) {
            BigDecimal eMax = mMax.subtract(uHigh).subtract(uM).subtract(uB);
            if (eMax.signum() < 0) eMax = BigDecimal.ZERO;
            // ZOH 阶跃点：Δm(E*) = threshold（Δm 对 E 线性，解析求解）
            BigDecimal eStar = slopeM.signum() > 0
                    ? threshold.subtract(base.deltaM).divide(slopeM, 6, RoundingMode.HALF_UP)
                    : null;
            BigDecimal eChosen = eMax;
            if (eStar != null && eStar.signum() > 0 && eMax.compareTo(eStar) >= 0) {
                BigDecimal dmAtEmax = base.deltaM.add(slopeM.multiply(eMax));
                BigDecimal dbAtEmax = base.deltaB.add(slopeB.multiply(eMax));
                BigDecimal totalIfTriggered = totalOf(uHigh, uM, uB, dmAtEmax, dbAtEmax, eMax);
                if (totalIfTriggered.compareTo(mMax) > 0) {
                    // 越过阶跃点后总投入超限 → 回退至阶跃点之下（9/30 场景）
                    eChosen = eStar.subtract(new BigDecimal("0.01"));
                    warnings.add(MonthlyCalculateResponse.WarningItem.builder()
                            .type("zoh_step_backoff")
                            .message("E_high 达到 " + d2(eStar).toPlainString() + " 时 ZOH 触发，总投入跃升至 "
                                    + d2(totalIfTriggered).toPlainString() + " 超出 M_max，已回退至阶跃点之下")
                            .build());
                }
            }
            eHigh = eChosen;
            alphaFinal = probeBase.signum() > 0
                    ? eHigh.divide(probeBase, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            // α 探针表（默认上限下的分布式 α 表，9/30 场景）
            List<BigDecimal> probes = in.alphaProbes != null && !in.alphaProbes.isEmpty()
                    ? in.alphaProbes
                    : List.of(new BigDecimal("20"), new BigDecimal("30"),
                            new BigDecimal("35"), new BigDecimal("39"),
                            new BigDecimal("45"), new BigDecimal("50"));
            for (BigDecimal probePct : probes) {
                BigDecimal pa = frac(probePct, BigDecimal.ZERO);
                BigDecimal e = probeBase.multiply(pa).setScale(2, RoundingMode.HALF_UP);
                BigDecimal dm = base.deltaM.add(slopeM.multiply(e));
                BigDecimal db = base.deltaB.add(slopeB.multiply(e));
                boolean zoh = dm.compareTo(threshold) >= 0;
                BigDecimal total = totalOf(uHigh, uM, uB,
                        zoh ? dm : BigDecimal.ZERO, zoh ? db : BigDecimal.ZERO, e);
                BigDecimal over = total.compareTo(mMax) > 0 ? total.subtract(mMax) : null;
                List<String> notes = new ArrayList<>();
                if (e.compareTo(eMax) > 0) notes.add("E_high 超出 M_max 可用空间");
                if (zoh) notes.add("ZOH 触发");
                alphaTable.add(AlphaRow.builder()
                        .alpha(pa).eHigh(d2(e)).deltaM(d2(dm)).deltaB(d2(db))
                        .zohTriggered(zoh).totalInvestment(d2(total))
                        .overLimit(d2(over))
                        .note(notes.isEmpty() ? null : String.join("；", notes))
                        .build());
            }
        } else {
            // auto 模式：α 迭代压缩（6/30 场景），E = M_max × α
            BigDecimal alpha = in.alphaInit != null ? in.alphaInit : new BigDecimal("0.20");
            BigDecimal decay = in.alphaDecay != null ? in.alphaDecay : new BigDecimal("0.8");
            int maxIter = in.maxIterations != null ? in.maxIterations : 20;
            int round = 0;
            BigDecimal e;
            BigDecimal dm;
            BigDecimal db;
            boolean zoh;
            BigDecimal total;
            do {
                e = mMax.multiply(alpha);
                dm = base.deltaM.add(slopeM.multiply(e));
                db = base.deltaB.add(slopeB.multiply(e));
                zoh = dm.compareTo(threshold) >= 0;
                // 总投入按执行口径（触发时取整至整数元；与 chosen/plan 一致）
                total = totalOf(uHigh, uM, uB,
                        zoh ? roundUpTo(dm, ONE) : BigDecimal.ZERO,
                        zoh ? roundUpTo(db, ONE) : BigDecimal.ZERO, e);
                boolean over = total.compareTo(mMax) > 0;
                alphaTable.add(AlphaRow.builder()
                        .alpha(alpha.setScale(4, RoundingMode.HALF_UP))
                        .eHigh(d2(e)).deltaM(d2(dm)).deltaB(d2(db))
                        .zohTriggered(zoh).totalInvestment(d2(total))
                        .overLimit(over ? d2(total.subtract(mMax)) : null)
                        .note(over ? "超限，继续压缩 α" : "满足约束")
                        .build());
                if (!over) break;
                alpha = alpha.multiply(decay);
                round++;
            } while (round <= maxIter);
            iterations = alphaTable.size() - 1;
            alphaFinal = alpha;
            eHigh = e;
            if (total.compareTo(mMax) > 0) {
                warnings.add(MonthlyCalculateResponse.WarningItem.builder()
                        .type("exceed_budget_limit")
                        .message("α 迭代 " + iterations + " 轮后总投入仍超出 M_max，请人工调整参数")
                        .build());
            }
        }

        // ---------- 最终选择与取整 ----------
        BigDecimal deltaMRaw = base.deltaM.add(slopeM.multiply(eHigh));
        BigDecimal deltaBRaw = base.deltaB.add(slopeB.multiply(eHigh));
        boolean zohTriggered = deltaMRaw.compareTo(threshold) >= 0;
        // ZOH-LQR 联合校正：严格向上取整至整数元（mcf §3.6 6.7）
        BigDecimal deltaMActual = zohTriggered ? roundUpTo(deltaMRaw, ONE) : zero2();
        BigDecimal deltaBActual = zohTriggered ? roundUpTo(deltaBRaw, ONE) : zero2();
        BigDecimal chosenTotal = totalOf(uHigh, uM, uB, deltaMActual, deltaBActual, eHigh);
        if (!zohTriggered) {
            warnings.add(MonthlyCalculateResponse.WarningItem.builder()
                    .type("zoh_not_triggered")
                    .message("ZOH 货币补仓 " + d2(deltaMRaw).toPlainString() + " < "
                            + threshold.toPlainString() + " → 不触发，低波不补仓")
                    .build());
        }

        // ---------- ZOH 阶跃点分析（两种模式都输出） ----------
        ZohStep zohStep = null;
        if (slopeM.signum() > 0) {
            BigDecimal eStar = threshold.subtract(base.deltaM).divide(slopeM, 6, RoundingMode.HALF_UP);
            if (eStar.signum() > 0) {
                BigDecimal dmAt = base.deltaM.add(slopeM.multiply(eStar));
                BigDecimal dbAt = base.deltaB.add(slopeB.multiply(eStar));
                BigDecimal totalBefore = totalOf(uHigh, uM, uB, BigDecimal.ZERO, BigDecimal.ZERO, eStar);
                BigDecimal totalAfter = totalOf(uHigh, uM, uB, dmAt, dbAt, eStar);
                zohStep = ZohStep.builder()
                        .eHighAtStep(d2(eStar))
                        .deltaMAtStep(d2(dmAt))
                        .totalBeforeStep(d2(totalBefore))
                        .totalAfterStep(d2(totalAfter))
                        .jumpAmount(d2(totalAfter.subtract(totalBefore)))
                        .note("E_high 达到触发点后 ZOH 补仓启动，总投入阶跃上升")
                        .build();
            }
        }

        // ---------- 高波 LQR 求解 ----------
        LqrSolve lqr = solveHighVolLqr(vHigh, in.categories, q, anchor, eHigh, betaTriggered);

        // 高波校正后总值 = 校正前 + Σ 取整后补仓
        BigDecimal deltaSum = BigDecimal.ZERO;
        for (AssetRow row : lqr.getDeltas()) {
            deltaSum = deltaSum.add(nz(row.getDeltaAmount()));
        }
        BigDecimal vHighAfter = vHigh.add(deltaSum);

        // ---------- IC-DRR ----------
        IcDrr icDrr = computeIcDrr(vHigh, vHighAfter, in.categories, q, lqr.getDeltas());

        // ---------- 校正后高波内部 ----------
        List<AssetRow> highVolPost = new ArrayList<>();
        for (AssetRow d : lqr.getDeltas()) {
            BigDecimal amt = nz(d.getAmount()).add(nz(d.getDeltaAmount()));
            BigDecimal ratio = pct(amt, vHighAfter);
            BigDecimal target = q.get(d.getCategory()).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            highVolPost.add(AssetRow.builder()
                    .category(d.getCategory()).amount(d2(amt))
                    .ratioActual(d2(ratio)).ratioTarget(d2(target))
                    .deviation(d2(ratio.subtract(target)))
                    .deltaRaw(d.getDeltaRaw()).deltaAmount(d.getDeltaAmount())
                    .build());
        }

        // ---------- 执行方案 ----------
        List<PlanItem> plan = new ArrayList<>();
        plan.add(PlanItem.builder().item("当月高波定投")
                .amount(d2(uHigh)).note("转入余额宝，月内自动扣款").build());
        plan.add(PlanItem.builder().item("LQR 高波校正")
                .amount(d2(eHigh)).note(highVolPlanNote(lqr.getDeltas())).build());
        plan.add(PlanItem.builder().item("ZOH 低波校正")
                .amount(d2(deltaMActual.add(deltaBActual)))
                .note(zohTriggered
                        ? "货币 Δm=" + d2(deltaMRaw).toPlainString() + "（取整 " + deltaMActual.toPlainString()
                                + "）；固收 Δb=" + d2(deltaBRaw).toPlainString() + "（取整 " + deltaBActual.toPlainString() + "）"
                        : "Δm=" + d2(deltaMRaw).toPlainString() + " < 阈值，不触发")
                .build());
        if (uM.signum() > 0 || uB.signum() > 0) {
            plan.add(PlanItem.builder().item("低波定投份额")
                    .amount(d2(uM.add(uB))).note("月初已投入（货币 " + d2(uM).toPlainString()
                            + " / 固收 " + d2(uB).toPlainString() + "）").build());
        }
        plan.add(PlanItem.builder().item("总投入").amount(d2(chosenTotal))
                .note("M_max = " + d2(mMax).toPlainString()
                        + (chosenTotal.compareTo(mMax) <= 0 ? "（在范围内）" : "（超限）"))
                .build());

        Map<String, BigDecimal> targetRatiosOut = new LinkedHashMap<>();
        for (String cat : SnapshotQueryService.CANONICAL_SIX_CATEGORIES) {
            targetRatiosOut.put(cat, d2(nz(in.targetRatios.get(cat))));
        }

        return QuarterlyCalculateResponse.builder()
                .vCurr(d2(v)).vHighVol(d2(vHigh))
                .preSix(preSix)
                .highVolPre(highVolPre)
                .anchor(anchor)
                .targetRatios(targetRatiosOut)
                .params(Params.builder()
                        .surplus(d2(in.surplus)).mMax(d2(mMax))
                        .mMaxSource(in.mMaxSource == null || in.mMaxSource.isBlank()
                                ? (in.mMaxExplicit ? "manual" : "default") : in.mMaxSource)
                        .uHigh(d2(uHigh)).uMonetaryDca(d2(uM)).uBondDca(d2(uB))
                        .alphaInit(in.alphaInit != null ? in.alphaInit : new BigDecimal("0.20"))
                        .alphaDecay(in.alphaDecay != null ? in.alphaDecay : new BigDecimal("0.8"))
                        .purchaseThreshold(d2(threshold))
                        .mode(mode)
                        .build())
                .alphaTable(alphaTable)
                .zohStep(zohStep)
                .chosen(Chosen.builder()
                        .alphaFinal(alphaFinal == null ? null : alphaFinal.setScale(4, RoundingMode.HALF_UP))
                        .eHigh(d2(eHigh))
                        .deltaMRaw(d2(deltaMRaw)).deltaBRaw(d2(deltaBRaw))
                        .deltaMActual(deltaMActual).deltaBActual(deltaBActual)
                        .zohTriggered(zohTriggered)
                        .totalInvestment(d2(chosenTotal))
                        .roundingStrategy("round_up_1")
                        .build())
                .alphaIterations(iterations)
                .lqr(lqr)
                .icDrr(icDrr)
                .highVolPost(highVolPost)
                .plan(plan)
                .warnings(warnings)
                .build();
    }

    /** 高波内部 LQR：KKT 主动集迭代（锚定资产 Δ=0）。 */
    private LqrSolve solveHighVolLqr(BigDecimal vHigh,
                                     Map<String, BigDecimal> categories,
                                     Map<String, BigDecimal> q,
                                     String anchor,
                                     BigDecimal eHigh,
                                     List<String> betaTriggered) {
        BigDecimal d = vHigh.add(nz(eHigh));
        List<String> active = new ArrayList<>();
        for (String cat : HIGH_VOL_CATEGORIES) {
            if (!cat.equals(anchor)) active.add(cat);
        }
        Map<String, BigDecimal> deltas = new LinkedHashMap<>();
        for (String cat : HIGH_VOL_CATEGORIES) deltas.put(cat, BigDecimal.ZERO);
        StringBuilder note = new StringBuilder();
        boolean interiorOk = true;

        while (true) {
            BigDecimal sumV = BigDecimal.ZERO;
            BigDecimal sumQ = BigDecimal.ZERO;
            for (String cat : active) {
                sumV = sumV.add(nz(categories.get(cat)));
                sumQ = sumQ.add(q.get(cat));
            }
            int k = active.size();
            if (k == 0) {
                note.append("预算无法分配：全部高波资产均已超配");
                break;
            }
            // c = (E + ΣV_S − D·Σq_S) / (|S|·D)；Δ_i = (q_i + c)·D − V_i
            BigDecimal c = nz(eHigh).add(sumV).subtract(d.multiply(sumQ))
                    .divide(d.multiply(new BigDecimal(k)), 10, RoundingMode.HALF_UP);
            List<String> removed = new ArrayList<>();
            for (String cat : active) {
                BigDecimal delta = q.get(cat).add(c).multiply(d).subtract(nz(categories.get(cat)));
                if (delta.signum() < 0) removed.add(cat);
                deltas.put(cat, delta);
            }
            if (removed.isEmpty()) break;
            interiorOk = false;
            note.append("内点解不成立（").append(String.join("、", removed))
                    .append(" 补仓为负）→ 边界解：置 0 后重解；");
            active.removeAll(removed);
            for (String cat : removed) deltas.put(cat, BigDecimal.ZERO);
        }
        if (interiorOk) {
            note.append("KKT 内点解成立：各类偏差等化后分配预算");
        }

        List<AssetRow> deltaRows = new ArrayList<>();
        for (String cat : HIGH_VOL_CATEGORIES) {
            BigDecimal raw = deltas.get(cat);
            BigDecimal rounded;
            if (cat.equals(anchor)) {
                rounded = BigDecimal.ZERO;
            } else {
                rounded = raw.setScale(0, RoundingMode.HALF_UP);
                if (raw.signum() > 0 && rounded.signum() == 0) rounded = ONE; // 正向极小值保底 1 元
            }
            deltaRows.add(AssetRow.builder()
                    .category(cat)
                    .amount(d2(nz(categories.get(cat))))
                    .deltaRaw(d2(raw))
                    .deltaAmount(rounded.setScale(2, RoundingMode.HALF_UP))
                    .note(cat.equals(anchor) ? "锚定资产 Δ=0" : null)
                    .build());
        }
        return LqrSolve.builder()
                .deltas(deltaRows)
                .kktNote(note.toString())
                .betaTriggered(betaTriggered == null ? new ArrayList<>() : betaTriggered)
                .build();
    }

    /** IC-DRR = (f_before − f_after)/f_before；f 为高波内部偏差平方和（百分点²）。 */
    private IcDrr computeIcDrr(BigDecimal vHighBefore, BigDecimal vHighAfter,
                               Map<String, BigDecimal> categories,
                               Map<String, BigDecimal> q,
                               List<AssetRow> deltas) {
        BigDecimal fBefore = BigDecimal.ZERO;
        BigDecimal fAfter = BigDecimal.ZERO;
        for (AssetRow row : deltas) {
            String cat = row.getCategory();
            BigDecimal targetPp = q.get(cat).multiply(HUNDRED);
            BigDecimal ratioBefore = pct(nz(categories.get(cat)), vHighBefore);
            BigDecimal amtAfter = nz(categories.get(cat)).add(nz(row.getDeltaAmount()));
            BigDecimal ratioAfter = pct(amtAfter, vHighAfter);
            fBefore = fBefore.add(ratioBefore.subtract(targetPp).pow(2));
            fAfter = fAfter.add(ratioAfter.subtract(targetPp).pow(2));
        }
        fBefore = fBefore.setScale(2, RoundingMode.HALF_UP);
        fAfter = fAfter.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ratio = fBefore.signum() > 0
                ? fBefore.subtract(fAfter).divide(fBefore, 6, RoundingMode.HALF_UP)
                        .multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return IcDrr.builder().fBefore(fBefore).fAfter(fAfter).ratioPct(ratio).build();
    }

    private String highVolPlanNote(List<AssetRow> deltas) {
        List<String> parts = new ArrayList<>();
        for (AssetRow row : deltas) {
            if (nz(row.getDeltaAmount()).signum() > 0) {
                parts.add(row.getCategory() + " " + row.getDeltaAmount().stripTrailingZeros().toPlainString());
            }
        }
        return parts.isEmpty() ? "无可分配预算" : String.join(" / ", parts);
    }

    // ========================================================================
    // 入参容器与工具
    // ========================================================================

    /** 季度求解入参（上层已补齐缺省值）。 */
    public static class QuarterlyInput {
        public BigDecimal vCurr;
        public Map<String, BigDecimal> categories = new LinkedHashMap<>();
        public BigDecimal surplus;
        public BigDecimal mMax;
        public boolean mMaxExplicit;
        public String mMaxSource;
        public BigDecimal uHigh;
        public BigDecimal uMonetaryDca;
        public BigDecimal uBondDca;
        public BigDecimal alphaInit;
        public BigDecimal alphaDecay;
        public BigDecimal purchaseThreshold;
        public Map<String, BigDecimal> targetRatios = new LinkedHashMap<>();
        public String mode;
        public List<BigDecimal> alphaProbes;
        public BigDecimal probeBaseMmax;
        public Integer maxIterations;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal zero2() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal d2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal frac(BigDecimal pct, BigDecimal dflt) {
        BigDecimal p = pct != null ? pct : dflt;
        return p.divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) return zero2();
        return part.divide(whole, 10, RoundingMode.HALF_UP).multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalOf(BigDecimal uHigh, BigDecimal uM, BigDecimal uB,
                                      BigDecimal deltaM, BigDecimal deltaB, BigDecimal eHigh) {
        return nz(uHigh).add(nz(uM)).add(nz(uB)).add(nz(deltaM)).add(nz(deltaB)).add(nz(eHigh))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal roundUpTo(BigDecimal v, BigDecimal step) {
        if (v == null || v.signum() <= 0) return zero2();
        return v.divide(step, 0, RoundingMode.CEILING).multiply(step).setScale(2, RoundingMode.HALF_UP);
    }

    /** 取整后的新比例与偏差（百分点数组：[devM, devB]）。 */
    private BigDecimal[] deviations(BigDecimal v, BigDecimal m, BigDecimal b,
                                    BigDecimal uHigh, BigDecimal uM, BigDecimal uB,
                                    BigDecimal eHigh, BigDecimal pM, BigDecimal pB,
                                    BigDecimal deltaM, BigDecimal deltaB) {
        BigDecimal newV = nz(v).add(nz(uHigh)).add(nz(uM)).add(nz(uB)).add(nz(eHigh))
                .add(nz(deltaM)).add(nz(deltaB));
        BigDecimal ratioM = pct(nz(m).add(nz(uM)).add(nz(deltaM)), newV);
        BigDecimal ratioB = pct(nz(b).add(nz(uB)).add(nz(deltaB)), newV);
        return new BigDecimal[]{
                ratioM.subtract(pM.multiply(HUNDRED)),
                ratioB.subtract(pB.multiply(HUNDRED)),
        };
    }
}