package com.octopus.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 添加 / 编辑站点。
 * 地址会自动补全 http://，校验后写入 SiteStore。
 */
public class SiteEditActivity extends Activity {

    public static final String EXTRA_SITE_ID = "site_id";

    private EditText inputName;
    private EditText inputUrl;
    private CheckBox checkDefault;
    private String siteId;
    private boolean editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_site_edit);

        siteId = getIntent().getStringExtra(EXTRA_SITE_ID);
        editing = !TextUtils.isEmpty(siteId);

        inputName = findViewById(R.id.input_name);
        inputUrl = findViewById(R.id.input_url);
        checkDefault = findViewById(R.id.check_default);
        Button btnDelete = findViewById(R.id.btn_delete);

        ((TextView) findViewById(R.id.title)).setText(editing ? R.string.edit_site : R.string.add_site);
        btnDelete.setVisibility(editing ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        Site editingSite = null;
        if (editing) {
            editingSite = findSite(siteId);
            if (editingSite == null) {
                finish();
                return;
            }
            inputName.setText(editingSite.name);
            inputUrl.setText(editingSite.url);
            checkDefault.setChecked(editingSite.isDefault);
        } else if (SiteStore.getSites(this).isEmpty()) {
            // 第一次添加站点：默认勾选“设为默认”
            checkDefault.setChecked(true);
        }

        Button btnAccounts = findViewById(R.id.btn_accounts);
        btnAccounts.setVisibility(editing ? View.VISIBLE : View.GONE);
        final Site siteForAccounts = editingSite;
        btnAccounts.setOnClickListener(v -> {
            Intent intent = new Intent(this, AccountsManagerActivity.class);
            intent.putExtra(AccountsManagerActivity.EXTRA_SITE_ID, siteId);
            intent.putExtra(AccountsManagerActivity.EXTRA_SITE_NAME,
                    siteForAccounts != null ? siteForAccounts.name : "");
            startActivity(intent);
        });

        final Site site = editingSite;
        findViewById(R.id.btn_save).setOnClickListener(v -> save(site));
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private Site findSite(String id) {
        for (Site s : SiteStore.getSites(this)) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    private void save(Site editingSite) {
        String name = inputName.getText().toString().trim();
        String urlRaw = inputUrl.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.site_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String url = SiteStore.normalizeUrl(urlRaw);
        if (!isValidUrl(url)) {
            Toast.makeText(this, R.string.site_url_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        if (SiteStore.existsUrl(this, url, editing ? siteId : null)) {
            Toast.makeText(this, R.string.site_exists, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean wasDefault = editingSite != null && editingSite.isDefault;
        Site site = editingSite != null
                ? editingSite
                : new Site(SiteStore.newId(), name, url, false);
        site.name = name;
        site.url = url;

        if (editing) {
            SiteStore.updateSite(this, site);
        } else {
            SiteStore.addSite(this, site);
        }

        boolean onlySite = SiteStore.getSites(this).size() == 1;
        if (checkDefault.isChecked() || onlySite) {
            SiteStore.setDefault(this, site.id);
        } else if (wasDefault) {
            // 取消“默认”勾选：把默认交给其它站点
            for (Site s : SiteStore.getSites(this)) {
                if (!s.id.equals(site.id)) {
                    SiteStore.setDefault(this, s.id);
                    break;
                }
            }
        }

        Toast.makeText(this, R.string.site_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private boolean isValidUrl(String url) {
        try {
            android.net.Uri u = android.net.Uri.parse(url);
            String scheme = u.getScheme();
            return u.getHost() != null
                    && scheme != null
                    && (scheme.equals("http") || scheme.equals("https"));
        } catch (Exception e) {
            return false;
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_site_title)
                .setMessage(R.string.delete_site_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    SiteStore.deleteSite(this, siteId);
                    Toast.makeText(this, R.string.site_deleted, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .show();
    }
}