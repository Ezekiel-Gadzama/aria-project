package com.aria.api.dto;

/**
 * Response for AI suggestion with optional reference information
 */
public class SuggestionResponse {
    private String suggestion;
    private ReferenceInfo reference;
    
    public SuggestionResponse() {}
    
    public SuggestionResponse(String suggestion) {
        this.suggestion = suggestion;
    }
    
    public SuggestionResponse(String suggestion, ReferenceInfo reference) {
        this.suggestion = suggestion;
        this.reference = reference;
    }
    
    public String getSuggestion() {
        return suggestion;
    }
    
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
    
    public ReferenceInfo getReference() {
        return reference;
    }
    
    public void setReference(ReferenceInfo reference) {
        this.reference = reference;
    }
    
    /**
     * Reference information for a message that inspired the suggestion
     */
    public static class ReferenceInfo {
        private Integer dialogId;
        private Long messageId;
        private String messageText;
        private String dialogName;
        private java.sql.Timestamp messageTimestamp;
        
        public ReferenceInfo() {}
        
        public ReferenceInfo(Integer dialogId, Long messageId, String messageText, String dialogName, java.sql.Timestamp messageTimestamp) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.messageText = messageText;
            this.dialogName = dialogName;
            this.messageTimestamp = messageTimestamp;
        }
        
        public Integer getDialogId() {
            return dialogId;
        }
        
        public void setDialogId(Integer dialogId) {
            this.dialogId = dialogId;
        }
        
        public Long getMessageId() {
            return messageId;
        }
        
        public void setMessageId(Long messageId) {
            this.messageId = messageId;
        }
        
        public String getMessageText() {
            return messageText;
        }
        
        public void setMessageText(String messageText) {
            this.messageText = messageText;
        }
        
        public String getDialogName() {
            return dialogName;
        }
        
        public void setDialogName(String dialogName) {
            this.dialogName = dialogName;
        }
        
        public java.sql.Timestamp getMessageTimestamp() {
            return messageTimestamp;
        }
        
        public void setMessageTimestamp(java.sql.Timestamp messageTimestamp) {
            this.messageTimestamp = messageTimestamp;
        }
    }
}

