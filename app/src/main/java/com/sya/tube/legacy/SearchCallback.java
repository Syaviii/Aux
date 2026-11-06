package com.sya.tube.legacy;
public interface SearchCallback {
    void onSearchItemSelected(SearchItem item);
    boolean onSearchSubmitted(String query);
    void onSearchTextChanged(String query);
    default void onSearchClosed() { }
}
