package link.parkerdata.skyhop;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.getcapacitor.BridgeActivity;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/**
 * Skyhop — native shell.
 *
 * This is a real Android app, not a Trusted Web Activity. AdMob may not
 * serve inside a TWA, because a TWA is Chrome showing a website, and the
 * ads would not belong to the app. Here the WebView is ours, so the ad
 * views are ours too.
 *
 * Three formats, arranged around the one thing that matters: the game
 * board is never covered while someone is playing.
 *
 *   Banner       a sibling BELOW the WebView, never an overlay.
 *   Interstitial after every third game over, when the round is over.
 *   App open     only when returning to an app that was already warm.
 */
public class MainActivity extends BridgeActivity {

    private static final String BANNER_ID       = "ca-app-pub-2328685696786328/4325032975";
    private static final String INTERSTITIAL_ID = "ca-app-pub-2328685696786328/7713613647";
    private static final String APP_OPEN_ID     = "ca-app-pub-2328685696786328/8169657641";

    /** No two full-screen ads inside this window, whatever asks for them. */
    private static final long ANY_FULLSCREEN_GAP_MS = 60 * 1000L;

    /** Coming back from a two-second glance at a notification is not a session. */
    private static final long APP_OPEN_COOLDOWN_MS = 4 * 60 * 1000L;

    /** Google requires app-open ads to be no more than four hours old. */
    private static final long APP_OPEN_MAX_AGE_MS = 4 * 60 * 60 * 1000L;

    /** Show an interstitial after every Nth win, not every one. */
    private static final int INTERSTITIAL_EVERY = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private AdView banner;
    private LinearLayout root;

    private InterstitialAd interstitial;
    private boolean interstitialLoading = false;
    private int roundsFinished = 0;

    private AppOpenAd appOpenAd;
    private boolean appOpenLoading = false;
    private long appOpenLoadedAt = 0L;
    private boolean showingFullScreen = false;
    private long lastFullScreenAt = 0L;

    private boolean wentToBackground = false;
    private boolean firstStart = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MobileAds.initialize(this, status -> {
            loadInterstitial();
            loadAppOpen();
        });

        getBridge().getWebView().addJavascriptInterface(new WebBridge(), "GameAds");

        installBannerBelowWebView();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                wentToBackground = true;
            }

            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                // A cold start already shows the splash; an ad on top of it
                // is the thing users uninstall over.
                if (firstStart) {
                    firstStart = false;
                    wentToBackground = false;
                    return;
                }
                if (wentToBackground) {
                    wentToBackground = false;
                    maybeShowAppOpen();
                }
            }
        });
    }

    /**
     * Rebuild the content view as [ WebView (weight 1) | AdView ].
     *
     * An overlaid banner would sit across the bottom of whatever the
     * WebView is showing — including the last row of posters and the
     * player controls. A sibling in a vertical LinearLayout cannot.
     */
    private void installBannerBelowWebView() {
        View webView = getBridge().getWebView();
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent == null) return;
        parent.removeView(webView);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(webView, webParams);

        banner = new AdView(this);
        banner.setAdUnitId(BANNER_ID);
        banner.setAdSize(AdSize.BANNER);
        root.addView(banner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        banner.loadAd(new AdRequest.Builder().build());

        parent.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /* ----------------------------------------------------------------
       The bridge the web app talks to. Every method is defensive: the
       same web files also run in a plain browser, where none of this
       exists, so app.js only calls in when window.MoviesAds is present.
       ---------------------------------------------------------------- */
    private class WebBridge {

        /** The game-over card is up — the round is over, an interstitial may be due. */
        @JavascriptInterface
        public void roundEnded() {
            ui.post(() -> {
                roundsFinished++;
                if (roundsFinished % INTERSTITIAL_EVERY == 0) maybeShowInterstitial();
            });
        }

        /** True when the shell is present at all. Handy for the web side. */
        @JavascriptInterface
        public boolean isApp() {
            return true;
        }
    }

    /* ----------------------------------------------------------------
       Interstitial
       ---------------------------------------------------------------- */

    private void loadInterstitial() {
        if (interstitialLoading || interstitial != null) return;
        interstitialLoading = true;

        InterstitialAd.load(this, INTERSTITIAL_ID, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitial = ad;
                        interstitialLoading = false;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        interstitial = null;
                        interstitialLoading = false;
                    }
                });
    }

    private void maybeShowInterstitial() {
        if (interstitial == null || showingFullScreen) { loadInterstitial(); return; }
        if (System.currentTimeMillis() - lastFullScreenAt < ANY_FULLSCREEN_GAP_MS) return;

        interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                interstitial = null;
                showingFullScreen = false;
                lastFullScreenAt = System.currentTimeMillis();
                loadInterstitial();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                interstitial = null;
                showingFullScreen = false;
                loadInterstitial();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                showingFullScreen = true;
            }
        });

        interstitial.show(this);
    }

    /* ----------------------------------------------------------------
       App open
       ---------------------------------------------------------------- */

    private void loadAppOpen() {
        if (appOpenLoading || isAppOpenReady()) return;
        appOpenLoading = true;

        AppOpenAd.load(this, APP_OPEN_ID, new AdRequest.Builder().build(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        appOpenAd = ad;
                        appOpenLoadedAt = System.currentTimeMillis();
                        appOpenLoading = false;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        appOpenAd = null;
                        appOpenLoading = false;
                    }
                });
    }

    private boolean isAppOpenReady() {
        return appOpenAd != null
                && System.currentTimeMillis() - appOpenLoadedAt < APP_OPEN_MAX_AGE_MS;
    }

    private void maybeShowAppOpen() {
        long now = System.currentTimeMillis();

        if (showingFullScreen) return;
        if (now - lastFullScreenAt < APP_OPEN_COOLDOWN_MS) return;
        if (!isAppOpenReady()) { loadAppOpen(); return; }

        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                appOpenAd = null;
                showingFullScreen = false;
                lastFullScreenAt = System.currentTimeMillis();
                loadAppOpen();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                appOpenAd = null;
                showingFullScreen = false;
                loadAppOpen();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                showingFullScreen = true;
            }
        });

        appOpenAd.show(this);
    }

    @Override
    public void onDestroy() {
        if (banner != null) banner.destroy();
        super.onDestroy();
    }
}
