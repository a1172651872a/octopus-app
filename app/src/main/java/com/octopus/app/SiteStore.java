package com.octopus.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 站点列表的本地持久化（SharedPreferences + JSON）。 */
public final class SiteStore {

    private static final String PREF = "octopus_sites";
    private static final String KEY_SITES = "sites";

    private SiteStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Site> getSites(Context ctx) {
        List<Site> list = new ArrayList<>();
        String raw = prefs(ctx).getString(KEY_SITES, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Site(
                        o.getString("id"),
                        o.optString("name", ""),
                        o.getString("url"),
                        o.optBoolean("def", false)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void saveSites(Context ctx, List<Site> sites) {
        JSONArray arr = new JSONArray();
        try {
            for (Site s : sites) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("url", s.url);
                o.put("def", s.isDefault);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(ctx).edit().putString(KEY_SITES, arr.toString()).apply();
    }

    /** 默认站点：优先 isDefault 的站点，否则第一个站点。 */
    public static Site getDefaultSite(Context ctx) {
        List<Site> sites = getSites(ctx);
        for (Site s : sites) {
            if (s.isDefault) return s;
        }
        return sites.isEmpty() ? null : sites.get(0);
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 设置某个站点为默认，并取消其它站点的默认标记。 */
    public static void setDefault(Context ctx, String siteId) {
        List<Site> sites = getSites(ctx);
        boolean found = false;
        for (Site s : sites) {
            boolean def = s.id.equals(siteId);
            s.isDefault = def;
            if (def) found = true;
        }
        // 若指定的站点不存在但列表非空，则回退到第一个
        if (!found && !sites.isEmpty()) {
            for (Site s : sites) s.isDefault = false;
            sites.get(0).isDefault = true;
        }
        saveSites(ctx, sites);
    }

    public static void addSite(Context ctx, Site site) {
        List<Site> sites = getSites(ctx);
        sites.add(site);
        saveSites(ctx, sites);
    }

    public static void updateSite(Context ctx, Site site) {
        List<Site> sites = getSites(ctx);
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i).id.equals(site.id)) {
                sites.set(i, site);
                break;
            }
        }
        saveSites(ctx, sites);
    }

    public static void deleteSite(Context ctx, String siteId) {
        List<Site> sites = getSites(ctx);
        List<Site> remain = new ArrayList<>();
        for (Site s : sites) {
            if (!s.id.equals(siteId)) remain.add(s);
        }
        // 删除后保证仍有一个默认站点
        if (!remain.isEmpty()) {
            boolean hasDefault = false;
            for (Site s : remain) {
                if (s.isDefault) {
                    hasDefault = true;
                    break;
                }
            }
            if (!hasDefault) remain.get(0).isDefault = true;
        }
        saveSites(ctx, remain);
        // 同时清理该站点的账号数据
        AccountStore.deleteAllForSite(ctx, siteId);
    }

    public static boolean existsUrl(Context ctx, String url, String excludeId) {
        String target = normalizeUrl(url);
        for (Site s : getSites(ctx)) {
            if (excludeId != null && s.id.equals(excludeId)) continue;
            if (normalizeUrl(s.url).equals(target)) return true;
        }
        return false;
    }

    /** 规范化地址：补全 scheme，去空格。 */
    public static String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase(Locale.ROOT);
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        return u;
    }
}
