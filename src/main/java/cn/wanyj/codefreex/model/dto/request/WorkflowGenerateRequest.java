package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * P7 宸ヤ綔娴佺敓鎴愯姹?
 *
 * @author BanXia
 */
@Data
public class WorkflowGenerateRequest {

    @NotNull(message = "搴旂敤 ID 涓嶈兘涓虹┖")
    private Long appId;

    @NotBlank(message = "鐢熸垚鎻愮ず璇嶄笉鑳戒负绌?")
    private String message;
}
