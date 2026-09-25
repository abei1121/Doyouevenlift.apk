package pro.xiaojiucai.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    private static final String TARGET_URL = "https://xiaojiucai.pro";
    private static final String APP_HOST = "xiaojiucai.pro";
    private static final String UA_SUFFIX = " XiaoJiuCaiApp/1.0.0 (Linux; Android)";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupWebView();
        setupBackNavigation();

        if (savedInstanceState == null) {
            webView.loadUrl(TARGET_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipe_refresh);
        webView = findViewById(R.id.webview);

        swipeRefresh.setColorSchemeResources(R.color.accent, R.color.primary);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.primary);
        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        // 仅在 WebView 处于顶部时允许下拉刷新
        webView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            swipeRefresh.setEnabled(webView.getScrollY() == 0);
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);

        // 注入专属 UA 标识，便于前端识别为独立 App 环境
        String defaultUa = settings.getUserAgentString();
        settings.setUserAgentString(defaultUa + UA_SUFFIX);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new InnerWebViewClient());
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastBackPressTime < 2000) {
                        finish();
                    } else {
                        lastBackPressTime = now;
                        Toast.makeText(MainActivity.this, R.string.press_again_exit, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private class InnerWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            return handleUrl(uri.toString());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(url);
        }

        private boolean handleUrl(String url) {
            if (url == null) return false;

            // 1. 本站域名保持在 WebView 内部浏览
            if (url.startsWith("https://" + APP_HOST) || url.startsWith("http://" + APP_HOST)) {
                return false;
            }

            // 2. 外部 Scheme (AI 原生唤醒如 deepseek://, chatgpt://, 社交如 tg://, intent://)
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                try {
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    startActivity(intent);
                    return true;
                } catch (URISyntaxException | ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "未检测到对应客户端", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }

            // 3. 站外网页链接通过系统外部浏览器打开
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            } catch (Exception ignored) {}
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            swipeRefresh.setRefreshing(false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }
}
