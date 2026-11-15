package com.octopus.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.List;
import java.util.Locale;

/**
 * Octopus App Shell —— 原生 WebView 壳子（多站点 · 多账号版）。
 *
 * 核心特性：
 *  - 启动加载“默认站点”；站点可增删改，任意切换
 *  - 每个站点可保存多个账号，每个账号有独立的登录快照
 *    （localStorage + Cookie），切换账号时整体保存/恢复，互不干扰
 *  - 账号密码更新后，自动清空该账号的登录态，强制用新密码重新登录
 *  - 沉浸式全屏（edge-to-edge，适配 Android 15/16/17），无浏览器地址栏
 *  - 系统返回键/手势：先网页后退，回到根页面时“再按一次退出”
 *  - 外站链接交给系统浏览器；文件上传、下载、网页权限申请均支持
 */
public class MainActivity extends Activity {

    private static final int RC_FILE_CHOOSER = 2001;
    private static final int RC_STORAGE = 2002;
    private static final int RC_ACCOUNTS = 2003;

    private static final String JS_READ_STORAGE =
            "(function(){try{var o={};for(var i=0;i<localStorage.length;i++){"
                    + "var k=localStorage.key(i);o[k]=localStorage.getItem(k);}"
                    + "return JSON.stringify(o);}catch(e){return '{}';}})()";

    private WebView webView;
    private View splashOverlay;
    private View errorOverlay;
    private ProgressBar topProgress;
    private TextView errorTitleView;
    private TextView errorSubView;
    private ImageButton btnSwitchSite;
    private ValueCallback<Uri[]> pendingFileCallback;
    private DownloadManager downloadManager;
    private long lastExitBackTime = 0L;

    /** 当前显示的站点（决定 WebView 内允许打开的域名）。 */
    private Site currentSite;
    /** 当前使用的账号（null = 该站点未创建账号，使用 WebView 原生登录态）。 */
    private Account currentAccount;
    /** 页面加载完成后需要注入 localStorage 的账号快照。 */
    private Account pendingInject;
    private boolean pendingInjectDone;
    /** 上次加载时记录的“默认站点” id，用于感知默认站点被修改。 */
    private String lastDefaultId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupWindow();
        bindViews();
        setupInsets(findViewById(R.id.root));
        setupWebView();
        registerBackCallback();
        findViewById(R.id.retry).setOnClickListener(v -> {
            showError(false, null, null);
            showSplash(true);
            if (currentSite != null) webView.reload();
        });

        btnSwitchSite.setOnClickListener(v -> showSwitcher());

        // 无站点 → 首次安装引导
        if (SiteStore.getSites(this).isEmpty()) {
            startActivity(new Intent(this, SitesManagerActivity.class));
            finish();
            return;
        }

