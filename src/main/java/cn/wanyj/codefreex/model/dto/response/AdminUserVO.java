package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员用户视图对象
 *
 * @author wanyj
 */
@Data
public class AdminUserVO {

    private Long userId;

    private String nickname;

    private String avatar;

    private String email;

    private String phone;

    private List<String> roles;

    private String status;

    private Integer totalCredits;

    private Integer remainingCredits;

    private LocalDateTime createTime;
}
