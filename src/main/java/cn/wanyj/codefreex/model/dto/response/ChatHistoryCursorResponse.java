package cn.wanyj.codefreex.model.dto.response;

import cn.wanyj.codefreex.model.entity.ChatHistory;
import lombok.Data;

import java.util.List;

/**
 * @author BanXia
 */
@Data
public class ChatHistoryCursorResponse {

    /**
     * 历史消息列表（按时间正序返回）
     */
    private List<ChatHistory> records;

    /**
     * 下一页游标（用于查询更早的消息）
     */
    private String nextCursor;

    /**
     * 是否还有更多历史
     */
    private boolean hasNext;

    public static ChatHistoryCursorResponse of(List<ChatHistory> records, String nextCursor, boolean hasNext) {
        ChatHistoryCursorResponse response = new ChatHistoryCursorResponse();
        response.setRecords(records);
        response.setNextCursor(nextCursor);
        response.setHasNext(hasNext);
        return response;
    }
}
