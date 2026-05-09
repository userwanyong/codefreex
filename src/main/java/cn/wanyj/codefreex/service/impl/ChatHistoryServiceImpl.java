package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.mapper.ChatHistoryMapper;
import cn.wanyj.codefreex.model.dto.response.ChatHistoryCursorResponse;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.service.ChatHistoryService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static cn.wanyj.codefreex.model.entity.table.ChatHistoryTableDef.CHAT_HISTORY;

/**
 * @author BanXia
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter CURSOR_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ChatHistoryMapper chatHistoryMapper;
    private final AppMapper appMapper;

    @Override
    public ChatHistory saveUserMessage(Long appId, Long userId, String message) {
        return saveMessage(appId, userId, message, "user", null);
    }

    @Override
    public ChatHistory saveAiMessage(Long appId, Long userId, String message) {
        return saveAiMessage(appId, userId, message, null);
    }

    @Override
    public ChatHistory saveAiMessage(Long appId, Long userId, String message, Long parentId) {
        return saveMessage(appId, userId, message, "ai", parentId);
    }

    @Override
    public ChatHistoryCursorResponse listChatHistory(Long appId, Long userId, String cursor, int size) {
        checkAppOwner(appId, userId);

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        QueryWrapper query = QueryWrapper.create()
                .where(CHAT_HISTORY.APP_ID.eq(appId))
                .and(CHAT_HISTORY.USER_ID.eq(userId));

        if (cursor != null && !cursor.isEmpty()) {
            CursorInfo cursorInfo = parseCursor(cursor);
            query.and(
                    CHAT_HISTORY.CREATE_TIME.lt(cursorInfo.time)
                            .or(CHAT_HISTORY.CREATE_TIME.eq(cursorInfo.time).and(CHAT_HISTORY.ID.lt(cursorInfo.id)))
            );
        }

        query.orderBy(CHAT_HISTORY.CREATE_TIME.desc(), CHAT_HISTORY.ID.desc())
                .limit(pageSize + 1);

        List<ChatHistory> records = chatHistoryMapper.selectListByQuery(query);
        boolean hasNext = records.size() > pageSize;
        if (hasNext) {
            records = records.subList(0, pageSize);
        }

        String nextCursor = null;
        if (hasNext && !records.isEmpty()) {
            ChatHistory oldest = records.get(records.size() - 1);
            nextCursor = buildCursor(oldest.getCreateTime(), oldest.getId());
        }

        Collections.reverse(records);
        return ChatHistoryCursorResponse.of(records, nextCursor, hasNext);
    }

    @Override
    public List<ChatHistory> listRecentMessages(Long appId, Long userId, int limit) {
        checkAppOwner(appId, userId);

        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        QueryWrapper query = QueryWrapper.create()
                .where(CHAT_HISTORY.APP_ID.eq(appId))
                .and(CHAT_HISTORY.USER_ID.eq(userId))
                .orderBy(CHAT_HISTORY.CREATE_TIME.desc(), CHAT_HISTORY.ID.desc())
                .limit(pageSize);

        List<ChatHistory> records = chatHistoryMapper.selectListByQuery(query);
        Collections.reverse(records);
        return records;
    }

    private ChatHistory saveMessage(Long appId, Long userId, String message, String messageType, Long parentId) {
        checkAppOwner(appId, userId);
        validateMessage(message);

        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setAppId(appId);
        chatHistory.setUserId(userId);
        chatHistory.setMessage(message);
        chatHistory.setMessageType(messageType);
        chatHistory.setParentId(parentId);
        chatHistoryMapper.insert(chatHistory);
        return chatHistory;
    }

    private void checkAppOwner(Long appId, Long userId) {
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "应用不存在");
        }
        if (!app.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "无权限访问该应用对话");
        }
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "消息不能为空");
        }
    }

    private CursorInfo parseCursor(String cursor) {
        int lastUnderscore = cursor.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "无效的游标参数");
        }

        String timeStr = cursor.substring(0, lastUnderscore);
        String idStr = cursor.substring(lastUnderscore + 1);
        try {
            LocalDateTime time = LocalDateTime.parse(timeStr, CURSOR_TIME_FORMATTER);
            Long id = Long.parseLong(idStr);
            return new CursorInfo(time, id);
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "无效的游标参数");
        }
    }

    private String buildCursor(LocalDateTime time, Long id) {
        return time.format(CURSOR_TIME_FORMATTER) + "_" + id;
    }

    private record CursorInfo(LocalDateTime time, Long id) {
    }
}
