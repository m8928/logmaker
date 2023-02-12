package me.blueat.logmaker.plugins.maker;

import lombok.Data;
import me.blueat.logmaker.plugin.api.maker.Maker;
import me.blueat.logmaker.plugin.api.maker.MakerArgs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

@Data
public class DateMaker extends Maker<String> {
    private final String makerName;
    private String format;
    private final String type;
    private Map<String, Object> args;

    public DateMaker(String makerName, String type, Map<String, Object> args) {
        this.makerName = makerName;
        this.args = args;
        this.format = MakerArgs.toString(args.get("format"));
        this.type = type;
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
    public Thread getThread() {
        return null;
    }

    @Override
    public boolean isThread() {
        return false;
    }

    @Override
    public void update(Map<String, Object> args) {
        this.args = args;
        this.format = MakerArgs.toString(args.get("format"));
    }

    @Override
    public Map<String, Object> getArgs() {
        return this.args;
    }
}
