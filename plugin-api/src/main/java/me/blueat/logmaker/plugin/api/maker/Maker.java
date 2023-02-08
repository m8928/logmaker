package me.blueat.logmaker.plugin.api.maker;

public interface Maker<T> {
    T getData();
    String getMakerName();
    String getType();
    long getSize();
    boolean isThread();
}
