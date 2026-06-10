-- M-WebDAV: 用户 WebDAV Digest 凭据 HA1 = MD5(username:realm:webdavPassword)
-- 仅存 HA1（不可逆），不存明文；NULL 表示该用户未设置 WebDAV 密码，禁止 WebDAV 登录。
ALTER TABLE users
    ADD COLUMN webdav_ha1 VARCHAR(64) NULL AFTER permission;
