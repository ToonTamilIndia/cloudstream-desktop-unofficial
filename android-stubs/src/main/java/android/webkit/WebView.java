package android.webkit;

import android.content.Context;
import android.view.View;

@android.annotation.Stub
public class WebView extends View {
    public static java.util.function.Consumer<String> loadUrlHandler = null;
    public static java.util.function.BiConsumer<WebView, String> loadViewUrlHandler = null;
    private final WebSettings settings = new WebSettings();
    private WebViewClient webViewClient;

    public WebView(Context context) {
        // Silently accept creation
    }

    public WebSettings getSettings() {
        return settings;
    }

    public void loadUrl(String url) {
        // Silently intercept direct WebView usage and pass to the global Playwright resolver!
        if (loadViewUrlHandler != null) {
            loadViewUrlHandler.accept(this, url);
        } else if (loadUrlHandler != null) {
            loadUrlHandler.accept(url);
        }
    }

    public void setWebViewClient(WebViewClient client) {
        this.webViewClient = client;
    }

    public WebViewClient getWebViewClient() { return webViewClient; }

    public void setWebChromeClient(WebChromeClient client) {
        // Silently accept
    }

    public void addJavascriptInterface(Object obj, String interfaceName) {
        // Silently accept
    }

    public void evaluateJavascript(String script, ValueCallback<String> callback) {
        if (callback != null) callback.onReceiveValue(null);
    }

    public void destroy() {}
}
