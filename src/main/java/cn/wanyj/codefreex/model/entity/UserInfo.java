package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author wanyj
 */
@Data
@Table("user_info")
public class UserInfo {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private Long inviterId;

    private String nickname;

    private String avatar;

    /** 累计获得码点 */
    private Integer totalCredits;

    /** 剩余码点 */
    private Integer remainingCredits;

    private String status;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    private Integer isDelete;
}
