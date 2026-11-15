package com.octopus.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 按站点保存账号列表及其登录快照。
 *
 * 存储结构：
 *   accounts_<siteId> -> [Account JSON...]
 *   active_<siteId>   -> 当前激活账号 id
 *   pending_logout_<siteId> -> 密码已更新、需要强制重新登录的站点
 */
public final class AccountStore {

    private static final String PREF = "octopus_accounts";

    private AccountStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Account> getAccounts(Context ctx, String siteId) {
        List<Account> list = new ArrayList<>();
        String raw = prefs(ctx).getString("accounts_" + siteId, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Account a = new Account();
                a.id = o.getString("id");
                a.siteId = siteId;
                a.name = o.optString("name", "");
                a.username = o.optString("username", "");
                a.password = o.optString("password", "");
                a.cookies = o.optString("cookies", "");
                a.localStorageJson = o.optString("localStorageJson", "");
                a.isDefault = o.optBoolean("def", false);
                a.updatedAt = o.optLong("updatedAt", 0L);
                list.add(a);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void saveAccounts(Context ctx, String siteId, List<Account> accounts) {
        JSONArray arr = new JSONArray();
        try {
            for (Account a : accounts) {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("name", a.name);
                o.put("username", a.username);
                o.put("password", a.password);
                o.put("cookies", a.cookies);
                o.put("localStorageJson", a.localStorageJson);
                o.put("def", a.isDefault);
                o.put("updatedAt", a.updatedAt);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(ctx).edit().putString("accounts_" + siteId, arr.toString()).apply();
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static Account findById(Context ctx, String siteId, String accountId) {
        if (accountId == null) return null;
        for (Account a : getAccounts(ctx, siteId)) {
            if (a.id.equals(accountId)) return a;
        }
        return null;
    }

    public static Account getDefaultAccount(Context ctx, String siteId) {
        for (Account a : getAccounts(ctx, siteId)) {
            if (a.isDefault) return a;
        }
        List<Account> list = getAccounts(ctx, siteId);
        return list.isEmpty() ? null : list.get(0);
    }

    public static String getActiveAccountId(Context ctx, String siteId) {
        return prefs(ctx).getString("active_" + siteId, null);
    }

    public static void setActiveAccountId(Context ctx, String siteId, String accountId) {
        if (accountId == null) {
            prefs(ctx).edit().remove("active_" + siteId).apply();
        } else {
            prefs(ctx).edit().putString("active_" + siteId, accountId).apply();
        }
    }

    /** 当前激活账号：优先 active，其次默认账号，否则 null。 */
    public static Account getActiveAccount(Context ctx, String siteId) {
        Account a = findById(ctx, siteId, getActiveAccountId(ctx, siteId));
        return a != null ? a : getDefaultAccount(ctx, siteId);
    }

    public static void addAccount(Context ctx, Account account) {
        List<Account> list = getAccounts(ctx, account.siteId);
        if (list.isEmpty()) {
            account.isDefault = true;
        }
        list.add(account);
        saveAccounts(ctx, account.siteId, list);
        if (getActiveAccountId(ctx, account.siteId) == null) {
            setActiveAccountId(ctx, account.siteId, account.id);
        }
    }

    public static void updateAccount(Context ctx, Account account) {
        List<Account> list = getAccounts(ctx, account.siteId);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(account.id)) {
                list.set(i, account);
                break;
            }
        }
        saveAccounts(ctx, account.siteId, list);
    }

    public static void deleteAccount(Context ctx, String siteId, String accountId) {
        List<Account> list = getAccounts(ctx, siteId);
        Account removed = null;
        List<Account> remain = new ArrayList<>();
        for (Account a : list) {
            if (a.id.equals(accountId)) {
                removed = a;
            } else {
                remain.add(a);
            }
        }
        if (!remain.isEmpty() && removed != null && removed.isDefault) {
            remain.get(0).isDefault = true;
        }
        saveAccounts(ctx, siteId, remain);
        if (accountId.equals(getActiveAccountId(ctx, siteId))) {
            setActiveAccountId(ctx, siteId, remain.isEmpty() ? null : remain.get(0).id);
        }
    }

    public static void setDefaultAccount(Context ctx, String siteId, String accountId) {
        List<Account> list = getAccounts(ctx, siteId);
        boolean found = false;
        for (Account a : list) {
            boolean def = a.id.equals(accountId);
            a.isDefault = def;
            if (def) found = true;
        }
        if (!found && !list.isEmpty()) {
            list.get(0).isDefault = true;
        }
        saveAccounts(ctx, siteId, list);
    }

    /** 删除站点时清理其账号数据。 */
    public static void deleteAllForSite(Context ctx, String siteId) {
        prefs(ctx).edit()
                .remove("accounts_" + siteId)
                .remove("active_" + siteId)
                .remove("pending_logout_" + siteId)
                .apply();
    }

    /* ------------------------------ 密码更新标记 ------------------------------ */

    public static void setPendingLogout(Context ctx, String siteId, boolean pending) {
        if (pending) {
            prefs(ctx).edit().putBoolean("pending_logout_" + siteId, true).apply();
        } else {
            prefs(ctx).edit().remove("pending_logout_" + siteId).apply();
        }
    }

    public static boolean isPendingLogout(Context ctx, String siteId) {
        return prefs(ctx).getBoolean("pending_logout_" + siteId, false);
    }
}