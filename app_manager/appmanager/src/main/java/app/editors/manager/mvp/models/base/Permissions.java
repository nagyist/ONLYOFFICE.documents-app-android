package app.editors.manager.mvp.models.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Permissions {

    @SerializedName("changeHistory")
    @Expose
    private Boolean changeHistory;
    @SerializedName("comment")
    @Expose
    private Boolean comment;
    @SerializedName("download")
    @Expose
    private Boolean download;
    @SerializedName("edit")
    @Expose
    private Boolean edit;
    @SerializedName("fillForms")
    @Expose
    private Boolean fillForms;
    @SerializedName("print")
    @Expose
    private Boolean print;
    @SerializedName("modifyFilter")
    @Expose
    private Boolean modifyFilter;
    @SerializedName("rename")
    @Expose
    private Boolean rename;
    @SerializedName("review")
    @Expose
    private Boolean review;

    public Boolean getChangeHistory() {
        return changeHistory;
    }

    public void setChangeHistory(Boolean changeHistory) {
        this.changeHistory = changeHistory;
    }

    public Boolean getComment() {
        return comment;
    }

    public void setComment(Boolean comment) {
        this.comment = comment;
    }

    public Boolean getDownload() {
        return download;
    }

    public void setDownload(Boolean download) {
        this.download = download;
    }

    public Boolean getEdit() {
        return edit;
    }

    public void setEdit(Boolean edit) {
        this.edit = edit;
    }

    public Boolean getFillForms() {
        return fillForms;
    }

    public void setFillForms(Boolean fillForms) {
        this.fillForms = fillForms;
    }

    public Boolean getPrint() {
        return print;
    }

    public void setPrint(Boolean print) {
        this.print = print;
    }

    public Boolean getModifyFilter() {
        return modifyFilter;
    }

    public void setModifyFilter(Boolean modifyFilter) {
        this.modifyFilter = modifyFilter;
    }

    public Boolean getRename() {
        return rename;
    }

    public void setRename(Boolean rename) {
        this.rename = rename;
    }

    public Boolean getReview() {
        return review;
    }

    public void setReview(Boolean review) {
        this.review = review;
    }

}
