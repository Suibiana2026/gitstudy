package com.bigData.main.controller.API;

public class ChatRequest {
    private String message;
    private String mode; // optional，可根据前端模式判断

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
