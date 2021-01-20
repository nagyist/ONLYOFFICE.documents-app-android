package app.editors.manager.mvp.models.response;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import app.editors.manager.mvp.models.base.Document;

public class ResponseDocument {
    @SerializedName("document")
    @Expose
    private Document document;
//    @SerializedName("editorConfig")
//    @Expose
//    private EditorConfig editorConfig;
    @SerializedName("token")
    @Expose
    private String token;
    @SerializedName("documentType")
    @Expose
    private String documentType;
    @SerializedName("type")
    @Expose
    private String type;

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

//    public EditorConfig getEditorConfig() {
//        return editorConfig;
//    }
//
//    public void setEditorConfig(EditorConfig editorConfig) {
//        this.editorConfig = editorConfig;
//    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


}
