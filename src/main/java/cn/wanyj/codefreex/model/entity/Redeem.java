package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author wanyj
 */
@Data
@Table("redeem")
public class Redeem {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String redeemCode;

    private Long userId;

    private String batch;

    private Integer quota;

    private String status;

    private LocalDateTime expireTime;

    private Integer maxUseCount;

    private Integer usedCount;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    private Integer isDelete;
}
