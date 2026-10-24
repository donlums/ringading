package uk.co.pix91.ringading;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Experimental direct Blink live player, no separate server/phone required. */
public final class LiveViewActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private ExoPlayer player;
    private ImmisTvBridge bridge;
    private TextView status;
    private Button watch;
    private volatile boolean finishing=false;
    private static final int BG=0xff0b121e;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(BG);
        PlayerView screen=new PlayerView(this);
        screen.setUseController(false);
        screen.setKeepScreenOn(true);
        screen.setVisibility(View.GONE);
        root.addView(screen,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout hud=new LinearLayout(this);
        hud.setOrientation(LinearLayout.VERTICAL);
        hud.setPadding(45,35,45,35);
        FrameLayout.LayoutParams hudParams=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(hud,hudParams);
        TextView title=new TextView(this);
        title.setText("Ringading  /  LIVE VIEW");
        title.setTextColor(0xffffc777);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        hud.addView(title);
        status=new TextView(this);
        status.setText("Select a Blink camera to watch live");
        status.setTextColor(Color.WHITE);
        status.setTextSize(17);
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);
        space.topMargin=12;
        hud.addView(status,space);

        LinearLayout buttons=new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actions=new LinearLayout.LayoutParams(-1,-2);
        actions.topMargin=20;
        hud.addView(buttons,actions);
        watch=new Button(this);
        watch.setAllCaps(false);
        watch.setText("Choose camera");
        watch.setOnClickListener(v -> chooseCamera(screen));
        buttons.addView(watch,new LinearLayout.LayoutParams(350,70));
        Button close=new Button(this);
        close.setAllCaps(false);
        close.setText("Back to Ringading");
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeParams=new LinearLayout.LayoutParams(310,70);
        closeParams.leftMargin=15;
        buttons.addView(close,closeParams);

        TextView note=new TextView(this);
        note.setText("Experimental live video · Maximum 35 seconds · Not saved · "
            + "Original Blink doorbells in Event Response mode may reject manual live view");
        note.setTextColor(0xffa9b8c9);
        note.setTextSize(14);
        LinearLayout.LayoutParams bottom=new LinearLayout.LayoutParams(-1,-2);
        bottom.topMargin=22;
        hud.addView(note,bottom);
        setContentView(root);
        watch.requestFocus();
    }
    private void report(String message) {
        runOnUiThread(() -> { if(!finishing && status!=null) status.setText(message); });
    }
    private void chooseCamera(PlayerView surface) {
        watch.setEnabled(false);
        report("Discovering your Blink doorbells…");
        io.execute(() -> {
            try {
                List<LiveViewApi.Camera> options=LiveViewApi.discover(this);
                runOnUiThread(()->{
                    if(finishing)return;
                    watch.setEnabled(true);
                    if(options.isEmpty()) {
                        report("No compatible doorbell found. Connect your Blink account in Ringading.");
                        return;
                    }
                    String[] names=new String[options.size()];
                    for(int i=0;i<options.size();i++)names[i]=options.get(i).name;
                    new AlertDialog.Builder(this)
                        .setTitle("Choose a Blink doorbell")
                        .setItems(names, (dialog,index)->startCamera(options.get(index),surface))
                        .setNegativeButton("Cancel",null).show();
                });
            }catch(Exception e) {
                report("Camera discovery: " + safeMessage(e));
                runOnUiThread(()->{if(!finishing)watch.setEnabled(true);});
            }
        });
    }
    private void startCamera(LiveViewApi.Camera camera,PlayerView surface) {
        watch.setEnabled(false);
        report("Requesting live view from " + camera.name + "…");
        io.execute(() -> {
            ImmisTvBridge stream=null;
            try {
                LiveViewApi.Session session=LiveViewApi.start(this,camera);
                if(finishing)return;
                stream=new ImmisTvBridge(this,session,new ImmisTvBridge.Listener(){
                    @Override public void update(String text) { report(text); }
                    @Override public void error(String text) { report(text); }
                });
                String streamUrl=stream.start();
                ImmisTvBridge newBridge=stream;
                runOnUiThread(()->{
                    if(finishing){newBridge.close();return;}
                    if(bridge!=null)bridge.close();
                    bridge=newBridge;
                    surface.setVisibility(View.VISIBLE);
                    if(player!=null)player.release();
                    player=new ExoPlayer.Builder(this).build();
                    player.addListener(new Player.Listener(){
                        @Override public void onPlaybackStateChanged(int state){
                            if(state==Player.STATE_READY) report("LIVE · " + camera.name + "  ·  Press Back to close");
                            else if(state==Player.STATE_BUFFERING)report("Buffering camera video…");
                            else if(state==Player.STATE_ENDED)report("Live camera session finished");
                        }
                        @Override public void onPlayerError(PlaybackException error) {
                            report("Video decoder: " + error.getErrorCodeName() +
                                " · Blink stream may need a different transport");
                        }
                    });
                    surface.setPlayer(player);
                    player.setMediaItem(new MediaItem.Builder().setUri(streamUrl)
                        .setMimeType(MimeTypes.VIDEO_MP2T).build());
                    player.prepare();
                    player.play();
                    watch.setEnabled(true);
                });
            }catch(Exception e) {
                report("Live view: " + safeMessage(e));
                if(stream!=null)stream.close();
                runOnUiThread(()->{if(!finishing)watch.setEnabled(true);});
            }
        });
    }
    private String safeMessage(Exception e) {
        if(e instanceof UnsupportedOperationException || e instanceof IllegalStateException
            || e instanceof SecurityException) {
            String text=e.getMessage();
            if(text!=null && text.length()<180 && !text.contains("http") && !text.contains("token"))
                return text;
        }
        return e.getClass().getSimpleName();
    }
    @Override public void onBackPressed(){ finish(); }
    @Override protected void onStop() {
        super.onStop();
        // No streaming or microphone left active in the background.
        if(player!=null){player.stop();player.release();player=null;}
        if(bridge!=null){bridge.close();bridge=null;}
    }
    @Override protected void onDestroy() {
        finishing=true;
        if(player!=null){player.release();player=null;}
        if(bridge!=null){bridge.close();bridge=null;}
        io.shutdownNow();
        super.onDestroy();
    }
}
