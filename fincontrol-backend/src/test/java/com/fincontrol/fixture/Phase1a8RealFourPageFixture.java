package com.fincontrol.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincontrol.dto.screenshot.ParsedAsset;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 1a.8 v3 四页真实数据的唯一机器可读 fixture 入口。
 * 图片本身保持在 gitignored uploads/samples；本类只读取不含用户图片的期望 JSON。
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
            ParsedAsset parsedAsset
    ) {
    }

    public record ExpectedFund(
            String categoryName,
            String fundName,
            BigDecimal amount,
            BigDecimal profit
    ) {
    }
}