        Site defaultSite = SiteStore.getDefaultSite(this);
        lastDefaultId = defaultSite == null ? null : defaultSite.id;
        loadSite(defaultSite);
    }

    /* ------------------------------ 窗口 / 沉浸式 ------------------------------ */

    private void setupWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /** 把系统栏 / 刘海 / 输入法高度变成内容内边距，保证网页不被状态栏和键盘遮挡。 */
    private void setupInsets(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                Insets cut = insets.getInsets(WindowInsets.Type.displayCutout());
                v.setPadding(
                        Math.max(bars.left, cut.left),
                        Math.max(bars.top, cut.top),
                        Math.max(bars.right, cut.right),
                        Math.max(Math.max(bars.bottom, cut.bottom), ime.bottom));
            } else {
                v.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    /* ------------------------------ WebView 配置 ------------------------------ */

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage：站点登录 token 存这里
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportMultipleWindows(false);    // target=_blank 在本窗口打开，不弹新窗口
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // 站点可能为 http
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setSaveEnabled(true);

        // 登录态持久化：接受 Cookie（含第三方），WebView 自动按 origin 存进沙盒
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        webView.setWebViewClient(new OctopusWebViewClient());
        webView.setWebChromeClient(new OctopusWebChromeClient());
        webView.setDownloadListener(new OctopusDownloadListener());
    }

    private void bindViews() {
        webView = findViewById(R.id.webview);
        splashOverlay = findViewById(R.id.splash);
        errorOverlay = findViewById(R.id.error_overlay);
        topProgress = findViewById(R.id.progress_bar);
        errorTitleView = findViewById(R.id.error_text);
        errorSubView = findViewById(R.id.error_sub);
        btnSwitchSite = findViewById(R.id.btn_switch_site);
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
    }

    /* ------------------------------ 站点 / 账号切换 ------------------------------ */

    private void loadSite(Site site) {
        loadSiteWithAccount(site, AccountStore.getActiveAccount(this, site.id));
    }

    /** 保存旧账号快照 → 切换到目标站点/账号。 */
    private void loadSiteWithAccount(final Site site, final Account account) {
        final Site oldSite = currentSite;
        final Account oldAccount = currentAccount;
        currentSite = site;
        currentAccount = account;
        captureAccountSnapshot(oldSite, oldAccount, () -> {
            if (account != null) {
                AccountStore.setActiveAccountId(MainActivity.this, site.id, account.id);
                clearCookies(site.url);
                applyCookies(site.url, account.cookies);
                pendingInject = account;
                pendingInjectDone = false;
            } else {
                pendingInject = null;
                pendingInjectDone = false;
            }
            showError(false, null, null);
            showSplash(true);
            topProgress.setVisibility(View.VISIBLE);
            topProgress.setProgress(5);
            btnSwitchSite.setVisibility(View.VISIBLE);
            webView.loadUrl(site.url);
        });
    }

    /** 采集指定账号的登录快照（localStorage + Cookie），失败则保留旧快照。 */
    private void captureAccountSnapshot(final Site site, final Account account, final Runnable onDone) {
        if (account == null || site == null || webView == null || webView.getProgress() < 100) {
            if (onDone != null) onDone.run();
            return;
        }
        webView.evaluateJavascript(JS_READ_STORAGE, value -> {
            String json = decodeJsResult(value);
            String cookies = CookieManager.getInstance().getCookie(site.url);
            boolean hasData = (cookies != null && !cookies.isEmpty()) || !"{}".equals(json);
            if (hasData) {
                account.localStorageJson = json;
                account.cookies = cookies == null ? "" : cookies;
                account.updatedAt = System.currentTimeMillis();
                AccountStore.updateAccount(MainActivity.this, account);
            }
            if (onDone != null) onDone.run();
        });
    }

    private static String decodeJsResult(String raw) {
        if (raw == null || raw.equals("null") || raw.isEmpty()) return "{}";
        try {
            Object o = new JSONTokener(raw).nextValue();
            return o instanceof String ? (String) o : "{}";
        } catch (Exception e) {
            return "{}";
        }
    }

    private void clearCookies(String url) {
        CookieManager cm = CookieManager.getInstance();
        String existing = cm.getCookie(url);
        if (existing != null && !existing.isEmpty()) {
            for (String part : existing.split(";")) {
                String name = part.split("=", 2)[0].trim();
                if (!name.isEmpty()) {
                    cm.setCookie(url, name + "=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/");
                }
            }
        }
        cm.flush();
    }

    private void applyCookies(String url, String cookies) {
        if (cookies == null || cookies.isEmpty()) return;
        CookieManager cm = CookieManager.getInstance();
        for (String part : cookies.split(";")) {
            String p = part.trim();
            if (p.contains("=")) cm.setCookie(url, p);
        }
        cm.flush();
    }

    private void injectLocalStorage(String json) {
        String payload = "{}";
        if (json != null && !json.trim().isEmpty()) {
            try {
                new JSONObject(json);
                payload = json;
            } catch (Exception ignored) {
            }
        }
        String js = "(function(){try{localStorage.clear();var d=" + payload
                + ";for(var k in d){if(Object.prototype.hasOwnProperty.call(d,k)){"
                + "localStorage.setItem(k,d[k]);}}}catch(e){}})()";
        webView.evaluateJavascript(js, null);
    }

    private boolean isCurrentSiteUrl(String url) {
        if (url == null || currentSite == null) return false;
        try {
            String h1 = Uri.parse(url).getHost();
            String h2 = Uri.parse(currentSite.url).getHost();
            return h1 != null && h2 != null && h1.equalsIgnoreCase(h2);
        } catch (Exception e) {
            return false;
        }
    }

    /** 左下角按钮：站点切换 + 当前站点账号切换 + 管理入口。 */
    private void showSwitcher() {
        final List<Site> sites = SiteStore.getSites(this);
        if (sites.isEmpty()) {
            startActivity(new Intent(this, SitesManagerActivity.class));
            return;
        }
        String[] names = new String[sites.size()];
        for (int i = 0; i < sites.size(); i++) {
            Site s = sites.get(i);
            Account active = AccountStore.getActiveAccount(this, s.id);
            String accountPart = active != null ? " · " + active.name : "";
            names[i] = s.name + (s.isDefault ? "（默认）" : "") + accountPart;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.switch_site)
                .setItems(names, (dialog, which) -> {
                    Site s = sites.get(which);
                    if (currentSite != null && currentSite.id.equals(s.id)) return;
                    loadSite(s);
                    Toast.makeText(this,
                            getString(R.string.site_switched, s.name), Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.switch_account, (d, w) -> openAccounts(currentSite))
                .setPositiveButton(R.string.manage_sites,
                        (d, w) -> startActivity(new Intent(this, SitesManagerActivity.class)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openAccounts(Site site) {
        if (site == null) return;
        Intent intent = new Intent(this, AccountsManagerActivity.class);
        intent.putExtra(AccountsManagerActivity.EXTRA_SITE_ID, site.id);
        intent.putExtra(AccountsManagerActivity.EXTRA_SITE_NAME, site.name);
        startActivityForResult(intent, RC_ACCOUNTS);
    }

    /* ------------------------------ 返回键 / 手势 ------------------------------ */

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastExitBackTime <= 2000L) {
            finish();
        } else {
            lastExitBackTime = now;
            Toast.makeText(this, R.string.exit_tip, Toast.LENGTH_SHORT).show();
        }
    }

    /* ------------------------------ 加载状态 / 错误页 ------------------------------ */

    private void showSplash(boolean show) {
        splashOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(boolean show, String title, String sub) {
        if (show) {
            if (title != null) errorTitleView.setText(title);
            if (sub != null) errorSubView.setText(sub);
            errorOverlay.setVisibility(View.VISIBLE);
            splashOverlay.setVisibility(View.GONE);
        } else {
            errorOverlay.setVisibility(View.GONE);
        }
    }

    /** 只允许当前站点（及其子域）在 WebView 内打开，其余 http(s)/scheme 交给系统。 */
    private boolean handleExternalNavigation(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            String host = Uri.parse(url).getHost();
            if (host != null && currentSite != null) {
                String currentHost = currentSiteHost();
                if (!currentHost.isEmpty()
                        && (host.equals(currentHost) || host.endsWith("." + currentHost))) {
                    return false;
                }
            }
        }
        openWithSystem(url);
        return true;
    }

    private String currentSiteHost() {
        try {
            String h = Uri.parse(currentSite.url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private void openWithSystem(String url) {
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, getString(R.string.open_browser)));
            } catch (Exception ex) {
                Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /* ------------------------------ 文件上传/下载 ------------------------------ */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_ACCOUNTS && resultCode == RESULT_OK && currentSite != null) {
            Account active = AccountStore.getActiveAccount(this, currentSite.id);
            if (active != null) {
                loadSiteWithAccount(currentSite, active);
                Toast.makeText(this, getString(R.string.account_switched, active.name),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == RC_FILE_CHOOSER && pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            pendingFileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.retry_download, Toast.LENGTH_SHORT).show();
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "octopus-download";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (name.isEmpty()) name = "octopus-download";
        if (name.length() > 120) name = name.substring(0, 120);
        return name;
    }

    private static String fileNameFrom(String contentDisposition, String url) {
        if (contentDisposition != null) {
            String lower = contentDisposition.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf("filename=");
            if (idx >= 0) {
                String name = contentDisposition.substring(idx + 9).trim();
                if (name.startsWith("\"")) {
                    int end = name.indexOf('"', 1);
                    name = end > 0 ? name.substring(1, end) : name.substring(1);
                }
                return sanitizeFileName(name);
            }
        }
        String path = Uri.parse(url).getLastPathSegment();
        return sanitizeFileName(path);
    }

    /* ------------------------------ 生命周期：登录态落盘 ------------------------------ */

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();

        Site def = SiteStore.getDefaultSite(this);
        if (def == null) {
            startActivity(new Intent(this, SitesManagerActivity.class));
            finish();
            return;
        }

        if (currentSite == null || findSiteById(currentSite.id) == null) {
            lastDefaultId = def.id;
            loadSite(def);
            return;
        }

        boolean defaultChanged = !def.id.equals(lastDefaultId);
        if (defaultChanged && !currentSite.id.equals(def.id)) {
            lastDefaultId = def.id;
            loadSite(def);
            return;
        }
        lastDefaultId = def.id;

        // 密码已更新：清空该站点登录态，强制用新密码重新登录
        if (AccountStore.isPendingLogout(this, currentSite.id)) {
            AccountStore.setPendingLogout(this, currentSite.id, false);
            forceLogoutAndReload();
            return;
        }

        // 账号被切换（例如在站点编辑页 / 底部按钮进入账号管理切换）：应用新账号
        Account active = AccountStore.getActiveAccount(this, currentSite.id);
        if (active == null && currentAccount != null) {
            // 该站点的账号被全部删除：回到 WebView 原生登录态
            loadSiteWithAccount(currentSite, null);
        } else if (active != null && (currentAccount == null || !active.id.equals(currentAccount.id))) {
            loadSiteWithAccount(currentSite, active);
        }
    }

    private void forceLogoutAndReload() {
        if (currentSite == null || webView == null) return;
        Account account = AccountStore.getActiveAccount(this, currentSite.id);
        if (account != null) {
            account.cookies = "";
            account.localStorageJson = "";
            account.updatedAt = System.currentTimeMillis();
            AccountStore.updateAccount(this, account);
        }
        currentAccount = account;
        pendingInject = null;
        pendingInjectDone = false;
        clearCookies(currentSite.url);
        webView.evaluateJavascript("try{localStorage.clear();}catch(e){}", null);
        showSplash(true);
        webView.loadUrl(currentSite.url);
        Toast.makeText(this, R.string.password_updated, Toast.LENGTH_LONG).show();
    }

    private Site findSiteById(String id) {
        for (Site s : SiteStore.getSites(this)) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
        captureAccountSnapshot(currentSite, currentAccount, null);
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onStop() {
        super.onStop();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            android.view.ViewGroup parent = (android.view.ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /* ------------------------------ 内部客户端 ------------------------------ */

    private class OctopusWebViewClient extends WebViewClient {
        private boolean firstPageLoaded = false;

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (!firstPageLoaded) {
                showSplash(true);
            }
            topProgress.setVisibility(View.VISIBLE);
            topProgress.setProgress(5);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            firstPageLoaded = true;
            showSplash(false);
            topProgress.setVisibility(View.GONE);

            // 账号快照注入：页面加载完成后写入 localStorage，然后重载让页面读取
            if (pendingInject != null && !pendingInjectDone && isCurrentSiteUrl(url)) {
                pendingInjectDone = true;
                injectLocalStorage(pendingInject.localStorageJson);
                applyCookies(currentSite.url, pendingInject.cookies);
                if (pendingInject.hasSnapshot()) {
                    view.postDelayed(() -> {
                        if (webView != null) webView.reload();
                    }, 150);
                }
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showError(true, getString(R.string.error_title), getString(R.string.error_sub));
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame() && response != null && response.getStatusCode() >= 500) {
                showError(true, getString(R.string.error_title), getString(R.string.error_sub));
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleExternalNavigation(request.getUrl().toString());
        }
    }

    private class OctopusWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress >= 100) {
                topProgress.setVisibility(View.GONE);
            } else {
                topProgress.setVisibility(View.VISIBLE);
                topProgress.setProgress(newProgress);
            }
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            pendingFileCallback = callback;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            String[] mimeTypes = params != null ? params.getAcceptTypes() : null;
            boolean capture = params != null && params.isCaptureEnabled();
            if (capture) {
                intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            } else if (mimeTypes != null && mimeTypes.length > 0 && mimeTypes[0] != null) {
                intent.setType(mimeTypes[0]);
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            } else {
                intent.setType("*/*");
            }
            try {
                startActivityForResult(intent, RC_FILE_CHOOSER);
            } catch (Exception e) {
                pendingFileCallback = null;
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            try {
                request.grant(request.getResources());
            } catch (Exception ignored) {
                // 用户拒绝或设备不支持，忽略即可
            }
        }
    }

    private class OctopusDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimeType, long contentLength) {
            try {
                boolean needPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
                if (needPermission
                        && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            RC_STORAGE);
                    return;
                }
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, fileNameFrom(contentDisposition, url));
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && !cookie.isEmpty()) {
                    request.addRequestHeader("Cookie", cookie);
                }
                if (userAgent != null && !userAgent.isEmpty()) {
                    request.addRequestHeader("User-Agent", userAgent);
                }
                downloadManager.enqueue(request);
                Toast.makeText(MainActivity.this, R.string.download_started, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, R.string.download_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}