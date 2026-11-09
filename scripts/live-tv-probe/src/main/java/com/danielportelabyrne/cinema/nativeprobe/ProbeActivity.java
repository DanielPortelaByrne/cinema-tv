package com.danielportelabyrne.cinema.nativeprobe;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.webkit.PermissionRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Isolated interoperability probe. No private app data, credentials, cookies or URLs are exported. */
public class ProbeActivity extends Activity {
    private static final String TAG = "CinemaNativeProbe";
    private WebView web;
    private boolean youtube;
    private TvView tv;
    private LinearLayout root;
    private long tuneStarted;
    private final java.util.LinkedHashMap<Long,String> channelInputs=new java.util.LinkedHashMap<>();
    private long tunedChannel=-1;
    private void note(String message) { Log.i(TAG, message); }
    @Override public void onCreate(Bundle state) { super.onCreate(state); show(getIntent()); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (tv!=null && intent.hasExtra("channel_id") && channelInputs.containsKey(intent.getLongExtra("channel_id",-1))) {
            long id=intent.getLongExtra("channel_id",-1); tune(channelInputs.get(id),TvContract.buildChannelUri(id)); return;
        }
        if (intent.getBooleanExtra("report", false) && web != null) {
            if (youtube) {
                web.evaluateJavascript("JSON.stringify({state:player?.getPlayerState(),time:player?.getCurrentTime(),duration:player?.getDuration()})", value -> note("YOUTUBE_STATUS " + value));
                return;
            }
            web.evaluateJavascript("JSON.stringify(Array.from(document.querySelectorAll('video')).map(v=>({ready:v.readyState,paused:v.paused,time:v.currentTime,width:v.videoWidth,height:v.videoHeight,error:v.error?{code:v.error.code,message:v.error.message}:null})))", value -> note("WEB_VIDEO " + value));
        } else { show(intent); }
    }
    private void label(String text) {
        TextView label = new TextView(this); label.setText(text); label.setTextColor(Color.WHITE);
        label.setTextSize(18); root.addView(label); note(text);
    }
    private void show(Intent intent) {
        if (tv != null) { tv.reset(); tv = null; }
        if (web != null) { web.destroy(); web = null; }
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24);
        root.setBackgroundColor(Color.rgb(12,15,20)); setContentView(root);
        youtube = "youtube".equals(intent.getStringExtra("mode"));
        if (youtube || "web".equals(intent.getStringExtra("mode"))) {
            String address = intent.getStringExtra("url");
            Uri uri = Uri.parse(address == null ? "https://www.adultswim.com/streams" : address);
            if (!"https".equals(uri.getScheme()) || !("www.adultswim.com".equals(uri.getHost()) || "www.tg4.ie".equals(uri.getHost()))) {
                label("Only official public test pages are allowed."); return;
            }
            label("Official player interoperability test: " + uri.getHost());
            web = new WebView(this); web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.getSettings().setMediaPlaybackRequiresUserGesture(false);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) { note("WEB_PAGE_READY " + Uri.parse(url).getHost()); }
            });
            web.setWebChromeClient(new WebChromeClient() {
                @Override public void onPermissionRequest(PermissionRequest request) {
                    // Only the official TG4 player may use the normal CDM licence path in this opt-in test.
                    note("WEB_PERMISSION origin="+request.getOrigin()+" resources="+java.util.Arrays.toString(request.getResources()));
                    if (intent.getBooleanExtra("protected_media",false)
                            && "https://www.tg4.ie/".equals(request.getOrigin().toString())
                            && java.util.Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                        note("WEB_PROTECTED_MEDIA_ENABLED www.tg4.ie"); return;
                    }
                    request.deny();
                }
                @Override public boolean onConsoleMessage(ConsoleMessage m) {
                    if (m.message().startsWith("CINEMA_PROBE_")) note(m.message()); return true;
                }
            });
            root.addView(web, new LinearLayout.LayoutParams(-1,0,1));
            note("WEBVIEW_VERSION "+WebView.getCurrentWebViewPackage().versionName);
            note("WIDEVINE_SUPPORTED "+android.media.MediaDrm.isCryptoSchemeSupported(java.util.UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")));
            if (youtube) {
                String id=intent.getStringExtra("video");
                if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) { label("Missing public video ID"); return; }
                String base="https://"+getPackageName();
                String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='margin:0;background:black'><div id='player'></div><script src='https://www.youtube.com/iframe_api'></script><script>var player;function onYouTubeIframeAPIReady(){player=new YT.Player('player',{width:'100%',height:innerHeight,videoId:'"+id+"',playerVars:{autoplay:1,playsinline:1,origin:'"+base+"'},events:{onReady:e=>{console.log('CINEMA_PROBE_READY');e.target.playVideo()},onStateChange:e=>console.log('CINEMA_PROBE_STATE '+e.data),onError:e=>console.log('CINEMA_PROBE_ERROR '+e.data)}})}</script></body></html>";
                web.loadDataWithBaseURL(base,html,"text/html","UTF-8",null);
                web.requestFocus(); note("YOUTUBE_EMBED "+id); return;
            }
            web.loadUrl(uri.toString()); web.requestFocus(); return;
        }
        label("TV Input Framework interoperability test");
        TvInputManager manager = (TvInputManager)getSystemService(TV_INPUT_SERVICE);
        for (TvInputInfo input : manager.getTvInputList()) {
            label("Input: " + input.getId() + " passthrough=" + input.isPassthroughInput());
        }
        if (checkSelfPermission("android.permission.READ_TV_LISTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            label("TV listings require the TV's normal runtime consent.");
            requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},100);
            return;
        }
        int count=0;
        channelInputs.clear();
        String selectedInput=null; long selectedId=-1;
        ScrollView choicesScroll=new ScrollView(this);
        LinearLayout choices=new LinearLayout(this); choices.setOrientation(LinearLayout.VERTICAL);
        choicesScroll.addView(choices); root.addView(choicesScroll,new LinearLayout.LayoutParams(-1,0,1));
        try (Cursor channels = getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{"_id","display_name","input_id","type"},null,null,null)) {
            if (channels != null) {
                while (channels.moveToNext()) {
                    final long id=channels.getLong(0); final String input=channels.getString(2);
                    if (input==null || input.isEmpty() || "TYPE_PREVIEW".equals(channels.getString(3))) continue;
                    count++;
                    channelInputs.put(id,input);
                    final String name=channels.getString(1);
                    Button b=new Button(this); b.setText(name); choices.addView(b);
                    b.setOnClickListener(v -> tune(input, TvContract.buildChannelUri(id)));
                    note("CHANNEL id="+id+" name="+name+" input="+input);
                    if (id==intent.getLongExtra("channel_id",-1)) { selectedInput=input; selectedId=id; }
                }
            }
            label("Accessible channel rows: " + count);
        } catch (SecurityException e) { label("Channel access denied: " + e.getClass().getSimpleName()); }
        if (selectedInput!=null) tune(selectedInput,TvContract.buildChannelUri(selectedId));
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code==100 && results.length>0 && results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) show(getIntent());
        else label("TV listings permission not granted.");
    }
    private void tune(String input, Uri channel) {
        tunedChannel=Long.parseLong(channel.getLastPathSegment());
        if (tv == null) {
            root.removeAllViews(); label("Native TV input test — Back returns to channel list");
            tv = new TvView(this); root.addView(tv, new LinearLayout.LayoutParams(-1,0,1));
            tv.setCallback(new TvView.TvInputCallback() {
                @Override public void onVideoAvailable(String id) { note("TIF_VIDEO_AVAILABLE " + id+" elapsed_ms="+(android.os.SystemClock.elapsedRealtime()-tuneStarted)); }
                @Override public void onVideoUnavailable(String id, int reason) { note("TIF_VIDEO_UNAVAILABLE " + id+" reason="+reason); }
                @Override public void onConnectionFailed(String id) { note("TIF_CONNECTION_FAILED " + id); }
            });
        }
        tuneStarted=android.os.SystemClock.elapsedRealtime();
        note("TIF_TUNE "+input+" "+channel); tv.tune(input,channel);
        long now=System.currentTimeMillis();
        try (Cursor epg=getContentResolver().query(TvContract.buildProgramsUriForChannel(tunedChannel,now,now+24*60*60*1000L),new String[]{"title","start_time_utc_millis","end_time_utc_millis"},null,null,null)) {
            int seen=0;
            while(epg!=null && epg.moveToNext() && seen++<2) note("TIF_EPG channel="+tunedChannel+" title="+epg.getString(0)+" start="+epg.getLong(1)+" end="+epg.getLong(2));
        } catch (RuntimeException e) { note("TIF_EPG_UNAVAILABLE "+e.getClass().getSimpleName()); }
    }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (tv!=null && (key==KeyEvent.KEYCODE_CHANNEL_UP || key==KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            java.util.ArrayList<Long> ids=new java.util.ArrayList<>(channelInputs.keySet());
            int next=(ids.indexOf(tunedChannel)+(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1)+ids.size())%ids.size();
            long id=ids.get(next); tune(channelInputs.get(id),TvContract.buildChannelUri(id)); return true;
        }
        return super.onKeyDown(key,event);
    }
    @Override public void onBackPressed() {
        if (tv!=null) { show(getIntent()); } else super.onBackPressed();
    }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (web != null) web.destroy(); if (tv != null) tv.reset(); super.onDestroy();
    }
}
