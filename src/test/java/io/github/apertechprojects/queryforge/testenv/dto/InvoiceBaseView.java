package io.github.apertechprojects.queryforge.testenv.dto;

import io.github.apertechprojects.queryforge.annotation.ForgeModel;

@ForgeModel
public class InvoiceBaseView {

    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
