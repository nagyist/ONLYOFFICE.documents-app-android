package app.editors.manager.mvp.models.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Logo {

    @SerializedName("image")
    @Expose
    private String image;
    @SerializedName("url")
    @Expose
    private String url;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
