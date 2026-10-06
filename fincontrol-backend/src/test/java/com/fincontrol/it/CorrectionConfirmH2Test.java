package com.fincontrol.it;

import com.fincontrol.AbstractIT;
import com.fincontrol.common.BusinessException;
import com.fincontrol.dto.correction.CorrectionConfirmResponse;
import com.fincontrol.dto.correction.MonthlyConfirmRequest;
import com.fincontrol.dto.correction.QuarterlyConfirmRequest;
import com.fincontrol.entity.OperationLog;
import com.fincontrol.mapper.CorrectionAssetDetailMapper;
import com.fincontrol.mapper.CorrectionIterationMapper;
import com.fincontrol.mapper.CorrectionParamMapper;
import com.fincontrol.mapper.OperationLogMapper;
import com.fincontrol.service.CorrectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 2a 校正页 H2 集成测试：确认入库（4 张表）+ 记录列表 / 详情回放读取。
 */
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CorrectionConfirmH2Test extends AbstractIT {

    private static final long TEST_USER_ID = 19070L;

    @Autowired private CorrectionService correctionService;
    @Autowired private OperationLogMapper operationLogMapper;
    @Autowired private CorrectionIterationMapper iterationMapper;
    @Autowired private CorrectionAssetDetailMapper assetDetailMapper;
    @Autowired private CorrectionParamMapper paramMapper;

    @Test
    @Transactional
    @DisplayName("2a-H01 · 季度联合校正确认：单事务写 4 张表 + 列表/详情可回放")
    void confirmQuarterly_writesFourTables_andReplayable() {
        QuarterlyConfirmRequest req = new QuarterlyConfirmRequest();
        req.setSnapshotDate(LocalDate.now().toString());
        req.setVCurr(new BigDecimal("9525.87"));
        req.setVMonetary(new BigDecimal("988.27"));
        req.setVBond(new BigDecimal("1486.37"));
        req.setVHighVol(new BigDecimal("7051.23"));
        req.setUHigh(new BigDecimal("618"));
        req.setDeltaMTheory(new BigDecimal("85.22"));
        req.setDeltaBTheory(new BigDecimal("123.88"));
        req.setDeltaMActual(BigDecimal.ZERO);
        req.setDeltaBActual(BigDecimal.ZERO);
        req.setRoundingStrategy("round_up_1");
        req.setBudgetLimitUsed(new BigDecimal("1000"));
        req.setTotalInvestment(new BigDecimal("1000"));
        req.setNotes(List.of("ZOH 货币补仓 85.22 < 100 → 不触发", "E_high=382（人为上限倒推）"));
        req.setCorrectionMode("lqr_zoh");

        QuarterlyConfirmRequest.IterationItem it1 = new QuarterlyConfirmRequest.IterationItem();
        it1.setSortOrder(1);
        it1.setAlpha(new BigDecimal("0.30"));
        it1.setEHigh(new BigDecimal("378"));
        it1.setDeltaM(new BigDecimal("84.69"));
        it1.setDeltaB(new BigDecimal("123.08"));
        it1.setZohTriggered(false);
        it1.setTotalInvestment(new BigDecimal("996"));
        QuarterlyConfirmRequest.IterationItem it2 = new QuarterlyConfirmRequest.IterationItem();
        it2.setSortOrder(2);
        it2.setAlpha(new BigDecimal("0.39"));
        it2.setEHigh(new BigDecimal("491.4"));
        it2.setDeltaM(new BigDecimal("99.75"));
        it2.setDeltaB(new BigDecimal("145.68"));
        it2.setZohTriggered(false);
        it2.setTotalInvestment(new BigDecimal("1109.4"));
        req.setIterations(List.of(it1, it2));

        QuarterlyConfirmRequest.AssetItem a1 = new QuarterlyConfirmRequest.AssetItem();
        a1.setPhase("pre_high");
        a1.setCategory("商品类");
        a1.setAmount(new BigDecimal("2207.71"));
        a1.setRatioActual(new BigDecimal("31.31"));
        a1.setRatioTarget(new BigDecimal("33.33"));
        a1.setDeviation(new BigDecimal("-2.02"));
        QuarterlyConfirmRequest.AssetItem a2 = new QuarterlyConfirmRequest.AssetItem();
        a2.setPhase("post_high");
        a2.setCategory("商品类");
        a2.setAmount(new BigDecimal("2414.71"));
        a2.setRatioActual(new BigDecimal("32.49"));
        a2.setRatioTarget(new BigDecimal("33.33"));
        a2.setDeviation(new BigDecimal("-0.84"));
        a2.setDeltaRaw(new BigDecimal("207.12"));
        a2.setDeltaAmount(new BigDecimal("207"));
        req.setAssets(List.of(a1, a2));

        QuarterlyConfirmRequest.ParamItem p1 = new QuarterlyConfirmRequest.ParamItem();
        p1.setKey("icDrrPct");
        p1.setNumValue(new BigDecimal("67.4"));
        QuarterlyConfirmRequest.ParamItem p2 = new QuarterlyConfirmRequest.ParamItem();
        p2.setKey("anchor");
        p2.setTextValue("海外权益类");
        req.setParams(List.of(p1, p2));

        CorrectionConfirmResponse resp = correctionService.confirmQuarterly(TEST_USER_ID, req);

        assertThat(resp.getOperationLogId()).isNotNull();
        assertThat(resp.getDetailRows()).isEqualTo(6);
        assertThat(resp.getWrittenToOperationLog()).isTrue();

        OperationLog op = operationLogMapper.selectById(resp.getOperationLogId());
        assertThat(op).isNotNull();
        assertThat(op.getCorrectionMode()).isEqualTo("lqr_zoh");
        assertThat(op.getOperationType()).isEqualTo("quarterly_correction");
        assertThat(op.getTotalInvestment()).isEqualByComparingTo("1000");
        assertThat(op.getSnapshotDate()).isEqualTo(LocalDate.now());
        assertThat(op.getSource()).isEqualTo("correction_page");
        assertThat(op.getWarnings()).contains("不触发");

        assertThat(iterationMapper.selectByOperationLog(op.getId())).hasSize(2);
        assertThat(assetDetailMapper.selectByOperationLog(op.getId())).hasSize(2);
        assertThat(paramMapper.selectByOperationLog(op.getId())).hasSize(2);

        // 列表：hasDetail + icDrrPct 摘要
        Map<String, Object> list = correctionService.listOperations(TEST_USER_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) list.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("hasDetail")).isEqualTo(true);
        assertThat(items.get(0).get("icDrrPct")).isEqualTo(new BigDecimal("67.40"));
        assertThat(items.get(0).get("anchor")).isEqualTo("海外权益类");

        // 详情回放
        Map<String, Object> detail = correctionService.getOperationDetail(TEST_USER_ID, op.getId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> iterations = (List<Map<String, Object>>) detail.get("iterations");
        assertThat(iterations).hasSize(2);
        assertThat((BigDecimal) iterations.get(0).get("alpha")).isEqualByComparingTo("0.30");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets = (List<Map<String, Object>>) detail.get("assets");
        assertThat(assets).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) detail.get("params");
        assertThat(params).containsKey("icDrrPct");

        // 用户隔离
        assertThatThrownBy(() -> correctionService.getOperationDetail(999L, op.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @Transactional
    @DisplayName("2a-H02 · 月度 ZOH 确认：correctionMode=zoh_only + triggered 边界")
    void confirmMonthly_writesOperationLog() {
        MonthlyConfirmRequest req = new MonthlyConfirmRequest();
        req.setSnapshotDate(LocalDate.now().toString());
        req.setVCurr(new BigDecimal("8713.89"));
        req.setVMonetary(new BigDecimal("797.38"));
        req.setVBond(new BigDecimal("1194.11"));
        req.setUHigh(new BigDecimal("659"));
        req.setDeltaMTheory(new BigDecimal("186.81"));
        req.setDeltaBTheory(new BigDecimal("282.20"));
        req.setDeltaMActual(new BigDecimal("190"));
        req.setDeltaBActual(new BigDecimal("290"));
        req.setRoundingStrategy("round_up_10");
        req.setBudgetLimitUsed(new BigDecimal("1000"));
        req.setTotalInvestment(new BigDecimal("1143.76"));
        req.setNotes(List.of("ZOH 触发：Δm=186.81 ≥ 100"));

        CorrectionConfirmResponse resp = correctionService.confirmMonthly(TEST_USER_ID, req);

        OperationLog op = operationLogMapper.selectById(resp.getOperationLogId());
        assertThat(op.getCorrectionMode()).isEqualTo("zoh_only");
        assertThat(op.getOperationType()).isEqualTo("monthly_correction");
        assertThat(op.getTriggeredBoundary()).isEqualTo("1");
        assertThat(op.getDeltaMActual()).isEqualByComparingTo("190");
        assertThat(op.getSource()).isEqualTo("correction_page");
        assertThat(op.getConfirmedAt()).isNotNull();
        assertThat(resp.getDetailRows()).isZero();
    }
}