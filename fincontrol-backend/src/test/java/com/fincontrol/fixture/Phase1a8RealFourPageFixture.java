package com.fincontrol.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.dto.screenshot.ParsedAsset;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 1a.8 v3.1 四页真实数据的唯一机器可读 fixture 入口。
 * 升级点：每只基金必含 holding_profit / cumulative_profit 双字段；fixture 自带 per-page expectedTotalAsset。
 */
public final class Phase1a8RealFourPageFixture {

    public static final String RESOURCE = "/fixtures/phase1a8-real-four-pages.json";

    private Phase1a8RealFourPageFixture() {
    }

    public static Fixture load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = Phase1a8RealFourPageFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("fixture 不存在: " + RESOURCE);
            }
            return mapper.readValue(in, Fixture.class);
        } catch (IOException e) {
            throw new IllegalStateException("fixture 读取失败: " + RESOURCE, e);
        }
    }

    public record Fixture(
            String fixtureVersion,
            String snapshotDate,
            int expectedCompleteInputCount,
            int expectedUniqueCount,
            BigDecimal expectedTotalAsset,
            BigDecimal expectedUniqueTotalAmount,
            List<Page> pages,
            List<ExpectedFund> expectedUnique
    ) {
        public List<ParsedAsset> parsedAssets() {
            return pages.stream().map(Page::parsedAsset).toList();
        }
    }

    public record Page(
            String pageId,
            String fileName,
            int expectedCompleteCount,
            List<String> headerOnly,
            BigDecimal expectedTotalAsset,
            ParsedAsset parsedAsset
    ) {
    }

    public record ExpectedFund(
            String categoryName,
            String fundName,
            BigDecimal amount,
            BigDecimal holdingProfit,
            BigDecimal cumulativeProfit
    ) {
    }
}
