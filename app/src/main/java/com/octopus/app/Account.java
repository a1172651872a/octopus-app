package com.octopus.app;

/**
 * 站点下的一个账号。
 * 同一站点可以保存多个账号，每个账号保存一套独立的登录快照
 * （localStorage + Cookie），切换账号时整体替换，实现“多账号各自独立登录”。
 */
public class Account {
    public String id;
    public String siteId;
    public String name;
    public String username;
    public String password;
    /** 登录快照：Cookie 字符串，如 "auth=xxx; theme=dark" */
    public String cookies;
    /** 登录快照：localStorage 的 JSON，如 {"auth-storage":"..."} */
    public String localStorageJson;
    public boolean isDefault;
    public long updatedAt;

    public Account() {
    }

    public Account(String id, String siteId, String name, String username, String password) {
        this.id = id;
        this.siteId = siteId;
        this.name = name;
        this.username = username;
        this.password = password;
        this.cookies = "";
        this.localStorageJson = "";
    }

    public boolean hasSnapshot() {
        return (cookies != null && !cookies.isEmpty())
                || (localStorageJson != null && !localStorageJson.isEmpty()
                && !localStorageJson.equals("{}"));
    }
}