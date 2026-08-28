package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户业务档案：仅存本地业务数据（码点/邀请），用户身份信息统一由 auth-service 提供。
 * user_id 即 auth-service 用户ID。
 *
 * @author wanyj
 */
@Data
@Table("user_info")
public class UserInfo {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** auth-service 用户ID */
    private Long userId;

    private Long inviterId;

    /** 累计获得码点 */
    private Integer totalCredits;

    /** 剩余码点 */
    private Integer remainingCredits;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    private Integer isDelete;
}
