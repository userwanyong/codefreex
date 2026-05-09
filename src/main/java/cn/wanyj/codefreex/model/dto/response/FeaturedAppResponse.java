package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 精选应用游标分页响应
 *
 * @author wanyj
 */
@Data
public class FeaturedAppResponse {

    /**
     * 应用列表
     */
    private List<AppVO> records;

    /**
     * 下一页游标（null 表示无下一页）
     */
    private String nextCursor;

    /**
     * 是否还有下一页
     */
    private boolean hasNext;

    public static FeaturedAppResponse of(List<AppVO> records, String nextCursor, boolean hasNext) {
        FeaturedAppResponse response = new FeaturedAppResponse();
        response.setRecords(records);
        response.setNextCursor(nextCursor);
        response.setHasNext(hasNext);
        return response;
    }
}
