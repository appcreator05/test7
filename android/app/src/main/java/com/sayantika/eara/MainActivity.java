package com.sayantika.eara;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ClientCertRequest;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String FORCED_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/133.0.0.0 Mobile Safari/537.36";
    public static final String TARGET_URL = "https://hdskay.blogspot.com";
    public static final String YOUTUBE_INAPP_URL = "file:///android_asset/youtube_movie_section.html";
    private static final int PERMISSION_REQUEST_CODE = 2001;
    private static final int STORAGE_DOWNLOAD_REQUEST_CODE = 3001;

    private WebView webView;
    private ProgressBar progressBar;
    private RelativeLayout rootLayout;

    // In-App YouTube Ultimate Movie Section
    private RelativeLayout youtubeSectionLayout = null;
    private WebView youtubeWebView = null;
    private ProgressBar youtubeProgressBar = null;
    private Button watchMovieFloatingBtn = null;

    // Splash overlay view
    private FrameLayout splashOverlay;

    // Video Fullscreen containers & state
    private FrameLayout fullscreenContainer;
    private View customVideoView = null;
    private WebChromeClient.CustomViewCallback customViewCallback = null;
    private boolean isVideoFullscreen = false;

    private RelativeLayout noInternetLayout;
    private boolean isOffline = false;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Immersive True Fullscreen Mode (Hide System Bottom Navigation Bar & Status Bar)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        hideSystemUI();

        // 2. Hardware Sound & Audio Control
        try {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
        } catch (Exception ignored) {}

        // 3. Build Layout Hierarchy Dynamically (No Ads)
        rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(0xFF0F172A);
        setContentView(rootLayout);

        // 4. Fullscreen Video Container (above webView)
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);
        RelativeLayout.LayoutParams fsParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        rootLayout.addView(fullscreenContainer, fsParams);

        // 5. Initialize WebView & Progress Bar
        setupWebView();
        setupProgressBar();

        // 5.1 Initialize Floating "Watch Ultimate Movie" Button
        setupWatchMovieFloatingButton();

        // 6. Build Offline "No Internet" Dialog (English UI)
        buildNoInternetPopup();

        // 7. Setup Native Splash Screen Overlay
        setupSplashScreen();

        // 8. Request Required App Permissions
        checkAndRequestAppPermissions();

        // 9. Register Real-Time Network Connectivity Listener
        setupNetworkMonitoring();

        // 10. Deep Link Handling & Initial Page Load
        handleDeepLink(getIntent());
        if (webView.getUrl() == null || webView.getUrl().isEmpty()) {
            if (isNetworkAvailable()) {
                webView.loadUrl(TARGET_URL);
            } else {
                showNoInternetPopup();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    /**
     * Handles deep linking from web URLs or custom app scheme (movieapp://).
     */
    private void handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        String scheme = data.getScheme();

        if ("movieapp".equalsIgnoreCase(scheme)) {
            String queryUrl = data.getQueryParameter("url");
            if (queryUrl != null && !queryUrl.isEmpty()) {
                if (webView != null) webView.loadUrl(queryUrl);
            } else {
                String host = data.getHost();
                String path = data.getPath();
                if (host != null) {
                    String fullUrl = "https://" + host + (path != null ? path : "");
                    if (webView != null) webView.loadUrl(fullUrl);
                }
            }
        } else if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (webView != null) {
                webView.loadUrl(data.toString());
            }
        }
    }

    /**
     * Checks and requests user permissions for location, camera, storage, audio, etc.
     */
    private void checkAndRequestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsToRequest = new ArrayList<>();

            String[] desiredPermissions = {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS
            };

            for (String perm : desiredPermissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(perm);
                }
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }

            if (!permissionsToRequest.isEmpty()) {
                requestPermissions(permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            Log.d("Permissions", "Hardware and system permissions processed.");
        } else if (requestCode == STORAGE_DOWNLOAD_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted. Please tap download again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage permission required to download files.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Automatic Download Handler:
     * Seamlessly routes file downloads (apk, mp4, pdf, zip, etc.) to Android DownloadManager
     * with system notifications and persistent storage.
     */
    private void handleDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            if (url == null || url.trim().isEmpty()) return;

            // Storage permission check for Android 9 (Pie) and older
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_DOWNLOAD_REQUEST_CODE);
                    Toast.makeText(this, "Storage permission required for download", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }

            // Sync cookies & headers for authenticated/session downloads
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("cookie", cookies);
            }
            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }

            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setDescription("Downloading file: " + filename);
            request.setTitle(filename);
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this, "Downloading " + filename + "...", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e("DownloadManager", "Error initiating download: " + e.getMessage());
            try {
                // Fallback to external browser/downloader
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Builds and displays the native splash screen overlay.
     */
    private void setupSplashScreen() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Color.BLACK);
        RelativeLayout.LayoutParams splashParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        splashOverlay.setLayoutParams(splashParams);

        // Fullscreen Cinematic Splash Image
        ImageView splashBg = new ImageView(this);
        splashBg.setImageResource(R.drawable.splash);
        splashBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splashOverlay.addView(splashBg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        rootLayout.addView(splashOverlay);
        splashOverlay.bringToFront();

        // Dismiss splash after 2.5s with smooth fade
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (splashOverlay != null && splashOverlay.getVisibility() == View.VISIBLE) {
                splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction(() -> {
                            if (splashOverlay != null) {
                                splashOverlay.setVisibility(View.GONE);
                            }
                        });
            }
        }, 2500);
    }

    /**
     * Direct navigation when clicking posts: Ad-free instant page loading.
     */
    public void handlePostClick(final String targetUrl) {
        runOnUiThread(() -> {
            if (targetUrl == null || targetUrl.trim().isEmpty()) return;
            if (webView != null) {
                webView.loadUrl(targetUrl);
            }
        });
    }

    /**
     * Floating Action Button for opening YouTube Ultimate Movie Section.
     * Hidden as user has a custom in-page button to open YouTube section.
     */
    private void setupWatchMovieFloatingButton() {
        // Disabled and hidden per user requirement (display none)
        watchMovieFloatingBtn = null;
    }

    public void openYouTubeSection() {
        runOnUiThread(() -> {
            showInAppYouTubeSection();
        });
    }

    private void showInAppYouTubeSection() {
        if (youtubeSectionLayout == null) {
            setupInAppYouTubeSection();
        }

        if (watchMovieFloatingBtn != null) {
            watchMovieFloatingBtn.setVisibility(View.GONE);
        }

        if (youtubeSectionLayout != null) {
            youtubeSectionLayout.setVisibility(View.VISIBLE);
            youtubeSectionLayout.bringToFront();
            if (youtubeWebView != null) {
                youtubeWebView.onResume();
                String current = youtubeWebView.getUrl();
                if (current == null || current.isEmpty() || current.equals("about:blank")) {
                    loadInAppYouTubeContent();
                }
            }
        }
    }

    /**
     * Loads the YouTube Movie Section HTML with a legitimate HTTPS Base URL (https://hdskay.blogspot.com/)
     * and valid Origin/Referer headers. This completely eliminates YouTube Error 153 (Video Player Configuration Error)
     * which happens when YouTube rejects file:/// or null origin headers.
     */
    private void loadInAppYouTubeContent() {
        if (youtubeWebView == null) return;
        try {
            java.io.InputStream is = getAssets().open("youtube_movie_section.html");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String html = new String(buffer, StandardCharsets.UTF_8);
            youtubeWebView.loadDataWithBaseURL("https://hdskay.blogspot.com/", html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            Log.e("InAppYouTube", "Error reading asset, fallback to loadUrl: " + e.getMessage());
            youtubeWebView.loadUrl("file:///android_asset/youtube_movie_section.html");
        }
    }

    public void hideInAppYouTubeSection() {
        if (youtubeSectionLayout != null) {
            youtubeSectionLayout.setVisibility(View.GONE);
        }
        if (youtubeWebView != null) {
            youtubeWebView.onPause();
        }
        hideSystemUI();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupInAppYouTubeSection() {
        youtubeSectionLayout = new RelativeLayout(this);
        youtubeSectionLayout.setBackgroundColor(0xFF000000);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        youtubeSectionLayout.setLayoutParams(layoutParams);

        youtubeWebView = new WebView(this);
        RelativeLayout.LayoutParams webParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        youtubeWebView.setLayoutParams(webParams);
        youtubeSectionLayout.addView(youtubeWebView);

        youtubeProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        youtubeProgressBar.setMax(100);
        youtubeProgressBar.setProgress(0);
        youtubeProgressBar.setVisibility(View.GONE);
        RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (4 * getResources().getDisplayMetrics().density)
        );
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        youtubeProgressBar.setLayoutParams(pbParams);
        youtubeSectionLayout.addView(youtubeProgressBar);

        // Third-party cookies & Data store setup for In-App YouTube Section
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(youtubeWebView, true);
        }

        // Bridge for In-App YouTube WebView
        Object inAppBridge = new Object() {
            @JavascriptInterface
            public void closeYouTubeSection() {
                runOnUiThread(() -> {
                    hideInAppYouTubeSection();
                });
            }

            @JavascriptInterface
            public void hideYouTubeSection() {
                runOnUiThread(() -> {
                    hideInAppYouTubeSection();
                });
            }

            @JavascriptInterface
            public void requestLandscapeFullscreen() {
                runOnUiThread(() -> {
                    isVideoFullscreen = true;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    hideSystemUI();
                });
            }

            @JavascriptInterface
            public void requestPortraitOrientation() {
                runOnUiThread(() -> {
                    isVideoFullscreen = false;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    hideSystemUI();
                });
            }

            @JavascriptInterface
            public void requestPortrait() {
                requestPortraitOrientation();
            }

            @JavascriptInterface
            public void setVideoFullscreen(final boolean isFs) {
                runOnUiThread(() -> {
                    isVideoFullscreen = isFs;
                    if (isFs) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                    hideSystemUI();
                });
            }

            @JavascriptInterface
            public void openExternalUrl(String url) {
                runOnUiThread(() -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                });
            }

            @JavascriptInterface
            public String searchYouTubeSync(String query) {
                try {
                    return YouTubeSearchHelper.search(query);
                } catch (Exception e) {
                    return "{\"success\":false,\"videos\":[]}";
                }
            }

            @JavascriptInterface
            public void searchYouTubeAsync(String query, String callbackName) {
                new Thread(() -> {
                    String json = YouTubeSearchHelper.search(query);
                    runOnUiThread(() -> {
                        if (youtubeWebView != null && callbackName != null) {
                            String escaped = json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
                            youtubeWebView.evaluateJavascript("if (typeof " + callbackName + " === 'function') { " + callbackName + "('" + escaped + "'); }", null);
                        }
                    });
                }).start();
            }

            @JavascriptInterface
            public String loadMoreYouTubeSync(String token, String apiKey, String clientVersion) {
                try {
                    return YouTubeSearchHelper.loadMore(token, apiKey, clientVersion);
                } catch (Exception e) {
                    return "{\"success\":false,\"videos\":[]}";
                }
            }

            @JavascriptInterface
            public void loadMoreYouTubeAsync(String token, String apiKey, String clientVersion, String callbackName) {
                new Thread(() -> {
                    String json = YouTubeSearchHelper.loadMore(token, apiKey, clientVersion);
                    runOnUiThread(() -> {
                        if (youtubeWebView != null && callbackName != null) {
                            String escaped = json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
                            youtubeWebView.evaluateJavascript("if (typeof " + callbackName + " === 'function') { " + callbackName + "('" + escaped + "'); }", null);
                        }
                    });
                }).start();
            }

            @JavascriptInterface
            public void openSubscriptionPage() {
                runOnUiThread(() -> {
                    hideInAppYouTubeSection();
                    if (webView != null) {
                        webView.getSettings().setUserAgentString(FORCED_USER_AGENT);
                        webView.loadUrl("https://hdskay.blogspot.com/p/suscription-page.html");
                    }
                });
            }
        };
        youtubeWebView.addJavascriptInterface(inAppBridge, "AndroidStartApp");
        youtubeWebView.addJavascriptInterface(inAppBridge, "Android");

        WebSettings ws = youtubeWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
        ws.setUserAgentString(FORCED_USER_AGENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        youtubeWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    Uri reqUri = request.getUrl();
                    String path = reqUri.getPath();
                    if (path != null && path.contains("/api/youtube/search")) {
                        String q = reqUri.getQueryParameter("query");
                        String json = YouTubeSearchHelper.search(q != null ? q : "");
                        return new WebResourceResponse("application/json", "UTF-8", new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                    } else if (path != null && path.contains("/api/youtube/more")) {
                        String token = reqUri.getQueryParameter("token");
                        String apiKey = reqUri.getQueryParameter("apiKey");
                        String ver = reqUri.getQueryParameter("clientVersion");
                        String json = YouTubeSearchHelper.loadMore(token, apiKey, ver);
                        return new WebResourceResponse("application/json", "UTF-8", new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return handleYtUrl(request.getUrl().toString());
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleYtUrl(url);
            }

            private boolean handleYtUrl(String url) {
                if (url == null) return false;
                if (url.startsWith("vnd.youtube:") || url.contains("youtube.com/watch") || url.contains("youtu.be/")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception ignored) {}
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (youtubeProgressBar != null) {
                    youtubeProgressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (youtubeProgressBar != null) {
                    youtubeProgressBar.setVisibility(View.GONE);
                }
            }
        });

        youtubeWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (youtubeProgressBar != null) {
                    youtubeProgressBar.setProgress(newProgress);
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        request.grant(request.getResources());
                    }
                });
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    onHideCustomView();
                    return;
                }
                customVideoView = view;
                customViewCallback = callback;
                isVideoFullscreen = true;

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

                if (fullscreenContainer != null) {
                    fullscreenContainer.removeAllViews();
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                    );
                    lp.setMargins(0, 0, 0, 0);
                    customVideoView.setLayoutParams(lp);
                    fullscreenContainer.addView(customVideoView, lp);
                    addFullscreenUntouchOverlays();
                    fullscreenContainer.setVisibility(View.VISIBLE);
                    fullscreenContainer.bringToFront();
                }

                hideSystemUI();
                if (getWindow() != null && getWindow().getDecorView() != null) {
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 100);
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 300);
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 700);
                }
            }

            @Override
            public void onHideCustomView() {
                if (customVideoView == null) return;
                if (fullscreenContainer != null) {
                    fullscreenContainer.removeAllViews();
                    fullscreenContainer.setVisibility(View.GONE);
                }
                customVideoView = null;
                isVideoFullscreen = false;
                if (customViewCallback != null) {
                    try {
                        customViewCallback.onCustomViewHidden();
                    } catch (Exception ignored) {}
                    customViewCallback = null;
                }

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                hideSystemUI();
                if (getWindow() != null && getWindow().getDecorView() != null) {
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 100);
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 300);
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 700);
                }
            }
        });

        rootLayout.addView(youtubeSectionLayout);
        youtubeSectionLayout.setVisibility(View.GONE);
    }

    private void injectBridgeAndListeners(WebView view) {
        if (view == null) return;
        String js = "(function() {" +
                "  function initBridge() {" +
                "    window.openYouTubeMovieSection = function() {" +
                "      if (window.AndroidStartApp && typeof window.AndroidStartApp.openYouTubeMovieSection === 'function') {" +
                "        window.AndroidStartApp.openYouTubeMovieSection();" +
                "      } else if (window.Android && typeof window.Android.openYouTubeMovieSection === 'function') {" +
                "        window.Android.openYouTubeMovieSection();" +
                "      }" +
                "    };" +
                "    if (!window.__hdskay_msg_listener_set) {" +
                "      window.__hdskay_msg_listener_set = true;" +
                "      window.addEventListener('message', function(e) {" +
                "        var d = e.data;" +
                "        if (typeof d === 'string') { try { d = JSON.parse(d); } catch(err) {} }" +
                "        if (d === 'HDSKAY_OPEN_YOUTUBE_SECTION' || (d && (d.type === 'HDSKAY_OPEN_YOUTUBE_SECTION' || d.action === 'openYouTubeMovieSection'))) {" +
                "          if (typeof window.openYouTubeMovieSection === 'function') window.openYouTubeMovieSection();" +
                "        }" +
                "      }, true);" +
                "    }" +
                "    if (!window.__hdskay_yt_click_set) {" +
                "      window.__hdskay_yt_click_set = true;" +
                "      document.addEventListener('click', function(e) {" +
                "        var el = e.target && e.target.closest ? e.target.closest('button, a, div, span') : null;" +
                "        if (!el) return;" +
                "        var oc = el.getAttribute('onclick') || '';" +
                "        var txt = el.textContent || '';" +
                "        var id = el.id || '';" +
                "        var dataAction = el.getAttribute('data-action') || '';" +
                "        if (oc.indexOf('openYouTubeMovieSection') !== -1 || txt.indexOf('Watch Ultimate Movie') !== -1 || id === 'watch-ultimate-movie-btn' || dataAction === 'openYouTubeMovieSection') {" +
                "          e.preventDefault();" +
                "          e.stopPropagation();" +
                "          if (typeof window.openYouTubeMovieSection === 'function') window.openYouTubeMovieSection();" +
                "        }" +
                "      }, true);" +
                "    }" +
                "  }" +
                "  initBridge();" +
                "  if (document.readyState === 'loading') {" +
                "    document.addEventListener('DOMContentLoaded', initBridge);" +
                "  }" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    /**
     * Ensures that navigator.userAgent always satisfies the website's check:
     * (ua.includes("wv") && ua.includes("Version/4.0") && ua.includes("AppleWebKit/537.36") && ua.includes("Chrome/"))
     * while also hooking Razorpay to enable webview_intent and UPI intent flow seamlessly.
     */
    private void injectUpiAndUaHook(WebView view) {
        if (view == null) return;
        String js = "(function() {" +
                "  try {" +
                "    var u = navigator.userAgent || '';" +
                "    var extra = '';" +
                "    if (!u.includes('wv')) extra += ' wv';" +
                "    if (!u.includes('Version/4.0')) extra += ' Version/4.0';" +
                "    if (!u.includes('AppleWebKit/537.36')) extra += ' AppleWebKit/537.36';" +
                "    if (!u.includes('Chrome/')) extra += ' Chrome/133.0.0.0';" +
                "    if (extra.length > 0) {" +
                "      var combined = u + extra;" +
                "      try {" +
                "        Object.defineProperty(navigator, 'userAgent', {" +
                "          get: function() { return combined; }," +
                "          configurable: true" +
                "        });" +
                "      } catch(e) {}" +
                "    }" +
                "    if (window.Razorpay && !window.__rzpHooked) {" +
                "      window.__rzpHooked = true;" +
                "      var OrigRzp = window.Razorpay;" +
                "      window.Razorpay = function(opts) {" +
                "        opts = opts || {};" +
                "        opts.webview_intent = true;" +
                "        opts.upi = opts.upi || {};" +
                "        opts.upi.flow = 'intent';" +
                "        return new OrigRzp(opts);" +
                "      };" +
                "      window.Razorpay.prototype = OrigRzp.prototype;" +
                "    }" +
                "  } catch(e) {}" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    /**
     * Initializes the Master WebView with full Browser Capabilities:
     * - Third-party cookies
     * - JavaScript enabled & optimized
     * - Pop-ups & redirects handled smoothly
     * - Sound & unrestricted media playback
     * - Intrusive web ads / site scripts supported without breaking page
     * - Protected content (DRM / Widevine / EME identifier granted)
     * - Auto verify (Client certs & HTTP authentication)
     * - On-device site data (LocalStorage, IndexedDB, WebSQL)
     * - Automatic download (integrated with DownloadManager)
     * - Embedded content (iframes, cross-origin resources, video embeds)
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        RelativeLayout.LayoutParams webParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        webView.setLayoutParams(webParams);
        rootLayout.addView(webView);

        // 1. Third-Party Cookies & Persistent Data Store
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        // 2. Hardware Acceleration for JavaScript Optimization
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 3. WebSettings Configuration
        WebSettings settings = webView.getSettings();

        // JavaScript & Optimization
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        // Pop-up and Redirect Support
        settings.setSupportMultipleWindows(true);

        // Sound / Audio without user gesture restriction
        settings.setMediaPlaybackRequiresUserGesture(false);

        // On-Device Site Data & Data Store (LocalStorage, IndexedDB, Database)
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        String databasePath = getApplicationContext().getDir("databases", Context.MODE_PRIVATE).getPath();
        settings.setDatabasePath(databasePath);

        // Embedded Content & Mixed Content (Allows HTTP sound/video on HTTPS sites, iframes, embeds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Viewport & Scaling
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Cache & Performance
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(FORCED_USER_AGENT);
        settings.setGeolocationEnabled(true);

        // 4. Automatic Download Listener
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            handleDownload(url, userAgent, contentDisposition, mimeType);
        });

        // 5. JavaScript Bridge (No Ads)
        Object jsBridge = new Object() {
            @JavascriptInterface
            public void showRewardedVideoForUrl(final String url) {
                // Direct ad-free instant loading
                handlePostClick(url);
            }

            @JavascriptInterface
            public void openYouTubeMovieSection() {
                openYouTubeSection();
            }

            @JavascriptInterface
            public void hideYouTubeSection() {
                runOnUiThread(() -> {
                    hideInAppYouTubeSection();
                });
            }

            @JavascriptInterface
            public void openCustomTab(final String url) {
                runOnUiThread(() -> {
                    openInCustomTabs(url);
                });
            }

            @JavascriptInterface
            public void setBannerVisibility(final boolean visible) {
                // Ad-free experience: No banner
            }

            @JavascriptInterface
            public void requestLandscapeFullscreen() {
                runOnUiThread(() -> {
                    isVideoFullscreen = true;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    hideSystemUI();
                });
            }

            @JavascriptInterface
            public void requestPortraitOrientation() {
                runOnUiThread(() -> {
                    isVideoFullscreen = false;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    hideSystemUI();
                });
            }

            @JavascriptInterface
            public void requestPortrait() {
                requestPortraitOrientation();
            }

            @JavascriptInterface
            public void setVideoFullscreen(final boolean isFs) {
                runOnUiThread(() -> {
                    isVideoFullscreen = isFs;
                    if (isFs) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                    hideSystemUI();
                    if (getWindow() != null && getWindow().getDecorView() != null) {
                        getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 150);
                        getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 500);
                    }
                });
            }

            @JavascriptInterface
            public void openSubscriptionPage() {
                runOnUiThread(() -> {
                    hideInAppYouTubeSection();
                    if (webView != null) {
                        webView.getSettings().setUserAgentString(FORCED_USER_AGENT);
                        webView.loadUrl("https://hdskay.blogspot.com/p/suscription-page.html");
                    }
                });
            }
        };

        webView.addJavascriptInterface(jsBridge, "AndroidStartApp");
        webView.addJavascriptInterface(jsBridge, "Android");

        // 6. WebChromeClient (Pop-ups, Protected Content, Fullscreen Video)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            /**
             * Protected Content & Hardware Permissions:
             * Explicitly grants RESOURCE_PROTECTED_MEDIA_ID for DRM/Widevine/EME protected streams.
             */
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        request.grant(request.getResources());
                    }
                });
            }

            /**
             * Pop-up & Multi-window Support:
             * Seamlessly creates a visible modal popup Dialog for payment gateways (Razorpay, 3D Secure, Netbanking, OTP)
             * with a dedicated Close button, full cookies/JS, and UPI deep-linking support.
             */
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                final Dialog popupDialog = new Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                
                RelativeLayout popupRoot = new RelativeLayout(MainActivity.this);
                popupRoot.setBackgroundColor(0xFF0F172A);

                // Header bar with Close button
                RelativeLayout popupHeader = new RelativeLayout(MainActivity.this);
                popupHeader.setId(View.generateViewId());
                popupHeader.setBackgroundColor(0xFF1E293B);
                int headerHeight = (int) (52 * getResources().getDisplayMetrics().density);
                RelativeLayout.LayoutParams headerParams = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        headerHeight
                );
                popupHeader.setLayoutParams(headerParams);

                TextView popupTitle = new TextView(MainActivity.this);
                popupTitle.setText("Secure Checkout & Verification");
                popupTitle.setTextColor(Color.WHITE);
                popupTitle.setTextSize(15f);
                popupTitle.setTypeface(Typeface.DEFAULT_BOLD);
                RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                titleParams.addRule(RelativeLayout.CENTER_VERTICAL);
                titleParams.leftMargin = (int) (16 * getResources().getDisplayMetrics().density);
                popupTitle.setLayoutParams(titleParams);
                popupHeader.addView(popupTitle);

                Button closeBtn = new Button(MainActivity.this);
                closeBtn.setText("✕ Close");
                closeBtn.setTextColor(Color.WHITE);
                closeBtn.setTextSize(13f);
                closeBtn.setBackgroundColor(0x33FFFFFF);
                RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        (int) (38 * getResources().getDisplayMetrics().density)
                );
                closeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                closeParams.addRule(RelativeLayout.CENTER_VERTICAL);
                closeParams.rightMargin = (int) (12 * getResources().getDisplayMetrics().density);
                closeBtn.setLayoutParams(closeParams);
                closeBtn.setOnClickListener(v -> popupDialog.dismiss());
                popupHeader.addView(closeBtn);

                popupRoot.addView(popupHeader);

                // Child WebView inside popup
                WebView popupWebView = new WebView(MainActivity.this);
                RelativeLayout.LayoutParams popupWebParams = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                popupWebParams.addRule(RelativeLayout.BELOW, popupHeader.getId());
                popupWebView.setLayoutParams(popupWebParams);
                popupRoot.addView(popupWebView);

                // Configure popupWebView settings (JS, DOM, cookies, etc.)
                WebSettings ps = popupWebView.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setJavaScriptCanOpenWindowsAutomatically(true);
                ps.setSupportMultipleWindows(true);
                ps.setDomStorageEnabled(true);
                ps.setDatabaseEnabled(true);
                ps.setMediaPlaybackRequiresUserGesture(false);
                ps.setUserAgentString(FORCED_USER_AGENT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true);
                    ps.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                }

                popupWebView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        popupDialog.dismiss();
                    }
                });

                popupWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        if (req != null && req.getUrl() != null) {
                            return handleUrlNavigation(req.getUrl());
                        }
                        return false;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        if (url != null) {
                            return handleUrlNavigation(Uri.parse(url));
                        }
                        return false;
                    }
                });

                popupDialog.setContentView(popupRoot);
                popupDialog.show();

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                super.onCloseWindow(window);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    onHideCustomView();
                    return;
                }

                customVideoView = view;
                customViewCallback = callback;
                isVideoFullscreen = true;

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

                if (watchMovieFloatingBtn != null) {
                    watchMovieFloatingBtn.setVisibility(View.GONE);
                }

                fullscreenContainer.removeAllViews();
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                );
                lp.setMargins(0, 0, 0, 0);
                customVideoView.setLayoutParams(lp);
                fullscreenContainer.addView(customVideoView, lp);

                // Add Untouch Shields over Fullscreen Video
                addFullscreenUntouchOverlays();

                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.bringToFront();

                hideSystemUI();
                if (getWindow() != null && getWindow().getDecorView() != null) {
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 100);
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 300);
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 700);
                }
            }

            @Override
            public void onHideCustomView() {
                if (customVideoView == null) return;

                try {
                    fullscreenContainer.removeAllViews();
                } catch (Exception ignored) {}
                customVideoView = null;
                fullscreenContainer.setVisibility(View.GONE);
                isVideoFullscreen = false;

                if (customViewCallback != null) {
                    try {
                        customViewCallback.onCustomViewHidden();
                    } catch (Exception ignored) {}
                    customViewCallback = null;
                }

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

                hideSystemUI();
                if (getWindow() != null && getWindow().getDecorView() != null) {
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 100);
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 300);
                    getWindow().getDecorView().postDelayed(MainActivity.this::hideSystemUI, 700);
                }
            }
        });

        // 7. WebViewClient (Auto-verify, Client Certs, Redirects, Seamless Navigation)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return handleUrlNavigation(uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                return handleUrlNavigation(uri);
            }

            /**
             * Auto Verify: Client Certificate support for identity verification.
             */
            @Override
            public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.proceed(null, null);
                } else {
                    super.onReceivedClientCertRequest(view, request);
                }
            }

            /**
             * Auto Verify: HTTP Authentication support.
             */
            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
                handler.proceed("", "");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showNoInternetPopup();
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }
                injectBridgeAndListeners(view);
                injectUpiAndUaHook(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                injectBridgeAndListeners(view);
                injectUpiAndUaHook(view);
            }
        });
    }

    /**
     * Centralized URL & Protocol Navigation:
     * - Automatic Deep Linking for UPI & Payment Apps (Google Pay, PhonePe, Paytm, BHIM, Cred, Navi, etc.)
     * - Android Intent URLs resolution and fallback handling
     * - In-App preservation for payment gateways & 3D Secure bank redirects
     * - Direct internal post navigation (no ads, instantaneous loading)
     * - External web links open in Chrome Custom Tabs
     */
    private boolean handleUrlNavigation(Uri uri) {
        if (uri == null) return false;
        String urlStr = uri.toString();
        String scheme = uri.getScheme();

        // 1. Direct UPI & Payment App Schemes (upi://, tez://, phonepe://, paytmmp://, bhim://, credpay://, gpay://)
        if (isUpiOrPaymentScheme(scheme)) {
            return launchUpiOrExternalApp(uri, urlStr);
        }

        // 2. Android Intent:// Schemes (Used heavily by Razorpay UPI Intent & Bank redirects)
        if ("intent".equalsIgnoreCase(scheme)) {
            return handleIntentScheme(urlStr);
        }

        // 3. Other Non-HTTP external schemes (WhatsApp, Telegram, Tel, Mailto, SMS, Market)
        if (scheme != null && !scheme.startsWith("http") && !scheme.startsWith("https")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Log.e("WebView", "Unable to open external scheme: " + urlStr);
            }
            return true;
        }

        // 4. Direct internal Blogspot navigation (Ad-Free, Instant!)
        String host = uri.getHost();
        if (host != null && (host.contains("hdskay.blogspot.com") || host.contains("blogspot.com"))) {
            return false; // let webView load URL directly
        }

        // 5. Payment Gateways & Banking Redirects (Keep INSIDE WebView so in-app checkout completes!)
        if (isPaymentOrBankUrl(urlStr, host)) {
            return false; // load inside WebView to preserve transaction session
        }

        // 6. External websites -> Open in Chrome Custom Tabs
        openInCustomTabs(urlStr);
        return true;
    }

    private boolean isUpiOrPaymentScheme(String scheme) {
        if (scheme == null) return false;
        String s = scheme.toLowerCase();
        return s.equals("upi") || s.equals("tez") || s.equals("phonepe")
                || s.equals("paytmmp") || s.equals("bhim") || s.equals("credpay")
                || s.equals("gpay") || s.equals("paytm") || s.equals("mobikwik")
                || s.equals("freecharge");
    }

    private boolean launchUpiOrExternalApp(Uri uri, String urlStr) {
        try {
            Intent upiIntent = new Intent(Intent.ACTION_VIEW, uri);
            upiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager pm = getPackageManager();
            if (upiIntent.resolveActivity(pm) != null) {
                startActivity(upiIntent);
                return true;
            }

            // Fallback: If specific scheme (e.g. tez://) is not registered, try generic upi://pay? query
            if (!"upi".equalsIgnoreCase(uri.getScheme()) && uri.getQuery() != null) {
                Uri fallbackUpiUri = Uri.parse("upi://pay?" + uri.getQuery());
                Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, fallbackUpiUri);
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Intent chooser = Intent.createChooser(fallbackIntent, "Pay with UPI");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
                return true;
            }

            // Create chooser for all installed UPI apps
            Intent chooser = Intent.createChooser(upiIntent, "Pay with UPI");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);
            return true;
        } catch (Exception e) {
            Log.e("UPI", "Error launching UPI app: " + e.getMessage());
            Toast.makeText(this, "Please select an installed UPI app (Google Pay, PhonePe, Paytm)", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private boolean handleIntentScheme(String urlStr) {
        try {
            Intent intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME);
            if (intent != null) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                PackageManager pm = getPackageManager();
                if (intent.resolveActivity(pm) != null) {
                    startActivity(intent);
                    return true;
                }

                // Check if underlying data or scheme is UPI
                Uri data = intent.getData();
                if (data != null && "upi".equalsIgnoreCase(data.getScheme())) {
                    Intent genericUpi = new Intent(Intent.ACTION_VIEW, data);
                    genericUpi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Intent chooser = Intent.createChooser(genericUpi, "Pay with UPI");
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (chooser.resolveActivity(pm) != null) {
                        startActivity(chooser);
                        return true;
                    }
                }

                String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                    if (webView != null) webView.loadUrl(fallbackUrl);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e("IntentScheme", "Error handling intent scheme: " + e.getMessage());
        }
        return true;
    }

    private boolean isPaymentOrBankUrl(String url, String host) {
        if (url == null) return false;
        String u = url.toLowerCase();
        String h = host != null ? host.toLowerCase() : "";

        return h.contains("razorpay.com")
                || h.contains("cashfree.com")
                || h.contains("payu.in")
                || h.contains("billdesk.com")
                || h.contains("paytm.com")
                || h.contains("phonepe.com")
                || h.contains("instamojo.com")
                || h.contains("ccavenue.com")
                || h.contains("juspay.in")
                || h.contains("npci.org.in")
                || u.contains("/checkout")
                || u.contains("/payment")
                || u.contains("bank")
                || u.contains("3dsecure")
                || u.contains("otp");
    }

    /**
     * Overlays untouch shields directly over native fullscreen video in fullscreenContainer.
     * Prevents clicking avatar/title/channel/speaker (top-left), link/shelf/logo (bottom corners),
     * and the center recommendation drawer card.
     * Crucial: setFocusable(false) prevents window focus theft.
     */
    private void addFullscreenUntouchOverlays() {
        // Disabled so YouTube player controls (Gear settings, Quality, Volume, CC) remain 100% clickable
    }

    private void setupProgressBar() {
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);

        RelativeLayout.LayoutParams progressParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                10
        );
        progressParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        progressBar.setLayoutParams(progressParams);
        rootLayout.addView(progressBar);
    }

    /**
     * Builds clean English offline recovery dialog.
     */
    private void buildNoInternetPopup() {
        noInternetLayout = new RelativeLayout(this);
        noInternetLayout.setBackgroundColor(0xF00F172A);
        noInternetLayout.setVisibility(View.GONE);

        RelativeLayout.LayoutParams fullParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        noInternetLayout.setLayoutParams(fullParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(48, 48, 48, 48);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1E293B);
        cardBg.setCornerRadius(28f);
        cardBg.setStroke(2, 0xFF334155);
        card.setBackground(cardBg);

        RelativeLayout.LayoutParams cardParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        cardParams.setMargins(40, 40, 40, 40);
        card.setLayoutParams(cardParams);

        // Warning Icon
        TextView iconView = new TextView(this);
        iconView.setText("📶");
        iconView.setTextSize(48);
        iconView.setGravity(Gravity.CENTER);
        card.addView(iconView);

        // Header Title
        TextView titleView = new TextView(this);
        titleView.setText("No Internet Connection");
        titleView.setTextColor(0xFFF8FAFC);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = 20;
        titleView.setLayoutParams(titleParams);
        card.addView(titleView);

        // Subtitle message
        TextView msgView = new TextView(this);
        msgView.setText("Please check your Wi-Fi or mobile data network to continue enjoying high-speed streaming.");
        msgView.setTextColor(0xFF94A3B8);
        msgView.setTextSize(14);
        msgView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        msgParams.topMargin = 12;
        msgParams.bottomMargin = 28;
        msgView.setLayoutParams(msgParams);
        card.addView(msgView);

        // Action Buttons Row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        // Retry Button
        Button retryBtn = new Button(this);
        retryBtn.setText("Try Again");
        retryBtn.setTextColor(Color.WHITE);
        retryBtn.setTextSize(14);
        retryBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable retryBg = new GradientDrawable();
        retryBg.setColor(0xFF2563EB);
        retryBg.setCornerRadius(18f);
        retryBtn.setBackground(retryBg);
        retryBtn.setPadding(32, 16, 32, 16);
        retryBtn.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                hideNoInternetPopup();
                if (webView != null) {
                    String currentUrl = webView.getUrl();
                    if (currentUrl == null || currentUrl.isEmpty() || currentUrl.equals("about:blank")) {
                        webView.loadUrl(TARGET_URL);
                    }
                }
            } else {
                Toast.makeText(this, "Still offline. Please connect to internet.", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(retryBtn);

        // Network Settings Button
        Button settingsBtn = new Button(this);
        settingsBtn.setText("Open Settings");
        settingsBtn.setTextColor(0xFFCBD5E1);
        settingsBtn.setTextSize(14);
        GradientDrawable settingsBg = new GradientDrawable();
        settingsBg.setColor(0xFF334155);
        settingsBg.setCornerRadius(18f);
        settingsBtn.setBackground(settingsBg);
        settingsBtn.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        setParams.leftMargin = 16;
        settingsBtn.setLayoutParams(setParams);
        settingsBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
        });
        btnRow.addView(settingsBtn);

        card.addView(btnRow);
        noInternetLayout.addView(card);
        rootLayout.addView(noInternetLayout);
    }

    private void showNoInternetPopup() {
        isOffline = true;
        runOnUiThread(() -> {
            if (noInternetLayout != null) {
                noInternetLayout.setVisibility(View.VISIBLE);
                noInternetLayout.bringToFront();
            }
        });
    }

    private void hideNoInternetPopup() {
        isOffline = false;
        runOnUiThread(() -> {
            if (noInternetLayout != null) {
                noInternetLayout.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Checks if active network connectivity is currently available.
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            );
        } else {
            android.net.NetworkInfo activeInfo = cm.getActiveNetworkInfo();
            return activeInfo != null && activeInfo.isConnected();
        }
    }

    /**
     * Sets up real-time connectivity listener for dynamic offline/online UI transitions.
     */
    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    runOnUiThread(() -> {
                        if (isOffline) {
                            hideNoInternetPopup();
                            if (webView != null) {
                                String currentUrl = webView.getUrl();
                                if (currentUrl == null || currentUrl.isEmpty() || currentUrl.equals("about:blank")) {
                                    webView.loadUrl(TARGET_URL);
                                }
                            }
                        }
                    });
                }

                @Override
                public void onLost(@NonNull Network network) {
                    runOnUiThread(() -> {
                        showNoInternetPopup();
                    });
                }
            };

            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e("Connectivity", "Error registering network callback: " + e.getMessage());
            }
        }
    }

    /**
     * Opens external links in Chrome Custom Tabs with dark luxury aesthetic.
     */
    private void openInCustomTabs(String url) {
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabColorSchemeParams darkParams = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(0xFF0F172A)
                    .setNavigationBarColor(0xFF0F172A)
                    .build();
            builder.setDefaultColorSchemeParams(darkParams);
            builder.setShowTitle(true);

            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(this, Uri.parse(url));
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        if (customVideoView != null) {
            if (webView != null && webView.getWebChromeClient() != null) {
                webView.getWebChromeClient().onHideCustomView();
            }
            return;
        }

        if (youtubeSectionLayout != null && youtubeSectionLayout.getVisibility() == View.VISIBLE) {
            if (youtubeWebView != null) {
                youtubeWebView.evaluateJavascript(
                    "if (typeof handleBackPressed === 'function') { handleBackPressed(); } else { if (window.AndroidStartApp) AndroidStartApp.hideYouTubeSection(); }",
                    null
                );
            } else {
                hideInAppYouTubeSection();
            }
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Immersive Sticky Fullscreen: Hides system navigation bar and status bar completely.
     */
    private void hideSystemUI() {
        runOnUiThread(() -> {
            try {
                Window win = getWindow();
                if (win == null) return;

                win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                win.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

                WindowInsetsControllerCompat insetsController =
                        WindowCompat.getInsetsController(win, win.getDecorView());
                if (insetsController != null) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars());
                    insetsController.setSystemBarsBehavior(
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }

                View decorView = win.getDecorView();
                if (decorView != null) {
                    decorView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                    );
                }
            } catch (Exception e) {
                Log.e("Fullscreen", "Error hiding system UI: " + e.getMessage());
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
            if (webView != null) {
                webView.onResume();
                webView.resumeTimers();
            }
            if (youtubeWebView != null) {
                youtubeWebView.onResume();
                youtubeWebView.resumeTimers();
            }
            if (customVideoView != null) {
                customVideoView.requestLayout();
            }
            if (fullscreenContainer != null && fullscreenContainer.getVisibility() == View.VISIBLE) {
                fullscreenContainer.requestLayout();
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUI();
        if (getWindow() != null && getWindow().getDecorView() != null) {
            getWindow().getDecorView().postDelayed(this::hideSystemUI, 250);
        }
        if (youtubeWebView != null) {
            youtubeWebView.post(() -> {
                youtubeWebView.evaluateJavascript("if (typeof window.scrollTo === 'function') { window.scrollTo(0, 0); }", null);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        if (youtubeWebView != null) {
            youtubeWebView.onResume();
            youtubeWebView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (youtubeWebView != null) {
            youtubeWebView.onPause();
        }
        if (webView != null) {
            webView.onPause();
        }
        // Persist Cookies & Data Store to SQLite on device storage
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        // Persist Cookies & Data Store to disk
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {}

        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        if (youtubeWebView != null) {
            youtubeWebView.destroy();
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
