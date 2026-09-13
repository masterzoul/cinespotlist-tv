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
        settings.setLoadWithOverviewMode(false);
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
            ":root{--max:100%!important;--page-pad:44px!important;}" +
            "html,body{width:100%!important;max-width:none!important;overflow-x:hidden!important;background:#171625!important;}body{font-size:22px!important;}" +
            ".app{width:100%!important;max-width:none!important;margin:0!important;box-shadow:none!important;background:#171625!important;}" +
            ".head-inner,main{width:100%!important;max-width:none!important;padding-left:54px!important;padding-right:54px!important;box-sizing:border-box!important;}" +
            "h1{font-size:42px!important;line-height:1.1!important;}" +
            ".meta{font-size:21px!important;}" +
            ".icons{gap:24px!important;margin-top:20px!important;}" +
            ".icon-btn{width:66px!important;height:66px!important;}" +
            ".icon-btn svg{width:32px!important;height:32px!important;}" +
            ".netflix-n{font-size:39px!important;}" +
            ".menu button,.search-input,.search-go,.search-clear{font-size:22px!important;padding:14px 18px!important;}" +
            "main{padding-top:36px!important;padding-bottom:52px!important;}" +
            ".category-head h2{font-size:36px!important;}" +
            ".category-head h2 span{font-size:25px!important;}" +
            ".category-head p,.notice,.error,.empty{font-size:24px!important;line-height:1.4!important;}" +
            ".cards{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:26px!important;align-items:start!important;}" +
            ".card{min-width:0!important;width:100%!important;border-radius:24px!important;overflow:visible!important;}" +
            ".card-top{gap:22px!important;padding:22px 22px 14px!important;align-items:flex-start!important;}" +
            ".poster{width:210px!important;height:315px!important;flex:0 0 210px!important;border-radius:17px!important;object-fit:cover!important;}" +
            ".topline{font-size:22px!important;line-height:1.28!important;white-space:normal!important;}" +
            ".title{font-size:34px!important;line-height:1.18!important;white-space:normal!important;overflow:visible!important;text-overflow:clip!important;display:block!important;}" +
            ".genres{font-size:24px!important;margin-top:10px!important;line-height:1.35!important;}" +
            ".provider{font-size:22px!important;}" +
            ".synopsis-text{font-size:24px!important;line-height:1.46!important;}" +
            ".more-btn{font-size:21px!important;margin-top:8px!important;}" +
            ".ratings{padding:4px 20px 18px!important;overflow:visible!important;}" +
            ".ratings-line{font-size:22px!important;line-height:1.35!important;letter-spacing:-.01em!important;flex-wrap:wrap!important;row-gap:6px!important;}" +
            ".syn-lang-btn{height:34px!important;min-width:52px!important;font-size:17px!important;line-height:32px!important;border-radius:9px!important;}" +
            ".load-more-btn{font-size:24px!important;padding:15px 34px!important;min-width:190px!important;border-radius:14px!important;}" +
            ".refresh-fab{width:70px!important;height:70px!important;right:42px!important;bottom:34px!important;}" +
            ".refresh-fab svg{width:31px!important;height:31px!important;}" +
            "footer{font-size:20px!important;line-height:1.5!important;padding-left:54px!important;padding-right:54px!important;}" +
            "button:focus,input:focus,a:focus,[tabindex]:focus{outline:5px solid #f2a65a!important;outline-offset:5px!important;box-shadow:0 0 0 7px rgba(242,166,90,.22)!important;transform:scale(1.035)!important;transition:transform .12s ease,box-shadow .12s ease!important;}" +
            "@media(max-width:1250px){.cards{grid-template-columns:1fr!important;}}" +
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
                "<body><div class='box'><h1>CineSpotList TV Landscape</h1><p>Internet connection is unavailable.</p><button onclick=\"location.href='" + APP_URL + "'\">Retry</button></div></body></html>";
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
