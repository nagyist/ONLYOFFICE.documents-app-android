package app.editors.manager.mvp.models.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Customer {

    @SerializedName("logo")
    @Expose
    private String logo;
    @SerializedName("name")
    @Expose
    private String name;

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
