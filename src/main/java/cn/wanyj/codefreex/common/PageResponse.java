package cn.wanyj.codefreex.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应封装
 *
 * @author wanyj
 */
@Data
public class PageResponse<T> implements Serializable {

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页号
     */
    private int pageNum;

    /**
     * 每页大小
     */
    private int pageSize;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 数据列表
     */
    private List<T> records;

    public static <T> PageResponse<T> of(List<T> records, long total, int pageNum, int pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        response.setTotalPages((int) Math.ceil((double) total / pageSize));
        return response;
    }
}
