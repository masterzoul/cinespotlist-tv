package com.masterzoul.cinespotlisttv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://cinespotlist3.pages.dev/";
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        enterImmersiveMode();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(23, 22, 37));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectTvMode();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showOfflinePage();
                }
            }
        });

        webView.loadUrl(APP_URL);
    }

    private void enterImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void injectTvMode() {
        String js = "(function(){" +
            "if(document.getElementById('cinespot-tv-style')) return;" +
            "var s=document.createElement('style');s.id='cinespot-tv-style';" +
            "s.textContent=`" +
            ":root{--max:820px!important;--page-pad:28px!important;}" +
            "html,body{overflow-x:hidden!important;}" +
            ".app{max-width:900px!important;margin:0 auto!important;box-shadow:0 0 80px rgba(0,0,0,.35)!important;}" +
            ".head-inner,main{max-width:820px!important;}" +
            "h1{font-size:34px!important;}" +
            ".meta{font-size:18px!important;}" +
            ".icons{gap:22px!important;margin-top:18px!important;}" +
            ".icon-btn{width:58px!important;height:58px!important;}" +
            ".icon-btn svg{width:29px!important;height:29px!important;}" +
            ".netflix-n{font-size:34px!important;}" +
            ".menu button,.search-input,.search-go,.search-clear{font-size:20px!important;padding:12px 16px!important;}" +
            "main{padding-top:30px!important;}" +
            ".category-head h2{font-size:30px!important;}" +
            ".category-head h2 span{font-size:22px!important;}" +
            ".category-head p,.notice,.error,.empty{font-size:21px!important;}" +
            ".cards{gap:20px!important;}" +
            ".card{border-radius:22px!important;}" +
            ".card-top{gap:20px!important;padding:18px 18px 14px!important;}" +
            ".poster{width:180px!important;height:270px!important;flex:0 0 180px!important;border-radius:16px!important;}" +
            ".topline{font-size:20px!important;}" +
            ".title{font-size:30px!important;line-height:1.22!important;}" +
            ".genres{font-size:22px!important;margin-top:9px!important;}" +
            ".provider{font-size:20px!important;}" +
            ".synopsis-text{font-size:22px!important;line-height:1.5!important;}" +
            ".more-btn{font-size:20px!important;margin-top:6px!important;}" +
            ".ratings{padding:4px 16px 16px!important;}" +
            ".ratings-line{font-size:20px!important;letter-spacing:-.01em!important;}" +
            ".syn-lang-btn{height:30px!important;min-width:46px!important;font-size:16px!important;line-height:28px!important;border-radius:8px!important;}" +
            ".load-more-btn{font-size:21px!important;padding:13px 26px!important;min-width:160px!important;}" +
            ".refresh-fab{width:62px!important;height:62px!important;right:34px!important;bottom:30px!important;}" +
            ".refresh-fab svg{width:28px!important;height:28px!important;}" +
            "footer{font-size:19px!important;}" +
            "button:focus,input:focus,[tabindex]:focus{outline:4px solid #e99a51!important;outline-offset:4px!important;box-shadow:0 0 0 4px rgba(233,154,81,.22)!important;}" +
            "@media(min-width:1200px){.app{max-width:900px!important}.head-inner,main{max-width:820px!important}}" +
            "`;document.head.appendChild(s);" +
            "function prep(){document.querySelectorAll('button,input,a,[role=button]').forEach(function(e){if(!e.hasAttribute('tabindex'))e.setAttribute('tabindex','0');});}" +
            "prep();new MutationObserver(prep).observe(document.body,{childList:true,subtree:true});" +
            "window.tvMove=function(dir){" +
              "var els=[].slice.call(document.querySelectorAll('button:not([disabled]),input:not([disabled]),a[href],[tabindex=\"0\"]')).filter(function(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0&&getComputedStyle(e).visibility!=='hidden';});" +
              "if(!els.length)return;var cur=document.activeElement;if(els.indexOf(cur)<0){els[0].focus();els[0].scrollIntoView({block:'center'});return;}" +
              "var cr=cur.getBoundingClientRect(),cx=cr.left+cr.width/2,cy=cr.top+cr.height/2,best=null,bs=1e9;" +
              "els.forEach(function(e){if(e===cur)return;var r=e.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,dx=x-cx,dy=y-cy,ok=false,score=1e9;" +
              "if(dir==='down'&&dy>8){ok=true;score=dy*dy+dx*dx*.35;}if(dir==='up'&&dy<-8){ok=true;score=dy*dy+dx*dx*.35;}if(dir==='right'&&dx>8){ok=true;score=dx*dx+dy*dy*.35;}if(dir==='left'&&dx<-8){ok=true;score=dx*dx+dy*dy*.35;}if(ok&&score<bs){bs=score;best=e;}});" +
              "if(best){best.focus();best.scrollIntoView({behavior:'smooth',block:'center',inline:'center'});}else{window.scrollBy({top:dir==='down'?420:dir==='up'?-420:0,left:dir==='right'?220:dir==='left'?-220:0,behavior:'smooth'});}" +
            "};" +
            "})();";
        webView.evaluateJavascript(js, null);
        webView.requestFocus();
    }

    private void showOfflinePage() {
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{margin:0;background:#171625;color:#fff;font-family:sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center;text-align:center}" +
                ".box{max-width:720px;padding:50px}h1{font-size:42px}p{font-size:25px;color:#aaa6bb}button{font-size:25px;padding:16px 30px;border-radius:14px;border:2px solid #e99a51;background:#211f31;color:#e99a51}</style></head>" +
                "<body><div class='box'><h1>CineSpotList TV</h1><p>Internet connection is unavailable.</p><button onclick=\"location.href='" + APP_URL + "'\">Retry</button></div></body></html>";
        webView.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && webView != null) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                webView.evaluateJavascript("window.tvMove&&window.tvMove('down')", null); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                webView.evaluateJavascript("window.tvMove&&window.tvMove('up')", null); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                webView.evaluateJavascript("window.tvMove&&window.tvMove('left')", null); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                webView.evaluateJavascript("window.tvMove&&window.tvMove('right')", null); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                webView.evaluateJavascript("(function(){var e=document.activeElement;if(e&&e.click)e.click();})()", null); return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }
}
