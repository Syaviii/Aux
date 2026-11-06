// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;
import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.util.*;
public final class AdBlocker {
    private static volatile Set<String> hosts=Collections.emptySet();
    public static void init(Context context) {
        new Thread(()->{ Set<String> loaded=new HashSet<>();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open("hosts.txt")))) {
                String line; while((line=reader.readLine())!=null) {
                    line=line.trim(); if(!line.isEmpty()&&!line.startsWith("#")) loaded.add(line.toLowerCase(Locale.ROOT));
                } hosts=Collections.unmodifiableSet(loaded);
            } catch(IOException e) { android.util.Log.w("Tube","Ad host list unavailable"); }
        },"TubeAdHosts").start();
    }
    public static boolean isAd(String url) {
        String host=Uri.parse(url).getHost(); if(host==null) return false;
        host=host.toLowerCase(Locale.ROOT);
        while(!host.isEmpty()) { if(hosts.contains(host)) return true; int dot=host.indexOf('.'); if(dot<0) break; host=host.substring(dot+1); }
        return false;
    }
    public static WebResourceResponse createEmptyResource() { return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0])); }
}
