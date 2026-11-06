package com.sya.tube.legacy;
public final class SearchItem {
    private final CharSequence title;
    private SearchItem(CharSequence title) { this.title=title; }
    public CharSequence getTitle() { return title; }
    public static final class Builder {
        private CharSequence title="";
        public Builder setTitle(CharSequence title) { this.title=title; return this; }
        public SearchItem build() { return new SearchItem(title); }
    }
}
