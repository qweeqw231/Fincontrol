package com.fincontrol.service;

import com.fincontrol.entity.NavHistory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 2b：{@link NavSyncService} —— 解析「每日明细」sheet（POI）+ 手动同步流程（写入走 mock）。
 *
 * <p>fixture 由 POI 现场生成（表头第 5 行、数据自第 6 行），不依赖真实个人数据文件。
 */
class NavSyncServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("2b-S01 · parse：表头偏移 / serial 与字符串日期 / \"-\" 容忍 / 空占位行跳过")
    void parse_readsRowsWithHeaderOffsetAndSkipsPlaceholders() throws IOException {
        Path fixture = writeFixture();
        NavSyncService svc = new NavSyncService(mock(NavHistoryWriter.class), false, "", 3, 1L);

        List<NavHistory> rows = svc.parse(fixture);

        assertThat(rows).hasSize(2);
        NavHistory first = rows.get(0);
        assertThat(first.getUserId()).isEqualTo(1L);
        assertThat(first.getNavDate()).isEqualTo(LocalDate.parse("2025-10-13"));
        assertThat(first.getWeekday()).isEqualTo("周一");
        assertThat(first.getDailyReturnPct()).isEqualByComparingTo("0.0001");
        assertThat(first.getActualProfit()).isEqualByComparingTo("0.01");
        assertThat(first.getCumulativeProfit()).isEqualByComparingTo("0.01");
        assertThat(first.getNav()).isEqualByComparingTo("1.0001");
        assertThat(first.getNavPct()).isEqualByComparingTo("0.01");
        assertThat(first.getTotalAsset()).isEqualByComparingTo("45.04");

        NavHistory second = rows.get(1);
        assertThat(second.getNavDate()).isEqualTo(LocalDate.parse("2025-10-14"));
        assertThat(second.getDailyReturnPct()).isNull(); // "-" → null
        assertThat(second.getNav()).isEqualByComparingTo("1.0017");
    }

    @Test
    @DisplayName("2b-S02 · syncNow：解析后经 NavHistoryWriter 批量 upsert，状态可查")
    void syncNow_writesViaWriter_andUpdatesStatus() throws IOException {
        Path fixture = writeFixture();
        NavHistoryWriter writer = mock(NavHistoryWriter.class);
        when(writer.upsertAll(anyList())).thenReturn(2);
        NavSyncService svc = new NavSyncService(writer, true, fixture.toString(), 3, 1L);

        Map<String, Object> result = svc.syncNow();

        assertThat(result.get("synced")).isEqualTo(true);
        assertThat(result.get("rows")).isEqualTo(2);
        assertThat(String.valueOf(result.get("range"))).isEqualTo("2025-10-13 ~ 2025-10-14");

        Map<String, Object> status = svc.status();
        assertThat(status.get("enabled")).isEqualTo(true);
        assertThat(status.get("fileExists")).isEqualTo(true);
        assertThat(status.get("lastRows")).isEqualTo(2);
        assertThat(status.get("lastSyncAt")).isNotNull();
        assertThat(status.get("lastError")).isNull();
    }

    @Test
    @DisplayName("2b-S03 · syncNow：文件不存在 → 明确失败信息（不抛异常）")
    void syncNow_missingFile_returnsClearFailure() {
        NavSyncService svc = new NavSyncService(mock(NavHistoryWriter.class), true,
                tempDir.resolve("not-exist.xlsx").toString(), 3, 1L);

        Map<String, Object> result = svc.syncNow();

        assertThat(result.get("synced")).isEqualTo(false);
        assertThat(String.valueOf(result.get("message"))).contains("源文件不存在");
    }

    @Test
    @DisplayName("2b-S04 · syncNow：未配置 source-file → 提示配置项")
    void syncNow_blankSourceFile() {
        NavSyncService svc = new NavSyncService(mock(NavHistoryWriter.class), true, "", 3, 1L);

        Map<String, Object> result = svc.syncNow();

        assertThat(result.get("synced")).isEqualTo(false);
        assertThat(String.valueOf(result.get("message"))).contains("source-file");
    }

    /** 生成与个人记录表同构的最小 fixture：前 4 行为说明/空行，第 5 行为表头。 */
    private Path writeFixture() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("每日明细");
            sheet.createRow(0).createCell(0).setCellValue("每日明细 (fixture)");
            // rows 1-3 留空
            sheet.createRow(1);
            sheet.createRow(2);
            sheet.createRow(3);
            Row header = sheet.createRow(4);
            String[] cols = {"序号", "日期", "星期", "涨跌幅(%)", "实际收益(元)", "累加(元)", "净值", "净值%(%)", "总资产（元）"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            // 数据行 1：Excel serial 日期（45943 = 2025-10-13）
            Row r1 = sheet.createRow(5);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue(45943);
            r1.createCell(2).setCellValue("周一");
            r1.createCell(3).setCellValue(0.0001);
            r1.createCell(4).setCellValue(0.01);
            r1.createCell(5).setCellValue(0.01);
            r1.createCell(6).setCellValue(1.0001);
            r1.createCell(7).setCellValue(0.01);
            r1.createCell(8).setCellValue(45.04);
            // 数据行 2：字符串日期 + "-" 占位
            Row r2 = sheet.createRow(6);
            r2.createCell(1).setCellValue("2025-10-14");
            r2.createCell(2).setCellValue("周二");
            r2.createCell(3).setCellValue("-");
            r2.createCell(5).setCellValue(0.08);
            r2.createCell(6).setCellValue(1.0017);
            r2.createCell(8).setCellValue(34.72);
            // 空占位行：有日期但净值/累加/总资产均空 → 跳过
            Row r3 = sheet.createRow(7);
            r3.createCell(1).setCellValue(45945);
            // 无日期行 → 跳过
            Row r4 = sheet.createRow(8);
            r4.createCell(0).setCellValue(999);

            Path p = tempDir.resolve("nav-fixture.xlsx");
            try (OutputStream os = Files.newOutputStream(p)) {
                wb.write(os);
            }
            return p;
        }
    }

    @SuppressWarnings("unused")
    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}