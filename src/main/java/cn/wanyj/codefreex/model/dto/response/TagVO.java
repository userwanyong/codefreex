package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * 标签 VO
 *
 * @author wanyj
 */
@Data
public class TagVO {

    private Long id;

    private String name;

    private Integer sortOrder;

    private Integer appCount;
}
