package com.octopus.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 添加 / 编辑某个站点下的账号。
 *
 * 密码同步规则：
 *  - 修改密码后清空该账号的登录快照；
 *  - 若该账号正是当前激活账号，则标记 pendingLogout，
 *    回到主界面时会清除站点登录态并重新加载，强制用新密码登录。
 */
public class AccountEditActivity extends Activity {

    public static final String EXTRA_SITE_ID = "site_id";
    public static final String EXTRA_SITE_NAME = "site_name";
    public static final String EXTRA_ACCOUNT_ID = "account_id";

    private String siteId;
    private String siteName;
    private String accountId;
    private boolean editing;

    private EditText inputName;
    private EditText inputUsername;
    private EditText inputPassword;
    private CheckBox checkDefault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);

        siteId = getIntent().getStringExtra(EXTRA_SITE_ID);
        siteName = getIntent().getStringExtra(EXTRA_SITE_NAME);
        accountId = getIntent().getStringExtra(EXTRA_ACCOUNT_ID);
        editing = !TextUtils.isEmpty(accountId);
        if (TextUtils.isEmpty(siteId)) {
            finish();
            return;
        }

        inputName = findViewById(R.id.input_name);
        inputUsername = findViewById(R.id.input_username);
        inputPassword = findViewById(R.id.input_password);
        checkDefault = findViewById(R.id.check_default);
        Button btnDelete = findViewById(R.id.btn_delete);

        ((TextView) findViewById(R.id.title)).setText(editing ? R.string.edit_account : R.string.add_account);
        btnDelete.setVisibility(editing ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        Account editingAccount = null;
        if (editing) {
            editingAccount = AccountStore.findById(this, siteId, accountId);
            if (editingAccount == null) {
                finish();
                return;
            }
            inputName.setText(editingAccount.name);
            inputUsername.setText(editingAccount.username);
            inputPassword.setText(editingAccount.password);
            checkDefault.setChecked(editingAccount.isDefault);
        } else if (AccountStore.getAccounts(this, siteId).isEmpty()) {
            checkDefault.setChecked(true);
        }

        final Account account = editingAccount;
        findViewById(R.id.btn_save).setOnClickListener(v -> save(account));
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private void save(Account editingAccount) {
        String name = inputName.getText().toString().trim();
        String username = inputUsername.getText().toString().trim();
        String password = inputPassword.getText().toString();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.account_name_required, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean passwordChanged;
        boolean isActive;
        if (editingAccount != null) {
            passwordChanged = !TextUtils.equals(editingAccount.password, password);
            isActive = editingAccount.id.equals(
                    AccountStore.getActiveAccountId(this, siteId));
            editingAccount.name = name;
            editingAccount.username = username;
            editingAccount.password = password;
            if (passwordChanged) {
                // 密码变了：旧登录快照作废
                editingAccount.cookies = "";
                editingAccount.localStorageJson = "";
                editingAccount.updatedAt = System.currentTimeMillis();
            }
            AccountStore.updateAccount(this, editingAccount);
            if (checkDefault.isChecked()) {
                AccountStore.setDefaultAccount(this, siteId, editingAccount.id);
            }
            if (passwordChanged && isActive) {
                AccountStore.setPendingLogout(this, siteId, true);
            }
            Toast.makeText(this, passwordChanged ? R.string.password_updated : R.string.account_saved,
                    Toast.LENGTH_LONG).show();
        } else {
            Account account = new Account(AccountStore.newId(), siteId, name, username, password);
            AccountStore.addAccount(this, account);
            if (checkDefault.isChecked()) {
                AccountStore.setDefaultAccount(this, siteId, account.id);
            }
            Toast.makeText(this, R.string.account_saved, Toast.LENGTH_SHORT).show();
        }
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    AccountStore.deleteAccount(this, siteId, accountId);
                    Toast.makeText(this, R.string.account_deleted, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .show();
    }
}