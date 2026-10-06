package com.fincontrol.service;

import com.fincontrol.entity.NavHistory;
import com.fincontrol.mapper.NavHistoryMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2b 净值 Excel 自动同步服务（个人记录表 → {@code nav_history}）。
 *
 * <p>机制（与用户对齐的方案）：
 * <ul>
 *   <li>定时轮询（默认 30s）比较文件 {@code lastModified + size}；无变化直接跳过</li>
 *   <li>文件稳定检测：距最后写入 &lt; stability-seconds 时等待下一轮（EXCEL 保存有临时文件/锁文件）</li>
 *   <li>解析「每日明细」sheet（表头第 5 行、数据自第 6 行），按 {@code (user_id, nav_date)}
 *       幂等 upsert（{@link NavHistoryMapper#upsert}）——同一日期重复同步不产生重复行</li>
 *   <li>手动兜底：{@code POST /api/nav/sync}；状态：{@code GET /api/nav/sync-status}</li>
 * </ul>
 *
 * <p>不做删除：Excel 中缺失的旧日期保留在库中（避免误删）。
 */
@Service
public class NavSyncService {

    private static final Logger log = LoggerFactory.getLogger(NavSyncService.class);

    /** 数据 sheet 名（与个人记录表约定一致） */
    static final String SHEET_NAME = "每日明细";
    /** 表头在第 5 行（index 4），数据从第 6 行（index 5）开始 */
    static final int DATA_START_ROW = 5;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NavHistoryWriter navHistoryWriter;
    private final boolean enabled;
    private final String sourceFile;
    private final int stabilitySeconds;
    private final long userId;

    // —— 同步状态（供 /api/nav/sync-status 读取）——
    private volatile long lastModified = Long.MIN_VALUE;
    private volatile long lastSize = -1L;
    private volatile String lastSyncAt;
    private volatile int lastRows;
    private volatile String lastError;

    public NavSyncService(NavHistoryWriter navHistoryWriter,
                          @Value("${fincontrol.nav.sync.enabled:true}") boolean enabled,
                          @Value("${fincontrol.nav.sync.source-file:}") String sourceFile,
                          @Value("${fincontrol.nav.sync.stability-seconds:3}") int stabilitySeconds,
                          @Value("${fincontrol.default-user-id:1}") long userId) {
        this.navHistoryWriter = navHistoryWriter;
        this.enabled = enabled;
        this.sourceFile = sourceFile;
        this.stabilitySeconds = stabilitySeconds;
        this.userId = userId;
    }

    /** 定时轮询：变化 + 稳定 才同步；失败不更新指纹（下一轮自动重试）。 */
    @Scheduled(fixedDelayString = "${fincontrol.nav.sync.poll-interval-ms:30000}",
            initialDelayString = "10000")
    public void poll() {
        if (!enabled || sourceFile == null || sourceFile.isBlank()) {
            return;
        }
        Path path = Paths.get(sourceFile);
        try {
            if (!Files.isRegularFile(path)) {
                return;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            long size = Files.size(path);
            if (modified == lastModified && size == lastSize) {
                return;
            }
            if (System.currentTimeMillis() - modified < stabilitySeconds * 1000L) {
                return; // 可能仍在写入，等下一轮
            }
            Map<String, Object> result = doSync(path);
            lastModified = modified;
            lastSize = size;
            log.info("2b nav 自动同步完成：{}", result.get("message"));
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("2b nav 自动同步失败（下一轮重试）：{}", lastError);
        }
    }

    /** 手动同步（忽略变化指纹，文件不可读/不稳定时给出明确错误）。 */
    public Map<String, Object> syncNow() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (sourceFile == null || sourceFile.isBlank()) {
            out.put("synced", false);
            out.put("message", "未配置 fincontrol.nav.sync.source-file");
            return out;
        }
        Path path = Paths.get(sourceFile);
        if (!Files.isRegularFile(path)) {
            out.put("synced", false);
            out.put("message", "源文件不存在：" + sourceFile);
            return out;
        }
        try {
            Map<String, Object> result = doSync(path);
            lastModified = Files.getLastModifiedTime(path).toMillis();
            lastSize = Files.size(path);
            out.putAll(result);
            return out;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("2b nav 手动同步失败：{}", lastError);
            out.put("synced", false);
            out.put("message", "同步失败：" + e.getMessage());
            return out;
        }
    }

    /** 同步状态（GET /api/nav/sync-status）。 */
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("sourceFile", sourceFile);
        boolean exists = sourceFile != null && !sourceFile.isBlank()
                && Files.isRegularFile(Paths.get(sourceFile));
        out.put("fileExists", exists);
        if (exists) {
            try {
                Path p = Paths.get(sourceFile);
                out.put("fileLastModified", LocalDateTime.ofInstant(
                        Files.getLastModifiedTime(p).toInstant(),
                        java.time.ZoneId.systemDefault()).format(TS_FMT));
                out.put("fileSize", Files.size(p));
            } catch (IOException ignore) {
                // 状态接口不因读文件失败而报错
            }
        }
        out.put("lastSyncAt", lastSyncAt);
        out.put("lastRows", lastRows);
        out.put("lastError", lastError);
        return out;
    }

    // ===================== 解析 + 写入 =====================

    private Map<String, Object> doSync(Path path) throws IOException {
        List<NavHistory> rows = parse(path);
        int n = navHistoryWriter.upsertAll(rows);
        lastSyncAt = LocalDateTime.now().format(TS_FMT);
        lastRows = n;
        lastError = null;

        String range = rows.isEmpty() ? "-"
                : rows.get(0).getNavDate() + " ~ " + rows.get(rows.size() - 1).getNavDate();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("synced", true);
        out.put("rows", n);
        out.put("range", range);
        out.put("message", "已同步 " + n + " 行（" + range + "）");
        return out;
    }

    /**
     * 解析「每日明细」sheet → {@link NavHistory} 列表（未含 id）。
     *
     * <p>列约定（与个人记录表一致）：1=日期 2=星期 3=涨跌幅(%) 4=实际收益(元) 5=累加(元)
     * 6=净值 7=净值%(%) 8=总资产(元)。尾部空占位行（净值/累加/总资产均空）跳过。
     */
    List<NavHistory> parse(Path path) throws IOException {
        List<NavHistory> out = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(path.toFile())) {
            Sheet sheet = wb.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalStateException("未找到 sheet「" + SHEET_NAME + "」");
            }
            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                LocalDate date = readDate(row.getCell(1));
                if (date == null) {
                    continue;
                }
                Double cum = readNumber(row.getCell(5));
                Double nav = readNumber(row.getCell(6));
                Double totalAsset = readNumber(row.getCell(8));
                if (cum == null && nav == null && totalAsset == null) {
                    continue; // 空占位行
                }
                NavHistory h = new NavHistory();
                h.setUserId(userId);
                h.setNavDate(date);
                h.setWeekday(readString(row.getCell(2)));
                h.setDailyReturnPct(scale(readNumber(row.getCell(3)), 8));
                h.setActualProfit(scale(readNumber(row.getCell(4)), 2));
                h.setCumulativeProfit(scale(cum, 2));
                h.setNav(scale(nav, 8));
                h.setNavPct(scale(readNumber(row.getCell(7)), 8));
                h.setTotalAsset(scale(totalAsset, 2));
                out.add(h);
            }
        }
        return out;
    }

    private static LocalDate readDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell) || (v > 20000 && v < 60000)) {
                try {
                    return DateUtil.getLocalDateTime(v).toLocalDate();
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            try {
                return LocalDate.parse(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static Double readNumber(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (type == CellType.FORMULA) {
            try {
                return cell.getNumericCellValue();
            } catch (Exception e) {
                return null;
            }
        }
        if (type == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty() || "-".equals(s)) {
                return null;
            }
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String readString(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private static BigDecimal scale(Double v, int dp) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(dp, RoundingMode.HALF_UP);
    }
}