package com.asuka.filelist.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户角色关联表，对应 user_roles。
 */
@TableName("user_roles")
public class UserRoleEntity {

    /** MP 不支持复合主键，这里仅标记 userId 避免通用 Mapper 启动告警；业务按 wrapper 操作复合键。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Long roleId;

    // ─── Getters & Setters ───────────────────────────────────

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
