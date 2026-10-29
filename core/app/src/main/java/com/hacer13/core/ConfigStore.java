package com.hacer13.core;
import android.content.Context;
import com.hacer13.update.*;
import org.json.JSONObject;
import java.io.File;
public final class ConfigStore {
    private ConfigStore(){}
    public static SignedUpdater engine(Context c)throws Exception{return new SignedUpdater(new File(c.getFilesDir(),"h13-v2"));}
    public static JSONObject current(Context c){try{SignedUpdater.Snapshot s=engine(c).current();return s.menu.put("revision",s.revision);}catch(Exception e){try{return new JSONObject(Trust.FALLBACK).put("revision",0);}catch(Exception ignored){return new JSONObject();}}}
    public static boolean rollback(Context c){try{return engine(c).rollback();}catch(Exception e){return false;}}
}
