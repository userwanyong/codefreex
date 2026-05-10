package cn.wanyj.codefreex.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 工作流图片素材
 *
 * @author BanXia
 */
@Data
@AllArgsConstructor
public class WorkflowImageAsset {

    private String type;
    private String keyword;
    private String url;
    private String source;
}
