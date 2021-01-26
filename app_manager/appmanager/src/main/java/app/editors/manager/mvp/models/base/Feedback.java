package app.editors.manager.mvp.models.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Feedback {

    @SerializedName("url")
    @Expose
    private String url;
    @SerializedName("visible")
    @Expose
    private Boolean visible;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

}