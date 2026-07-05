package android.webkit;

@android.annotation.Stub
public class WebViewClient {
    public WebViewClient() {}
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return null; }
    public void onPageFinished(WebView view, String url) {}
}
