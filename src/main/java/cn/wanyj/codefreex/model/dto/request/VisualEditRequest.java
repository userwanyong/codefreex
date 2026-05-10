package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 鍙鍖栫紪杈戣姹?
 *
 * @author BanXia
 */
@Data
public class VisualEditRequest {

    @NotNull(message = "搴旂敤 ID 涓嶈兘涓虹┖")
    private Long appId;

    @NotBlank(message = "鍏冪礌閫夋嫨鍣ㄤ笉鑳戒负绌?")
    private String selector;

    @NotBlank(message = "鍘熷鍏冪礌 HTML 涓嶈兘涓虹┖")
    private String selectedHtml;

    @NotBlank(message = "淇敼闇€姹備笉鑳戒负绌?")
    private String instruction;

    private String targetFile;
}
