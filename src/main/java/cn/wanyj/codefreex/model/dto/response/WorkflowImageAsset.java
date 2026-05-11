package cn.wanyj.codefreex.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 工作流图片素材
 *
 * @author BanXia
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowImageAsset implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private String keyword;
    private String url;
    private String source;
}
