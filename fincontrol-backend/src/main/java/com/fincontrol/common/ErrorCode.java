package com.fincontrol.common;

/**
 * 错误码定义（[api-contract.md §11](#)）。
 *
 * <table>
 *   <tr><th>码</th><th>含义</th><th>HTTP</th></tr>
 *   <tr><td>0</td><td>成功</td><td>200</td></tr>
 *   <tr><td>1001</td><td>快照日期无效</td><td>400</td></tr>
 *   <tr><td>1003</td><td>金额必须 > 0</td><td>400</td></tr>
 *   <tr><td>1004</td><td>大类名称不在枚举值内</td><td>400</td></tr>
 *   <tr><td>2001</td><td>该日期无快照数据</td><td>404</td></tr>
 *   <tr><td>2002</td><td>快照日期与其他对话冲突</td><td>409</td></tr>
 *   <tr><td>2003</td><td>超过撤销时限（10 秒）</td><td>410</td></tr>
 *   <tr><td>3001</td><td>视觉模型 API 返回非 JSON</td><td>502</td></tr>
 *   <tr><td>3002</td><td>视觉模型 API 调用超时</td><td>504</td></tr>
 *   <tr><td>3003</td><td>视觉模型 API 返回 0 只基金</td><td>502</td></tr>
 *   <tr><td>5001</td><td>服务器内部错误</td><td>500</td></tr>
 *   <tr><td>5002</td><td>数据库写入失败</td><td>500</td></tr>
 * </table>
 *
 * <p>3001/3002/3003 原名为 DEEPSEEK_*（Phase 1a.2），Phase 1a.5 切换视觉模型后改为 VISION_*，
 * 码值 3001/3002/3003 不变（契约稳定），仅 Java 常量名 + 默认消息文本更新。
 *
 * <p>区间分配：[api-contract.md §1.4](#)：
 * <ul>
 *   <li>1001-1099 参数错误</li>
 *   <li>2001-2099 业务错误</li>
 *   <li>3001-3099 外部依赖</li>
 *   <li>5001-5099 服务器</li>
 * </ul>
 */
public enum ErrorCode {
    SUCCESS(0, "success"),
    INVALID_SNAPSHOT_DATE(1001, "快照日期无效"),
    AMOUNT_MUST_BE_POSITIVE(1003, "金额必须 > 0"),
    INVALID_CATEGORY_NAME(1004, "大类名称不在枚举值内"),
    SNAPSHOT_NOT_FOUND(2001, "该日期无快照数据"),
    SNAPSHOT_DATE_CONFLICT(2002, "快照日期与其他对话冲突"),
    UNDO_TIMEOUT(2003, "超过撤销时限（10 秒）"),
    VISION_INVALID_JSON(3001, "视觉模型 API 返回非 JSON"),
    VISION_TIMEOUT(3002, "视觉模型 API 调用超时"),
    VISION_ZERO_FUNDS(3003, "视觉模型 API 返回 0 只基金"),
    INTERNAL_ERROR(5001, "服务器内部错误"),
    DATABASE_ERROR(5002, "数据库写入失败");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
}
