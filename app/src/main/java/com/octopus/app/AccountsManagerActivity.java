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
 * 某个站点下的账号列表：切换 / 编辑 / 删除 / 添加。
 * 点击账号行 = 切换为该账号（返回结果给 MainActivity 执行快照切换）。
 */
public class AccountsManagerActivity extends Activity {

    public static final String EXTRA_SITE_ID = "site_id";
    public static final String EXTRA_SITE_NAME = "site_name";
    public static final String EXTRA_ACCOUNT_ID = "account_id";
    public static final String EXTRA_SWITCHED = "switched";

    private String siteId;
    private String siteName;
    private ListView listView;
    private View emptyState;
    private AccountAdapter adapter;
    private List<Account> accounts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accounts);
        siteId = getIntent().getStringExtra(EXTRA_SITE_ID);
        siteName = getIntent().getStringExtra(EXTRA_SITE_NAME);
        if (TextUtils.isEmpty(siteId)) {
            finish();
            return;
        }
        listView = findViewById(R.id.accounts_list);
        emptyState = findViewById(R.id.empty_state);
        TextView title = findViewById(R.id.title);
        title.setText(getString(R.string.accounts_title_of,
                TextUtils.isEmpty(siteName) ? getString(R.string.accounts_title) : siteName));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_account).setOnClickListener(v -> startEdit(null));
        adapter = new AccountAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                switchTo(accounts.get(position)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        accounts = AccountStore.getAccounts(this, siteId);
        adapter.notifyDataSetChanged();
        boolean empty = accounts.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void startEdit(String accountId) {
        Intent intent = new Intent(this, AccountEditActivity.class);
        intent.putExtra(AccountEditActivity.EXTRA_SITE_ID, siteId);
        intent.putExtra(AccountEditActivity.EXTRA_SITE_NAME, siteName);
        if (accountId != null) {
            intent.putExtra(AccountEditActivity.EXTRA_ACCOUNT_ID, accountId);
        }
        startActivity(intent);
    }

    private void switchTo(Account account) {
        Account active = AccountStore.getActiveAccount(this, siteId);
        if (active != null && account.id.equals(active.id)) {
            return;
        }
        AccountStore.setActiveAccountId(this, siteId, account.id);
        Intent result = new Intent();
        result.putExtra(EXTRA_SWITCHED, true);
        setResult(RESULT_OK, result);
        finish();
    }

    private void confirmDelete(final Account account) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    AccountStore.deleteAccount(this, siteId, account.id);
                    Toast.makeText(this, R.string.account_deleted, Toast.LENGTH_SHORT).show();
                    reload();
                })
                .show();
    }

    private class AccountAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return accounts.size();
        }

        @Override
        public Object getItem(int i) {
            return accounts.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AccountsManagerActivity.this)
                        .inflate(R.layout.item_account, parent, false);
            }
            final Account account = accounts.get(position);
            TextView name = convertView.findViewById(R.id.account_name);
            TextView username = convertView.findViewById(R.id.account_username);
            TextView activeBadge = convertView.findViewById(R.id.account_active);
            RadioButton radio = convertView.findViewById(R.id.account_default);
            ImageView edit = convertView.findViewById(R.id.account_edit);
            ImageView delete = convertView.findViewById(R.id.account_delete);

            name.setText(account.name);
            if (TextUtils.isEmpty(account.username)) {
                username.setText(account.hasSnapshot() ? "已登录" : "未登录");
            } else {
                username.setText(account.username + " · " + (account.hasSnapshot() ? "已登录" : "未登录"));
            }
            Account active = AccountStore.getActiveAccount(AccountsManagerActivity.this, siteId);
            boolean isActive = active != null && account.id.equals(active.id);
            radio.setChecked(account.isDefault);
            activeBadge.setVisibility(isActive ? View.VISIBLE : View.GONE);

            radio.setOnClickListener(v -> {
                AccountStore.setDefaultAccount(AccountsManagerActivity.this, siteId, account.id);
                reload();
            });
            edit.setOnClickListener(v -> startEdit(account.id));
            delete.setOnClickListener(v -> confirmDelete(account));
            return convertView;
        }
    }
}