package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人中心视图对象：身份信息来自 auth-service RPC，码点/邀请来自本地业务表
 *
 * @author wanyj
 */
@Data
public class UserProfileVO {

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private Boolean emailVerified;

    private String phone;

    private Boolean phoneVerified;

    private List<String> roles;

    /** 邀请人用户ID（本地业务数据，可为空） */
    private Long inviterId;

    private Integer totalCredits;

    private Integer remainingCredits;

    /** 账号创建时间（auth-service） */
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
