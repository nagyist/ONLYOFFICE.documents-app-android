package app.editors.manager.mvp.models.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Plugins {

    @SerializedName("pluginsData")
    @Expose
    private List<String> pluginsData = null;

    public List<String> getPluginsData() {
        return pluginsData;
    }

    public void setPluginsData(List<String> pluginsData) {
        this.pluginsData = pluginsData;
    }

}
