package com.sya.tube.legacy;
import android.app.AlertDialog;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
import android.content.Context;
import com.thekirankumar.youtubeauto2.utils.CarEditText;
import com.sya.tube.car.CarBridge;
import java.util.List;

// Bridges CarStream's search events to the actual host-drawn Android Auto keyboard.
public final class SearchController {
    private final Context activity;
    private SearchCallback callback;
    private String hint="Search";
    private AlertDialog dialog;
    private final com.google.android.apps.auto.sdk.SearchController projection;
    private final com.google.android.gms.car.input.InputManager inputManager;
    public SearchController(Context activity) { this(activity, null, null); }
    public SearchController(Context activity, com.google.android.apps.auto.sdk.SearchController projection, com.google.android.gms.car.input.InputManager inputManager) {
        this.activity=activity; this.projection=projection; this.inputManager=inputManager;
    }
    public void setSearchCallback(SearchCallback callback) {
        this.callback=callback;
        if(projection!=null) projection.setSearchCallback(new com.google.android.apps.auto.sdk.SearchCallback() {
            @Override public void onSearchItemSelected(com.google.android.apps.auto.sdk.SearchItem item) { callback.onSearchSubmitted(item.getTitle().toString()); }
            @Override public boolean onSearchSubmitted(String query) { return callback.onSearchSubmitted(query); }
            @Override public void onSearchTextChanged(String query) { callback.onSearchTextChanged(query); }
            @Override public void onSearchStop() { callback.onSearchClosed(); }
        });
    }
    public void setSearchHint(String hint) { this.hint=hint; if(projection!=null) projection.setSearchHint(hint); else CarBridge.updateSearchHint(hint); }
    public void showSearchBox() {
        if(projection!=null) { projection.showSearchBox(); projection.startSearch(""); return; }
        if(CarBridge.showSearch(hint,callback)) return;
        EditText input=new EditText(activity); input.setSingleLine(); input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        dialog=new AlertDialog.Builder(activity).setTitle(hint).setView(input)
            .setPositiveButton("Search",(d,w)->{ if(callback!=null) callback.onSearchSubmitted(input.getText().toString()); })
            .setNegativeButton(android.R.string.cancel,null).create();
        input.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH || action==EditorInfo.IME_ACTION_DONE || (event!=null&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER&&event.getAction()==android.view.KeyEvent.ACTION_UP)) { if(callback!=null) callback.onSearchSubmitted(input.getText().toString()); hideSearchBox(); return true; } return false;
        });
        dialog.show(); input.requestFocus();
    }
    public void hideSearchBox() { if(projection!=null) projection.hideSearchBox(); else CarBridge.hideSearch(); if(dialog!=null) { dialog.dismiss(); dialog=null; } }
    public void setSearchItems(List<SearchItem> items) {
        if(projection!=null) {
            java.util.ArrayList<com.google.android.apps.auto.sdk.SearchItem> projected=new java.util.ArrayList<>();
            for(SearchItem item:items) projected.add(new com.google.android.apps.auto.sdk.SearchItem.Builder().setTitle(item.getTitle()).build());
            projection.setSearchItems(projected);
        } else CarBridge.updateSearchItems(items);
    }
    public void startInput(CarEditText edit) {
        if(inputManager!=null) { inputManager.startInput(new com.google.android.gms.car.input.CarEditable() {
            @Override public android.view.inputmethod.InputConnection onCreateInputConnection(EditorInfo info) { return edit.onCreateInputConnection(info); }
            @Override public void setCarEditableListener(com.google.android.gms.car.input.CarEditableListener listener) { }
            @Override public void setInputEnabled(boolean enabled) { edit.setEnabled(enabled); }
        }); return; }
        SearchCallback inputCallback=new SearchCallback() {
            @Override public void onSearchItemSelected(SearchItem item) { onSearchSubmitted(item.getTitle().toString()); }
            @Override public boolean onSearchSubmitted(String value) {
                edit.setText(value); edit.onEditorAction(EditorInfo.IME_ACTION_DONE); return true;
            }
            @Override public void onSearchTextChanged(String value) { edit.setText(value); }
        };
        if(!CarBridge.showSearch("Enter text",inputCallback)) {
            edit.setVisibility(android.view.View.VISIBLE); edit.requestFocus();
            ((android.view.inputmethod.InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(edit,0);
        }
    }
    public void stopInput() { if(inputManager!=null) inputManager.stopInput(); else CarBridge.hideSearch(); }
}
