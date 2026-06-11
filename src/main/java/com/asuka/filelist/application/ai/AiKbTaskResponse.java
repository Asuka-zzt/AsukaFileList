package com.asuka.filelist.application.ai;

/**
 * AI 服务索引任务状态（提交索引返回 / 查询任务状态）。
 *
 * @param taskId AI 服务 Celery 任务 id
 * @param status 任务状态（pending/parsing/indexing/indexed/failed 等，可空）
 * @param error  失败原因（可空）
 */
public record AiKbTaskResponse(
        String taskId,
        String status,
        String error
) {
}
