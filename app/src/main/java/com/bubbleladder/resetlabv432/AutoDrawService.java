package com.bubbleladder.resetlabv432;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

public class AutoDrawService extends Service{
    public static final String CHANNEL_ID="reset_lab_v433_live";
    public static final int NOTI_ID=4330;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final ExecutorService ex=Executors.newSingleThreadExecutor();
    private boolean syncing=false;
    private int checks=0;

    private final Runnable poll=new Runnable(){public void run(){runPoll();}};
    private final Runnable tick=new Runnable(){public void run(){update();h.postDelayed(this,5000L);}};

    @Override public void onCreate(){
        super.onCreate();
        channel();
        startForeground(NOTI_ID,notification());
        h.post(tick);
        h.post(poll);
    }
    @Override public int onStartCommand(Intent i,int f,int id){
        if(!ResetCore.prefs(this).getBoolean(ResetCore.K_AUTO,true)){stopSelf();return START_NOT_STICKY;}
        if(!syncing){h.removeCallbacks(poll);h.post(poll);}
        return START_STICKY;
    }

    private void runPoll(){
        if(syncing)return;
        syncing=true;
        ex.execute(()->{
            boolean newRound=false;
            try{ResetCore.SyncResult r=ResetCore.sync(this);newRound=r.newRound;}catch(Exception ignored){}
            final boolean advanced=newRound;
            h.post(()->{
                syncing=false;
                checks++;
                sendBroadcast(new Intent(ResetCore.ACTION_UPDATED).setPackage(getPackageName()));
                update();
                h.removeCallbacks(poll);
                // 경계 직전/직후에는 8초, 평소에는 25초 간격으로 계속 확인.
                long left=ResetCore.millisToNextDraw();
                long delay=(left<=45000L || left>=150000L)?8000L:25000L;
                // 새 결과를 잡은 직후에는 API 중복 호출을 줄이기 위해 최소 20초 휴식.
                if(advanced)delay=Math.max(delay,20000L);
                h.postDelayed(poll,delay);
            });
        });
    }

    private void channel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL_ID,"V4.3.3 Reset Lab 회차감시",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("회차 경계뿐 아니라 백그라운드에서 API 새 회차를 계속 감시합니다.");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }
    private Notification notification(){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.content.SharedPreferences sp=ResetCore.prefs(this);
        int round=sp.getInt(ResetCore.K_LAST_API_ROUND,0);
        String date=sp.getString(ResetCore.K_LAST_API_DATE,"");
        ResetCore.EngineView[] e=ResetCore.views(this);
        StringBuilder x=new StringBuilder();
        x.append("API ").append(date).append(" ").append(round>0?round+"회":"-").append(" · 다음 ").append(ResetCore.countdownText());
        for(int i=0;i<e.length;i++)if(e[i].analysis!=null)x.append(" · ").append(i==0?"N":String.valueOf(ResetCore.INTERVAL[i])).append(":").append(ResetCore.COMBO[e[i].analysis.exclude]);
        x.append(" · 감시 ").append(checks).append("회");
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("보글사다리3 V4.3.3 Reset Lab · 회차감시 ON")
                .setContentText(x.toString())
                .setStyle(new Notification.BigTextStyle().bigText(x.toString()))
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();
    }
    private void update(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTI_ID,notification());}
    @Override public void onDestroy(){h.removeCallbacksAndMessages(null);ex.shutdownNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
