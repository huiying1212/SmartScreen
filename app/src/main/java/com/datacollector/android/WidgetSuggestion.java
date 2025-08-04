package com.datacollector.android;

/**
 * 表示LLM返回的智能建议Widget
 */
public class WidgetSuggestion {
    private String type;
    private String app;
    private String action;
    
    public WidgetSuggestion() {
    }
    
    public WidgetSuggestion(String type, String app, String action) {
        this.type = type;
        this.app = app;
        this.action = action;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getApp() {
        return app;
    }
    
    public void setApp(String app) {
        this.app = app;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
} 