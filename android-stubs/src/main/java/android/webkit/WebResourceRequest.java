package android.webkit;

@android.annotation.Stub
public interface WebResourceRequest {
    android.net.Uri getUrl();
    String getMethod();
    java.util.Map<String, String> getRequestHeaders();
    boolean isForMainFrame();
    boolean hasGesture();
    boolean isRedirect();
}
