package com.bubbleladder.resetlabv432;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ResetCore {
    private ResetCore(){}
    public static final String API="https://api.bepick.io/game/bubble_ladder3";
    public static final String PREF="bubble_reset_lab_v432";
    public static final String ACTION_UPDATED="com.bubbleladder.resetlabv432.UPDATED";
    public static final int MAX_HISTORY=5000, WINDOW=480;
    public static final int[] INTERVAL={0,3,4,6,7};
    public static final String[] ENGINE={"무리셋","3회 리셋","4회 리셋","6회 리셋","7회 리셋"};
    public static final String[] COMBO={"","좌3짝","좌4홀","우3홀","우4짝"};
    public static final String[] DIM={"좌/우","사다리수","홀/짝"};
    private static final int[][] VEC={{0,0,0},{+1,+1,-1},{+1,-1,+1},{-1,+1,+1},{-1,-1,-1}};
    public static final String K_MASTER="master_history",K_AUTO="auto",K_BASE_STAKE="base_stake",K_ODDS="odds",K_LAST_LATEST="last_latest",K_SNAPSHOT="snapshot_size",K_LAST_SYNC="last_sync";

    public static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private static String eh(int i){return "engine_hist_"+i;}
    private static String ec(int i){return "engine_counter_"+i;}
    private static String er(int i){return "engine_resets_"+i;}
    private static String pt(int i){return "pending_idx_"+i;}
    private static String pe(int i){return "pending_exc_"+i;}
    private static String pn(int i){return "total_"+i;}
    private static String ph(int i){return "hit_"+i;}
    private static String pp(int i){return "profit_"+i;}
    private static String rec(int i){return "records_"+i;}

    public static final class Result{public long idx;public String date;public int round,combo;}
    public static final class PatternStat{public int length,matches,plusNext,minusNext,vote;}
    public static final class DimensionStat{public String name;public PatternStat[] patterns=new PatternStat[4];public int plusVotes,minusVotes,abstain,finalVote;}
    public static final class Analysis{public int exclude,count,topOppose,secondOppose,decisiveVotes;public int[] oppose=new int[5];public String triple,selected,grade,mode,range,suffix;public DimensionStat[] dims;}
    public static final class EngineView{public int id,interval,counter,resets,total,hit,recent20N,recent20Hit,dataSize;public double profit;public Analysis analysis;}
    public static final class SyncResult{public boolean newRound;public long latest;public int snapshotSize;public List<Result> master;public EngineView[] engines;}

    public static List<Result> fetch() throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(API).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setUseCaches(false);
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","BubbleResetLab/4.3.2");
        int code=c.getResponseCode();if(code<200||code>=300)throw new Exception("API HTTP "+code);
        BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));StringBuilder sb=new StringBuilder();String line;
        while((line=br.readLine())!=null)sb.append(line);br.close();c.disconnect();
        JSONObject root=new JSONObject(sb.toString());JSONArray arr=root.optJSONArray("data");if(arr==null)throw new Exception("API data 없음");
        List<Result> out=new ArrayList<>();
        for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null)continue;int combo=o.optInt("fd4",0);long idx=o.optLong("idx",0);if(idx<=0||combo<1||combo>4)continue;Result r=new Result();r.idx=idx;r.date=o.optString("date","");r.round=o.optInt("round",0);r.combo=combo;out.add(r);} 
        out.sort((a,b)->Long.compare(b.idx,a.idx));if(out.isEmpty())throw new Exception("결과 없음");return out;
    }

    private static List<Result> loadList(SharedPreferences sp,String key){
        List<Result> out=new ArrayList<>();String raw=sp.getString(key,"");if(raw==null||raw.isEmpty())return out;
        try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;Result r=new Result();r.idx=j.optLong("i");r.date=j.optString("d","");r.round=j.optInt("r",0);r.combo=j.optInt("c",0);if(r.idx>0&&r.combo>=1&&r.combo<=4)out.add(r);}}catch(Exception ignored){}
        out.sort((a,b)->Long.compare(b.idx,a.idx));return out;
    }
    private static void saveList(SharedPreferences sp,String key,List<Result> list){
        try{JSONArray a=new JSONArray();for(Result r:list){JSONObject o=new JSONObject();o.put("i",r.idx);o.put("d",r.date);o.put("r",r.round);o.put("c",r.combo);a.put(o);}sp.edit().putString(key,a.toString()).apply();}catch(Exception ignored){}
    }
    public static List<Result> merge(List<Result>a,List<Result>b){TreeMap<Long,Result>m=new TreeMap<>(Collections.reverseOrder());for(Result r:a)m.put(r.idx,r);for(Result r:b)m.put(r.idx,r);List<Result>o=new ArrayList<>(m.values());if(o.size()>MAX_HISTORY)o=new ArrayList<>(o.subList(0,MAX_HISTORY));return o;}
    public static List<Result> master(Context c){return loadList(prefs(c),K_MASTER);}
    public static List<Result> recentDesc(List<Result>d,int n){List<Result>x=new ArrayList<>(d);x.sort((a,b)->Long.compare(b.idx,a.idx));return x.size()>n?new ArrayList<>(x.subList(0,n)):x;}

    public static SyncResult sync(Context c)throws Exception{
        SharedPreferences sp=prefs(c);List<Result> fresh=fetch();long latest=fresh.get(0).idx;long prev=sp.getLong(K_LAST_LATEST,-1);boolean first=prev<=0;boolean newRound=!first&&latest!=prev;
        List<Result> master=merge(loadList(sp,K_MASTER),fresh);saveList(sp,K_MASTER,master);
        sp.edit().putInt(K_SNAPSHOT,fresh.size()).putLong(K_LAST_LATEST,latest).putLong(K_LAST_SYNC,System.currentTimeMillis()).apply();
        for(int i=0;i<INTERVAL.length;i++){
            List<Result> hist=loadList(sp,eh(i));if(first||hist.isEmpty())hist=new ArrayList<>(fresh);
            if(newRound){resolvePending(sp,i,fresh);if(i==0){hist=new ArrayList<>(master);}else{int count=sp.getInt(ec(i),0)+1;if(count>=INTERVAL[i]){hist=new ArrayList<>(fresh);count=0;sp.edit().putInt(er(i),sp.getInt(er(i),0)+1).apply();}else hist=merge(hist,fresh);sp.edit().putInt(ec(i),count).apply();}}
            else if(i==0)hist=new ArrayList<>(master);
            saveList(sp,eh(i),hist);Analysis a=analyze(hist);savePending(sp,i,hist,a);
        }
        SyncResult sr=new SyncResult();sr.newRound=newRound;sr.latest=latest;sr.snapshotSize=fresh.size();sr.master=master;sr.engines=views(c);return sr;
    }

    public static EngineView[] views(Context c){SharedPreferences sp=prefs(c);EngineView[] out=new EngineView[INTERVAL.length];for(int i=0;i<out.length;i++){List<Result> h=loadList(sp,eh(i));EngineView v=new EngineView();v.id=i;v.interval=INTERVAL[i];v.counter=sp.getInt(ec(i),0);v.resets=sp.getInt(er(i),0);v.total=sp.getInt(pn(i),0);v.hit=sp.getInt(ph(i),0);v.profit=Double.longBitsToDouble(sp.getLong(pp(i),Double.doubleToLongBits(0)));int[] rr=recentRecord(sp,i,20);v.recent20Hit=rr[0];v.recent20N=rr[1];v.dataSize=h.size();v.analysis=h.isEmpty()?null:analyze(h);out[i]=v;}return out;}

    public static Analysis analyze(List<Result> desc){if(desc==null||desc.isEmpty())return null;List<Result> all=new ArrayList<>(desc);all.sort(Comparator.comparingLong(x->x.idx));int end=all.size(),start=Math.max(0,end-WINDOW);return decision(all,start,end);}
    private static Analysis decision(List<Result> all,int start,int end){
        Analysis a=new Analysis();a.count=end-start;a.dims=new DimensionStat[3];int[] lens={3,4,5,6};
        for(int dim=0;dim<3;dim++){DimensionStat ds=new DimensionStat();ds.name=DIM[dim];for(int j=0;j<lens.length;j++){PatternStat ps=pattern(all,start,end,lens[j],dim);ds.patterns[j]=ps;if(ps.vote>0){ds.plusVotes++;a.decisiveVotes++;}else if(ps.vote<0){ds.minusVotes++;a.decisiveVotes++;}else ds.abstain++;}ds.finalVote=ds.plusVotes>ds.minusVotes?1:ds.minusVotes>ds.plusVotes?-1:0;a.dims[dim]=ds;}
        for(int combo=1;combo<=4;combo++){int opp=0;for(int dim=0;dim<3;dim++)for(PatternStat ps:a.dims[dim].patterns)if(ps.vote!=0&&ps.vote!=VEC[combo][dim])opp++;a.oppose[combo]=opp;}
        int max=-1;List<Integer> leaders=new ArrayList<>();for(int c=1;c<=4;c++){if(a.oppose[c]>max){max=a.oppose[c];leaders.clear();leaders.add(c);}else if(a.oppose[c]==max)leaders.add(c);} 
        if(leaders.size()==1){a.exclude=leaders.get(0);a.mode="반대표 최다";}else{int best=-1;List<Integer> x=new ArrayList<>();for(int c:leaders){int z=0;for(int d=0;d<3;d++){int fv=a.dims[d].finalVote;if(fv!=0&&fv!=VEC[c][d])z++;}if(z>best){best=z;x.clear();x.add(c);}else if(z==best)x.add(c);}if(x.size()==1){a.exclude=x.get(0);a.mode="동률→3차원 다수결";}else{a.exclude=tieAbs(all,start,end,x);a.mode="동률→미출현 간격";}}
        a.topOppose=a.oppose[a.exclude];for(int c=1;c<=4;c++)if(c!=a.exclude)a.secondOppose=Math.max(a.secondOppose,a.oppose[c]);int gap=a.topOppose-a.secondOppose;a.grade=a.decisiveVotes>=9&&gap>=3?"강":a.decisiveVotes>=6&&gap>=2?"보통":"약";a.triple=tripleFor(a.exclude);a.selected=selectedThree(a.exclude);a.range=range(all,start,end);a.suffix=suffix(all,end,6);return a;
    }
    private static PatternStat pattern(List<Result>a,int start,int end,int len,int dim){PatternStat p=new PatternStat();p.length=len;if(end-start<=len)return p;for(int next=start+len;next<end;next++){boolean same=true;for(int j=0;j<len;j++){if(VEC[a.get(end-len+j).combo][dim]!=VEC[a.get(next-len+j).combo][dim]){same=false;break;}}if(same){p.matches++;if(VEC[a.get(next).combo][dim]>0)p.plusNext++;else p.minusNext++;}}p.vote=p.plusNext>p.minusNext?1:p.minusNext>p.plusNext?-1:0;return p;}
    private static int tieAbs(List<Result>a,int start,int end,List<Integer>x){int best=x.get(0),bg=-1;for(int c:x){int g=end-start+1;for(int i=end-1;i>=start;i--)if(a.get(i).combo==c){g=end-1-i;break;}if(g>bg||(g==bg&&c<best)){bg=g;best=c;}}return best;}

    private static void savePending(SharedPreferences sp,int i,List<Result>d,Analysis a){if(d.isEmpty()||a==null)return;long next=nextIdx(d.get(0));long existing=sp.getLong(pt(i),-1);if(existing==next)return;sp.edit().putLong(pt(i),next).putInt(pe(i),a.exclude).apply();}
    private static void resolvePending(SharedPreferences sp,int i,List<Result>fresh){long idx=sp.getLong(pt(i),-1);int exc=sp.getInt(pe(i),0);if(idx<=0||exc<1||exc>4)return;Result actual=null;for(Result r:fresh)if(r.idx==idx){actual=r;break;}if(actual==null)return;boolean ok=actual.combo!=exc;int n=sp.getInt(pn(i),0)+1,h=sp.getInt(ph(i),0)+(ok?1:0);int stake=Math.max(5000,sp.getInt(K_BASE_STAKE,5000));double odds=Math.max(1.01,sp.getFloat(K_ODDS,1.95f));double pnl=ok?successProfit(stake,odds):-3.0*stake;double old=Double.longBitsToDouble(sp.getLong(pp(i),Double.doubleToLongBits(0)));appendRecord(sp,i,idx,exc,actual.combo,ok);sp.edit().putInt(pn(i),n).putInt(ph(i),h).putLong(pp(i),Double.doubleToLongBits(old+pnl)).remove(pt(i)).remove(pe(i)).apply();}
    private static void appendRecord(SharedPreferences sp,int i,long idx,int exc,int actual,boolean ok){try{JSONArray a=new JSONArray(sp.getString(rec(i),"[]"));JSONObject o=new JSONObject();o.put("idx",idx);o.put("exclude",exc);o.put("actual",actual);o.put("ok",ok);a.put(o);JSONArray z=new JSONArray();for(int j=Math.max(0,a.length()-1000);j<a.length();j++)z.put(a.get(j));sp.edit().putString(rec(i),z.toString()).apply();}catch(Exception ignored){}}
    private static int[] recentRecord(SharedPreferences sp,int i,int limit){int hit=0,n=0;try{JSONArray a=new JSONArray(sp.getString(rec(i),"[]"));for(int j=a.length()-1;j>=0&&n<limit;j--){JSONObject o=a.optJSONObject(j);if(o==null)continue;n++;if(o.optBoolean("ok",false))hit++;}}catch(Exception ignored){}return new int[]{hit,n};}

    public static void resetExperiment(Context c){SharedPreferences sp=prefs(c);boolean auto=sp.getBoolean(K_AUTO,true);int stake=sp.getInt(K_BASE_STAKE,5000);float odds=sp.getFloat(K_ODDS,1.95f);sp.edit().clear().putBoolean(K_AUTO,auto).putInt(K_BASE_STAKE,stake).putFloat(K_ODDS,odds).apply();}
    public static JSONObject backup(Context c)throws Exception{SharedPreferences sp=prefs(c);JSONObject root=new JSONObject();root.put("format","BubbleResetLabV432");JSONObject all=new JSONObject();Map<String,?>m=sp.getAll();for(Map.Entry<String,?>e:m.entrySet()){Object v=e.getValue();if(v instanceof String)all.put(e.getKey(),v);else if(v instanceof Integer)all.put(e.getKey(),(Integer)v);else if(v instanceof Long)all.put(e.getKey(),(Long)v);else if(v instanceof Float)all.put(e.getKey(),(double)(Float)v);else if(v instanceof Boolean)all.put(e.getKey(),(Boolean)v);}root.put("prefs",all);return root;}
    public static void restore(Context c,JSONObject root)throws Exception{if(!"BubbleResetLabV432".equals(root.optString("format")))throw new Exception("V4.3.2 백업이 아닙니다.");JSONObject all=root.getJSONObject("prefs");SharedPreferences.Editor ed=prefs(c).edit().clear();Iterator<String>it=all.keys();while(it.hasNext()){String k=it.next();Object v=all.get(k);if(v instanceof Boolean)ed.putBoolean(k,(Boolean)v);else if(v instanceof Integer)ed.putInt(k,(Integer)v);else if(v instanceof Long)ed.putLong(k,(Long)v);else if(v instanceof Double){if(k.equals(K_ODDS))ed.putFloat(k,((Double)v).floatValue());else ed.putLong(k,((Double)v).longValue());}else ed.putString(k,String.valueOf(v));}ed.apply();}

    public static long nextIdx(Result r){try{String dk=dayKey(r.date);if(r.round<480)return Long.parseLong(dk.substring(2,8)+String.format(Locale.US,"%04d",r.round+1));SimpleDateFormat f=new SimpleDateFormat("yyyyMMdd",Locale.US);Calendar c=Calendar.getInstance();c.setTime(f.parse(dk));c.add(Calendar.DAY_OF_MONTH,1);String d=f.format(c.getTime());return Long.parseLong(d.substring(2,8)+"0001");}catch(Exception e){return r.idx+1;}}
    public static long millisToNextDraw(){long x=180000L,now=System.currentTimeMillis(),mod=Math.floorMod(now,x);long left=x-mod;return left==0?x:left;}
    public static String countdownText(){long s=(millisToNextDraw()+999)/1000;return String.format(Locale.KOREA,"%02d:%02d",s/60,s%60);}
    public static String sideLabel(String dim,int v){if("좌/우".equals(dim))return v>0?"좌":"우";if("사다리수".equals(dim))return v>0?"3줄":"4줄";return v>0?"홀":"짝";}
    public static String tripleFor(int c){switch(c){case 1:return "우 + 4줄 + 홀";case 2:return "우 + 3줄 + 짝";case 3:return "좌 + 4줄 + 짝";case 4:return "좌 + 3줄 + 홀";default:return "-";}}
    public static String selectedThree(int exc){StringBuilder s=new StringBuilder();for(int k=1;k<=4;k++)if(k!=exc){if(s.length()>0)s.append(" / ");s.append(COMBO[k]);}return s.toString();}
    private static String dayKey(String s){String d=String.valueOf(s==null?"":s).replaceAll("\\D","");return d.length()>=8?d.substring(0,8):String.valueOf(s==null?"":s);}
    private static String range(List<Result>a,int st,int en){if(en<=st)return "-";Result x=a.get(st),y=a.get(en-1);return x.date+" "+x.round+"회 → "+y.date+" "+y.round+"회";}
    private static String suffix(List<Result>a,int en,int max){StringBuilder s=new StringBuilder();for(int i=Math.max(0,en-max);i<en;i++){if(s.length()>0)s.append(" → ");s.append(COMBO[a.get(i).combo]);}return s.toString();}
    public static double successProfit(int stake,double odds){return stake*(2*odds-3);}public static double breakEven(double odds){return 3/(2*odds);}public static String pct(double v){return String.format(Locale.KOREA,"%.1f%%",v*100);}public static String money(double v){return String.format(Locale.KOREA,"%,.0f원",v);}public static String signed(double v){return (v>=0?"+":"")+money(v);}
}
