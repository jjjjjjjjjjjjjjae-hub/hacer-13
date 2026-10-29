package com.hacer13.update;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class SignedUpdaterTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private KeyPair pair; private File directory;private SignedUpdater engine;private Map<String,byte[]> blobs;private int requests;
    @Before public void setup()throws Exception{pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();directory=temp.newFolder();blobs=new HashMap<>();engine=client(p->{});}
    private SignedUpdater client(SignedUpdater.Fault fault)throws Exception{return new SignedUpdater(directory,Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),(url,max)->{requests++;byte[] b=blobs.get(url);if(b==null)throw new IOException("offline");if(b.length>max)throw new IOException("oversize");return b;},fault);}
    private byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private Map<String,byte[]> files(String title)throws Exception{Map<String,byte[]> f=new LinkedHashMap<>();f.put("menu.json",bytes(new JSONObject(Trust.FALLBACK).put("title",title).toString()));f.put("theme.json",bytes("{\"accent\":\"#40E4BE\"}"));return f;}
    private JSONObject manifest(int revision,Map<String,byte[]> files)throws Exception{
        JSONArray array=new JSONArray();
        for(Map.Entry<String,byte[]> f:files.entrySet()){String h=SignedUpdater.sha256(f.getValue());blobs.put(Trust.BASE+"blobs/"+h,f.getValue());array.put(new JSONObject().put("path",f.getKey()).put("sha256",h).put("size",f.getValue().length));}
        return new JSONObject().put("schema",2).put("channel","lac").put("revision",revision).put("min_core",1).put("files",array);
    }
    private byte[] sign(JSONObject m)throws Exception{byte[] p=bytes(m.toString());Signature sig=Signature.getInstance("Ed25519");sig.initSign(pair.getPrivate());sig.update(p);return bytes(new JSONObject().put("algorithm","Ed25519").put("payload_b64",Base64.getEncoder().encodeToString(p)).put("signature_b64",Base64.getEncoder().encodeToString(sig.sign())).toString());}
    private byte[] release(int revision,String title)throws Exception{return sign(manifest(revision,files(title)));}
    private void reject(byte[] b)throws Exception{assertThrows(Exception.class,()->engine.install(b));assertEquals(0,engine.current().revision);}
    @Test public void signedInstallAndChangedFilesOnly()throws Exception{
        engine.install(release(1,"one"));assertEquals(2,requests);assertEquals("one",engine.current().menu.getString("title"));
        engine.install(release(2,"two"));assertEquals(3,requests);assertEquals(2,engine.current().revision);
    }
    @Test public void noDownloadOnReplay()throws Exception{byte[] b=release(4,"four");engine.install(b);int before=requests;engine.install(b);engine.install(release(3,"old"));assertEquals(before,requests);assertEquals(4,engine.current().revision);}
    @Test public void corruptedSignature()throws Exception{JSONObject env=new JSONObject(new String(release(1,"one"),StandardCharsets.UTF_8));byte[] sig=Base64.getDecoder().decode(env.getString("signature_b64"));sig[0]^=1;env.put("signature_b64",Base64.getEncoder().encodeToString(sig));reject(bytes(env.toString()));assertEquals(0,requests);}
    @Test public void wrongPinnedKey()throws Exception{byte[] b=release(1,"one");pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();engine=client(p->{});reject(b);}
    @Test public void hashMismatchLeavesOldVersion()throws Exception{engine.install(release(1,"one"));byte[] next=release(2,"two");for(String k:new ArrayList<>(blobs.keySet()))blobs.put(k,bytes("corrupted"));assertThrows(Exception.class,()->engine.install(next));assertEquals(1,engine.current().revision);}
    @Test public void offlineKeepsOldVersion()throws Exception{engine.install(release(1,"one"));byte[] next=release(2,"two");blobs.clear();assertThrows(IOException.class,()->engine.install(next));assertEquals(1,engine.current().revision);}
    @Test public void rollbackRetainsReplayFloorAcrossRestart()throws Exception{engine.install(release(1,"one"));engine.install(release(2,"two"));assertTrue(engine.rollback());engine=client(p->{});assertEquals(1,engine.current().revision);assertEquals(2,engine.current().highest);engine.install(release(2,"two"));assertEquals(1,engine.current().revision);engine.install(release(3,"three"));assertEquals(3,engine.current().revision);}
    @Test public void firstUpdateCanRollbackToBuiltin()throws Exception{engine.install(release(1,"one"));assertTrue(engine.rollback());assertEquals(0,engine.current().revision);assertEquals(1,engine.current().highest);}
    @Test public void crashBeforePointerCommitKeepsOld()throws Exception{engine.install(release(1,"one"));byte[] b=release(2,"two");SignedUpdater faulty=client(p->{if(p.equals("before_commit"))throw new IOException("simulated crash");});assertThrows(IOException.class,()->faulty.install(b));engine=client(p->{});assertEquals(1,engine.current().revision);assertEquals(1,engine.current().highest);engine.install(b);assertEquals(2,engine.current().revision);}
    @Test public void crashAfterStagingKeepsOld()throws Exception{engine.install(release(1,"one"));byte[] b=release(2,"two");SignedUpdater faulty=client(p->{if(p.equals("staged"))throw new IOException("simulated crash");});assertThrows(IOException.class,()->faulty.install(b));assertEquals(1,engine.current().revision);}
    @Test public void corruptedActiveAutomaticallyRecoversBackup()throws Exception{engine.install(release(1,"one"));engine.install(release(2,"two"));JSONObject s=new JSONObject(Files.readString(directory.toPath().resolve("state.json")));Files.writeString(directory.toPath().resolve(s.getString("active")).resolve("menu.json"),"bad");assertEquals(1,engine.current().revision);assertEquals(2,engine.current().highest);engine.install(release(2,"two"));assertEquals(1,engine.current().revision);}
    @Test public void malformedStateFailsClosed()throws Exception{engine.current();Files.writeString(directory.toPath().resolve("state.json"),"{");assertThrows(Exception.class,()->engine.install(release(1,"one")));}
    @Test public void missingStateFailsClosed()throws Exception{engine.current();Files.delete(directory.toPath().resolve("state.json"));assertThrows(SecurityException.class,()->engine.install(release(1,"one")));}
    @Test public void pathTraversalRejectedBeforeDownload()throws Exception{Map<String,byte[]> f=files("x");f.put("../evil.dex",bytes("code"));reject(sign(manifest(1,f)));assertEquals(0,requests);}
    @Test public void executableInventoryRejected()throws Exception{Map<String,byte[]> f=files("x");f.put("resources/plugin.dex",bytes("code"));reject(sign(manifest(1,f)));}
    @Test public void unsignedExtraCommandsRejected()throws Exception{JSONObject m=manifest(1,files("x"));m.put("command","shell");reject(sign(m));}
    @Test public void executableMenuFieldRejected()throws Exception{Map<String,byte[]> f=files("x");JSONObject m=new JSONObject(new String(f.get("menu.json"),StandardCharsets.UTF_8));m.put("script","alert(1)");f.put("menu.json",bytes(m.toString()));reject(sign(manifest(1,f)));}
    @Test public void nonDemoFeatureRejected()throws Exception{Map<String,byte[]> f=files("x");JSONObject m=new JSONObject(new String(f.get("menu.json"),StandardCharsets.UTF_8));m.getJSONArray("features").getJSONObject(0).put("kind","native");f.put("menu.json",bytes(m.toString()));reject(sign(manifest(1,f)));}
    @Test public void duplicatePathsRejected()throws Exception{JSONObject m=manifest(1,files("x"));m.getJSONArray("files").put(m.getJSONArray("files").getJSONObject(0));reject(sign(m));}
    @Test public void invalidChannelRejected()throws Exception{JSONObject m=manifest(1,files("x")).put("channel","other");reject(sign(m));}
    @Test public void incompatibleCoreRejected()throws Exception{reject(sign(manifest(1,files("x")).put("min_core",2)));}
    @Test public void fractionalRevisionRejected()throws Exception{reject(sign(manifest(1,files("x")).put("revision",1.5)));}
    @Test public void oversizedInventoryRejected()throws Exception{JSONObject m=manifest(1,files("x"));m.getJSONArray("files").getJSONObject(0).put("size",Integer.MAX_VALUE);reject(sign(m));}
    @Test public void boundedReadRejectsOversize()throws Exception{assertThrows(IOException.class,()->SignedUpdater.bounded(new ByteArrayInputStream(new byte[11]),10));}
    @Test public void pngDimensionsBounded()throws Exception{byte[] p=new byte[33];byte[] head={(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82};System.arraycopy(head,0,p,0,head.length);java.nio.ByteBuffer.wrap(p,16,4).putInt(999999);java.nio.ByteBuffer.wrap(p,20,4).putInt(1);assertThrows(SecurityException.class,()->SignedUpdater.validateData("resources/badge.png",p));}
    @Test public void concurrentInstallIsSerialized()throws Exception{byte[] one=release(1,"one"),two=release(2,"two");ExecutorService pool=Executors.newFixedThreadPool(2);try{Future<?> a=pool.submit(()->{try{engine.install(one);}catch(Exception e){throw new RuntimeException(e);}});Future<?> b=pool.submit(()->{try{engine.install(two);}catch(Exception e){throw new RuntimeException(e);}});a.get();b.get();assertEquals(2,engine.current().highest);assertEquals(2,engine.current().revision);}finally{pool.shutdownNow();}}
    @Test public void arbitraryNetworkHostsRejected()throws Exception{assertThrows(SecurityException.class,()->new SignedUpdater.HttpsTransport().get("https://example.com/data",10));}
    @Test public void invalidSpkiRejected()throws Exception{assertThrows(SecurityException.class,()->new SignedUpdater(directory,Base64.getEncoder().encodeToString(new byte[44]),(u,n)->new byte[0],p->{}));}
    @Test public void rfc8032EmptyMessageVector()throws Exception{
        byte[] pk=hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] sig=hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
        assertTrue(org.bouncycastle.math.ec.rfc8032.Ed25519.verify(sig,0,pk,0,new byte[0],0,0));sig[63]^=1;assertFalse(org.bouncycastle.math.ec.rfc8032.Ed25519.verify(sig,0,pk,0,new byte[0],0,0));
    }
    private byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    @Test public void currentMenuDoesNotWaitForSlowNetwork()throws Exception{
        byte[] next=release(1,"one");CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1);
        SignedUpdater slow=new SignedUpdater(directory,Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),(url,max)->{entered.countDown();if(!resume.await(5,TimeUnit.SECONDS))throw new IOException("test timeout");return blobs.get(url);},p->{});
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{Future<?> update=pool.submit(()->{try{slow.install(next);}catch(Exception e){throw new RuntimeException(e);}});assertTrue(entered.await(5,TimeUnit.SECONDS));Future<SignedUpdater.Snapshot> read=pool.submit(()->engine.current());assertEquals(0,read.get(1,TimeUnit.SECONDS).revision);resume.countDown();update.get(5,TimeUnit.SECONDS);}finally{resume.countDown();pool.shutdownNow();}
    }
}
