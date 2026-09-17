package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告-用户确认实体（用户选择“不再弹出”后落库）
 *
 * @author wanyj
 */
@Data
@Table("announcement_ack")
public class AnnouncementAck {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long announcementId;

    private Long userId;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;
}
