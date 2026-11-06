// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import com.thekirankumar.youtubeauto2.application.MyApplication;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class LocalFileBrowser {
    private static String readUtf8(InputStream stream) throws IOException { ByteArrayOutputStream output=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int count; while((count=stream.read(buffer))!=-1)output.write(buffer,0,count); return output.toString("UTF-8"); }
    private static String escape(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    public static WebResourceResponse intercept(String url) {
        Uri uri=Uri.parse(url);
        if(!"file".equals(uri.getScheme()) || uri.getPath()==null || uri.getPath().startsWith("/android_asset/")) return null;
        File directory=new File(uri.getPath());
        if(!directory.isDirectory()) return null;
        String css="body{background:#303030;color:white}a{color:white}td{padding:20px}";
        try(InputStream stream=MyApplication.getInstance().getAssets().open("filebrowser.css")) { css=readUtf8(stream); } catch(IOException ignored) { }
        StringBuilder page=new StringBuilder("<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>Internal storage</title><style>").append(css).append("</style></head><body data-tube-file-browser='true'><h3>").append(escape(directory.getPath())).append("</h3>");
        File parent=directory.getParentFile();
        if(parent!=null) page.append("<a class='icon up' href='").append(escape(Uri.fromFile(parent).toString()+"/")).append("'>[parent directory]</a>");
        File[] entries=directory.listFiles();
        if(entries==null) page.append("<p>This folder is unavailable. Allow file access in Aux's app permissions.</p>");
        else if(entries.length==0) page.append("<p>This folder is empty.</p>");
        else {
            Arrays.sort(entries,Comparator.comparing(File::isFile).thenComparing(File::getName,String.CASE_INSENSITIVE_ORDER));
            page.append("<table><thead><tr><th>Name</th><th>Size</th><th>Date Modified</th></tr></thead><tbody>");
            for(File file:entries) page.append("<tr><td><a class='icon ").append(file.isDirectory()?"dir":"file").append("' href='").append(escape(Uri.fromFile(file).toString()+(file.isDirectory()?"/":""))).append("'>").append(escape(file.getName())).append("</a></td><td class='detailsColumn'>").append(file.isDirectory()?"":Long.toString(file.length()/1024)+" KB").append("</td><td class='detailsColumn'>").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date(file.lastModified()))).append("</td></tr>");
            page.append("</tbody></table>");
        }
        page.append("</body></html>");
        return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(page.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
