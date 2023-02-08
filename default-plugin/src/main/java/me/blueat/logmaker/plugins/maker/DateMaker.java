package me.blueat.logmaker.plugins.maker;

import me.blueat.logmaker.plugin.api.maker.Maker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateMaker implements Maker<String> {
    private final String makerName;
    private final String format;
    private final String type;

    public DateMaker(String makerName, String format) {
        this.makerName = makerName;
        this.format = format;
        this.type = this.getClass().getName();
    }

    @Override
    public String getData() {
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.ENGLISH);
        return sdf.format(new Date());
    }

    @Override
    public String getMakerName() {
        return makerName;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public boolean isThread() {
        return false;
    }
}
