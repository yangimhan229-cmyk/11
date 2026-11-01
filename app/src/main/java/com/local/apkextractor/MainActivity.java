package com.local.apkextractor;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public class MainActivity extends Activity {
  static final int BG=Color.rgb(13,24,42), PANEL=Color.rgb(25,40,61), WHITE=Color.WHITE, MUTED=Color.rgb(174,194,219), BLUE=Color.rgb(69,137,237);
  final ExecutorService executor=Executors.newSingleThreadExecutor();
  final ArrayList<App> apps=new ArrayList<>(), visible=new ArrayList<>();
  PackageManager pm;
  ListView list;
  TextView count;
  EditText search;
  CheckBox showSystem;
  ArrayAdapter<App> adapter;
  App selected;
  final int SAVE=101;

  static class App {
    String name,pkg,version; ApplicationInfo info; boolean system;
    App(String n,ApplicationInfo i,boolean s,String v){name=n;pkg=i.packageName;info=i;system=s;version=v;}
  }

  @Override public void onCreate(Bundle b){
    super.onCreate(b); pm=getPackageManager();
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(d(16),d(18),d(16),d(10));root.setBackgroundColor(BG);setContentView(root);
    root.addView(label("APK 추출기",27,WHITE,true));
    TextView sub=label("LG G8 · 설치 앱 APK 저장 및 공유",13,MUTED,false); sub.setPadding(0,d(4),0,d(12));root.addView(sub);
    search=new EditText(this);search.setSingleLine();search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setTextSize(15);search.setHint("앱 이름 / 패키지 검색");
    search.setPadding(d(12),0,d(12),0);search.setBackground(shape(PANEL,12));root.addView(search,new LinearLayout.LayoutParams(-1,d(49)));
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);root.addView(bar,new LinearLayout.LayoutParams(-1,d(50)));
    count=label("앱 불러오는 중…",12,MUTED,false);bar.addView(count,new LinearLayout.LayoutParams(0,-2,1));
    showSystem=new CheckBox(this);showSystem.setText("시스템 앱 포함");showSystem.setTextSize(12);showSystem.setTextColor(WHITE);showSystem.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));bar.addView(showSystem);
    list=new ListView(this);list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    root.addView(label("기본 APK → .apk   |   분할 APK → .apks (ZIP)",12,MUTED,false));
    adapter=new ArrayAdapter<App>(this,android.R.layout.simple_list_item_1,visible) {
      @Override public View getView(int pos,View old,android.view.ViewGroup parent) {
        App app=getItem(pos);
        LinearLayout outer=new LinearLayout(MainActivity.this);outer.setPadding(0,0,0,d(9));
        LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(12),d(12),d(10),d(12));row.setBackground(shape(PANEL,12));outer.addView(row,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout words=new LinearLayout(MainActivity.this);words.setOrientation(1);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=label(app.name,15,WHITE,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);words.addView(title);
        TextView pkg=label(app.pkg,11,MUTED,false);pkg.setSingleLine(true);pkg.setEllipsize(TextUtils.TruncateAt.END);words.addView(pkg);
        words.addView(label("버전 "+app.version,11,MUTED,false));
        Button btn=new Button(MainActivity.this);btn.setText("추출");btn.setTextSize(13);btn.setAllCaps(false);btn.setTextColor(WHITE);btn.setPadding(0,0,0,0);btn.setBackground(shape(BLUE,9));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(d(68),d(43));bp.leftMargin=d(8);row.addView(btn,bp);btn.setOnClickListener(v->export(app));
        return outer;
      }
    };
    list.setAdapter(adapter);
    search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter();}public void afterTextChanged(Editable e){}});
    showSystem.setOnCheckedChangeListener((v,b1)->filter());
    executor.execute(()->{
      ArrayList<App> result=new ArrayList<>();
      try {
        for(ApplicationInfo ai:pm.getInstalledApplications(0)){
          if(ai.sourceDir==null)continue;
          String ver="";
          try{PackageInfo pi=pm.getPackageInfo(ai.packageName,0);ver=pi.versionName==null?"":pi.versionName;}catch(Exception ignored){}
          boolean system=(ai.flags & (ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
          result.add(new App(String.valueOf(pm.getApplicationLabel(ai)),ai,system,ver));
        }
        result.sort((a,c)->a.name.compareToIgnoreCase(c.name));
        runOnUiThread(()->{apps.clear();apps.addAll(result);filter();});
      }catch(Exception e){runOnUiThread(()->alert("앱 목록 오류: "+e.getMessage()));}
    });
  }
  void filter(){
    if(adapter==null)return;
    String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
    visible.clear();
    for(App a:apps)if((showSystem.isChecked()||!a.system)&&(a.name.toLowerCase(Locale.ROOT).contains(q)||a.pkg.toLowerCase(Locale.ROOT).contains(q)))visible.add(a);
    count.setText("검색된 앱 "+visible.size()+"개");
    adapter.notifyDataSetChanged();
  }
  void export(App a){
    selected=a;
    boolean split=a.info.splitSourceDirs!=null&&a.info.splitSourceDirs.length>0;
    String version=a.version.isEmpty()?"unknown":a.version.replaceAll("[^A-Za-z0-9_.-]","_");
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");
    i.putExtra(Intent.EXTRA_TITLE,a.pkg+"_v"+version+(split?".apks":".apk"));
    try{startActivityForResult(i,SAVE);}catch(Exception e){alert("저장 위치 선택 실패: "+e.getMessage());}
  }
  @Override protected void onActivityResult(int code,int res,Intent data){
    super.onActivityResult(code,res,data);
    if(code!=SAVE||res!=RESULT_OK||data==null||data.getData()==null||selected==null)return;
    App app=selected;selected=null;
    Uri uri=data.getData();
    Toast.makeText(this,"APK 저장 중…",Toast.LENGTH_SHORT).show();
    executor.execute(()->{
      boolean split=app.info.splitSourceDirs!=null&&app.info.splitSourceDirs.length>0;
      try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
        if(out==null)throw new IOException("출력 파일을 열 수 없음");
        ApplicationInfo ai=pm.getApplicationInfo(app.pkg,0);
        if(split){
          try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(out))){
            add(zip,ai.sourceDir,"base.apk");
            for(int n=0;n<ai.splitSourceDirs.length;n++)add(zip,ai.splitSourceDirs[n],"split_"+n+".apk");
          }
        }else{
          try(InputStream in=new BufferedInputStream(new FileInputStream(ai.sourceDir))){copy(in,out);}
        }
        runOnUiThread(()->new AlertDialog.Builder(this).setTitle("저장 완료").setMessage(app.name+"\n"+(split?"분할 APK (.apks)":"일반 APK (.apk)"))
          .setNegativeButton("닫기",null).setPositiveButton("공유",(d,w)->share(uri,split)).show());
      }catch(Exception e){
        try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}
        runOnUiThread(()->alert("추출 실패: "+e.getMessage()));
      }
    });
  }
  void share(Uri uri,boolean split){
    Intent i=new Intent(Intent.ACTION_SEND);
    i.setType(split?"application/octet-stream":"application/vnd.android.package-archive");
    i.putExtra(Intent.EXTRA_STREAM,uri);
    i.setClipData(ClipData.newUri(getContentResolver(),"APK",uri));
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try{startActivity(Intent.createChooser(i,"APK 파일 공유"));}catch(Exception e){alert("공유 실패: "+e.getMessage());}
  }
  static void add(ZipOutputStream out,String path,String name)throws IOException{
    out.putNextEntry(new ZipEntry(name));
    try(InputStream in=new FileInputStream(path)){copy(in,out);}
    out.closeEntry();
  }
  static void copy(InputStream in,OutputStream out)throws IOException{
    byte[] buf=new byte[65536];int n;while((n=in.read(buf))>=0)if(n>0)out.write(buf,0,n);
  }
  void alert(String msg){new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("확인",null).show();}
  TextView label(String s,int size,int color,boolean bold){
    TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);return t;
  }
  GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(d(radius));return d;}
  int d(int dp){return (int)(getResources().getDisplayMetrics().density*dp+0.5f);}
  @Override public void onDestroy(){executor.shutdown();super.onDestroy();}
}