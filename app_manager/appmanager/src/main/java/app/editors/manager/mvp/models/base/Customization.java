package app.editors.manager.mvp.models.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Customization {

    @SerializedName("customer")
    @Expose
    private Customer customer;
    @SerializedName("logo")
    @Expose
    private Logo logo;
    @SerializedName("about")
    @Expose
    private Boolean about;
    @SerializedName("feedback")
    @Expose
    private Feedback feedback;
    @SerializedName("mentionShare")
    @Expose
    private Boolean mentionShare;

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Logo getLogo() {
        return logo;
    }

    public void setLogo(Logo logo) {
        this.logo = logo;
    }

    public Boolean getAbout() {
        return about;
    }

    public void setAbout(Boolean about) {
        this.about = about;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    public Boolean getMentionShare() {
        return mentionShare;
    }

    public void setMentionShare(Boolean mentionShare) {
        this.mentionShare = mentionShare;
    }

}
