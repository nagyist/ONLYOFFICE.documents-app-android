package app.editors.manager.mvp.models.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import app.editors.manager.mvp.models.user.User;

public class EditorConfig {

    @SerializedName("callbackUrl")
    @Expose
    private String callbackUrl;
    @SerializedName("plugins")
    @Expose
    private Plugins plugins;
    @SerializedName("customization")
    @Expose
    private Customization customization;
    @SerializedName("user")
    @Expose
    private User user;
    @SerializedName("lang")
    @Expose
    private String lang;
    @SerializedName("mode")
    @Expose
    private String mode;

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public Plugins getPlugins() {
        return plugins;
    }

    public void setPlugins(Plugins plugins) {
        this.plugins = plugins;
    }

    public Customization getCustomization() {
        return customization;
    }

    public void setCustomization(Customization customization) {
        this.customization = customization;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

}
