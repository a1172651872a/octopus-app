package com.octopus.app;

/** 站点模型：一个网页地址，可自定义名称并设为默认。 */
public class Site {
    public String id;
    public String name;
    public String url;
    public boolean isDefault;

    public Site() {
    }

    public Site(String id, String name, String url, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.isDefault = isDefault;
    }
}
