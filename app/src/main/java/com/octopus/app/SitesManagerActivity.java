package com.octopus.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 站点管理：添加 / 编辑 / 删除站点，设置默认站点。
 * 只负责列表，具体编辑交给 SiteEditActivity。
 */
public class SitesManagerActivity extends Activity {

    private ListView listView;
    private View emptyState;
    private SiteAdapter adapter;
    private List<Site> sites = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sites);
        listView = findViewById(R.id.sites_list);
        emptyState = findViewById(R.id.empty_state);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_site).setOnClickListener(v -> startEdit(null));
        adapter = new SiteAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> startEdit(sites.get(position).id));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        sites = SiteStore.getSites(this);
        adapter.notifyDataSetChanged();
        boolean empty = sites.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void startEdit(String siteId) {
        Intent intent = new Intent(this, SiteEditActivity.class);
        if (siteId != null) {
            intent.putExtra(SiteEditActivity.EXTRA_SITE_ID, siteId);
        }
        startActivity(intent);
    }

    private void confirmDelete(final Site site) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_site_title)
                .setMessage(R.string.delete_site_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    SiteStore.deleteSite(this, site.id);
                    Toast.makeText(this, R.string.site_deleted, Toast.LENGTH_SHORT).show();
                    reload();
                })
                .show();
    }

    private class SiteAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return sites.size();
        }

        @Override
        public Object getItem(int i) {
            return sites.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SitesManagerActivity.this)
                        .inflate(R.layout.item_site, parent, false);
            }
            final Site site = sites.get(position);
            TextView name = convertView.findViewById(R.id.site_name);
            TextView url = convertView.findViewById(R.id.site_url);
            RadioButton radio = convertView.findViewById(R.id.site_default);
            ImageView delete = convertView.findViewById(R.id.site_delete);

            name.setText(site.name);
            url.setText(site.url);
            radio.setChecked(site.isDefault);
            radio.setOnClickListener(v -> {
                SiteStore.setDefault(SitesManagerActivity.this, site.id);
                reload();
            });
            delete.setOnClickListener(v -> confirmDelete(site));
            return convertView;
        }
    }
}