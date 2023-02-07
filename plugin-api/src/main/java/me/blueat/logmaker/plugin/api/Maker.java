package me.blueat.logmaker.plugin.api;

public interface Maker<T> {
    T getData();
    String getMakerName();
    String getType();
    long getSize();
    boolean isThread();
}
