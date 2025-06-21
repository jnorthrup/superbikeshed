package cursors

import (
	"fmt"
	"reflect"
	"testing"
	"time"

	"app/toolo" // Using app/ as the root for module paths
)

// MockKSPForAttention is a mock KSP for testing AttentionService.
type MockKSPForAttention struct {
	Name         string
	QueryResults []toolo.KSPResult
	QueryError   error
	LastQuery    toolo.KSPQuery
}

func (m *MockKSPForAttention) Query(query toolo.KSPQuery) ([]toolo.KSPResult, error) {
	m.LastQuery = query
	if m.QueryError != nil {
		return nil, m.QueryError
	}
	return m.QueryResults, nil
}
func (m *MockKSPForAttention) GetName() string                    { return m.Name }
func (m *MockKSPForAttention) GetCapabilities() map[string]string { return nil }

func TestNewAttentionService(t *testing.T) {
	delegator := toolo.NewKSPDelegator()
	service := NewAttentionService(delegator)

	if service == nil {
		t.Fatal("NewAttentionService returned nil")
	}
	if service.kspDelegator != delegator {
		t.Error("KSPDelegator not set correctly")
	}
	if service.maxHistory != 20 { // Default
		t.Errorf("Expected default maxHistory 20, got %d", service.maxHistory)
	}
}

func TestAttentionService_SetFocus(t *testing.T) {
	service := NewAttentionService(nil) // Delegator not needed for this test

	focus := service.SetFocus("test_type", "test_content", map[string]interface{}{"key": "value"})
	if focus == nil {
		t.Fatal("SetFocus returned nil focus")
	}
	if focus.Type != "test_type" || focus.Content != "test_content" {
		t.Errorf("Focus content not set correctly: %+v", focus)
	}
	if focus.Metadata["key"] != "value" {
		t.Errorf("Focus metadata not set correctly: %+v", focus.Metadata)
	}
	if focus.Timestamp.IsZero() {
		t.Error("Focus timestamp not set")
	}

	currentFocus := service.GetCurrentFocus()
	if currentFocus != focus {
		t.Error("GetCurrentFocus does not return the focus set by SetFocus")
	}

	history := service.GetFocusHistory()
	if len(history) != 1 || history[0] != focus {
		t.Errorf("Focus not added to history correctly: %+v", history)
	}
}

func TestAttentionService_FocusHistory(t *testing.T) {
	service := NewAttentionService(nil)
	service.SetMaxHistory(2)

	f1 := service.SetFocus("type1", "content1", nil)
	f2 := service.SetFocus("type2", "content2", nil)
	history := service.GetFocusHistory()
	if len(history) != 2 || history[0] != f1 || history[1] != f2 {
		t.Errorf("History incorrect after 2 focuses: %+v", history)
	}

	f3 := service.SetFocus("type3", "content3", nil)
	history = service.GetFocusHistory()
	if len(history) != 2 {
		t.Errorf("History length should be capped at maxHistory (2), got %d", len(history))
	}
	if history[0] != f2 || history[1] != f3 {
		t.Errorf("History not FIFO correctly after exceeding max: Expected [f2, f3], got [%+v, %+v]", history[0].ID, history[1].ID)
	}

	service.ClearFocus()
	if service.GetCurrentFocus() != nil {
		t.Error("CurrentFocus not nil after ClearFocus")
	}
	if len(service.GetFocusHistory()) != 0 {
		t.Error("FocusHistory not empty after ClearFocus")
	}
}

func TestAttentionService_SetMaxHistory(t *testing.T) {
	service := NewAttentionService(nil)
	for i := 0; i < 5; i++ {
		service.SetFocus(fmt.Sprintf("type%d", i), fmt.Sprintf("content%d", i), nil)
	}
	if len(service.GetFocusHistory()) != 5 {
		t.Fatalf("Expected 5 items in history, got %d", len(service.GetFocusHistory()))
	}

	service.SetMaxHistory(3)
	if len(service.GetFocusHistory()) != 3 {
		t.Errorf("History not trimmed to new maxHistory 3, got %d", len(service.GetFocusHistory()))
	}
	// Ensure it kept the latest 3
	lastFocusInTrimmedHistory := service.GetFocusHistory()[2]
	if lastFocusInTrimmedHistory.Type != "type4" { // 0,1,2,3,4 -> kept 2,3,4
		t.Errorf("History did not keep the correct latest items. Last item type: %s", lastFocusInTrimmedHistory.Type)
	}

	service.SetMaxHistory(-5) // Should default to 0
	if service.maxHistory != 0 {
		t.Errorf("maxHistory should be 0 if negative value is set, got %d", service.maxHistory)
	}
	if len(service.GetFocusHistory()) != 0 {
		t.Errorf("History not trimmed to 0, got %d", len(service.GetFocusHistory()))
	}
}

func TestAttentionService_QueryKSPsForCurrentFocus_NoFocus(t *testing.T) {
	delegator := toolo.NewKSPDelegator()
	service := NewAttentionService(delegator)
	_, err := service.QueryKSPsForCurrentFocus(nil)
	if err == nil {
		t.Error("Expected error when querying with no focus, got nil")
	}
}

func TestAttentionService_QueryKSPsForCurrentFocus_NoDelegator(t *testing.T) {
	service := NewAttentionService(nil) // No delegator
	service.SetFocus("test", "test", nil)
	_, err := service.QueryKSPsForCurrentFocus(nil)
	if err == nil {
		t.Error("Expected error when querying with nil delegator, got nil")
	} else if err.Error() != "KSP delegator not initialized" {
		t.Errorf("Unexpected error message: %s", err.Error())
	}
}

func TestAttentionService_QueryKSPsForCurrentFocus_Success(t *testing.T) {
	delegator := toolo.NewKSPDelegator()
	mockKSP := &MockKSPForAttention{
		Name:         "att_ksp",
		QueryResults: []toolo.KSPResult{{ID: "att_res1", Title: "Attention Result"}},
	}
	delegator.RegisterProvider(mockKSP, 1)

	service := NewAttentionService(delegator)
	focusTime := time.Now().Add(-5 * time.Minute) // Ensure timestamp is distinct

	// Use a variable for the focus object to ensure its timestamp is set before query
	focus := &AttentionFocus{
		ID:        "focus-123",
		Type:      "document",
		Content:   "attention query content",
		Metadata:  map[string]interface{}{"user_intent": "find_info", "custom_key": "custom_val"},
		Timestamp: focusTime,
	}
	service.currentFocus = focus // Manually set for predictable timestamp in test

	additionalCtx := map[string]interface{}{"session_id": "s123", "custom_key": "override_val"}
	results, err := service.QueryKSPsForCurrentFocus(additionalCtx)
	if err != nil {
		t.Fatalf("QueryKSPsForCurrentFocus failed: %v", err)
	}

	if len(results) != 1 || results[0].ID != "att_res1" {
		t.Errorf("Unexpected results: %+v", results)
	}

	// Verify the query sent to the KSP
	receivedQuery := mockKSP.LastQuery
	expectedQueryText := "attention query content (Intent: find_info)"
	if receivedQuery.QueryText != expectedQueryText {
		t.Errorf("Expected query text '%s', got '%s'", expectedQueryText, receivedQuery.QueryText)
	}

	// Verify context
	if receivedQuery.Context["focus_type"] != "document" {
		t.Errorf("Expected context focus_type 'document', got '%v'", receivedQuery.Context["focus_type"])
	}
	if receivedQuery.Context["user_intent"] != "find_info" {
		t.Errorf("Expected context user_intent 'find_info', got '%v'", receivedQuery.Context["user_intent"])
	}
	if ts, ok := receivedQuery.Context["timestamp"].(time.Time); !ok || !ts.Equal(focusTime) {
		t.Errorf("Expected context timestamp '%v', got '%v'", focusTime, receivedQuery.Context["timestamp"])
	}
	if receivedQuery.Context["session_id"] != "s123" {
		t.Errorf("Expected additional context session_id 's123', got '%v'", receivedQuery.Context["session_id"])
	}
	if receivedQuery.Context["custom_key"] != "override_val" { // Additional context should override metadata
		t.Errorf("Expected additional context 'custom_key' to override metadata, got '%v'", receivedQuery.Context["custom_key"])
	}
}

func TestAttentionService_QueryKSPsForCurrentFocus_DelegatorError(t *testing.T) {
	delegator := toolo.NewKSPDelegator()
	mockKSP := &MockKSPForAttention{
		Name:       "err_ksp",
		QueryError: fmt.Errorf("KSP failed internally"),
	}
	delegator.RegisterProvider(mockKSP, 1)

	service := NewAttentionService(delegator)
	service.SetFocus("error_test", "query that fails", nil)

	_, err := service.QueryKSPsForCurrentFocus(nil)
	if err == nil {
		t.Fatal("Expected error from QueryKSPsForCurrentFocus when KSP returns error, got nil")
	}
	// Check if the error is wrapped as expected
	// The exact message depends on how KSPDelegator formats it.
	// For now, just check it's not nil. A more robust test would check parts of the error string.
	expectedErrorSubstring := "failed to delegate KSP query"
	if !reflect.DeepEqual(err.Error()[:len(expectedErrorSubstring)], expectedErrorSubstring) &&
		!reflect.DeepEqual(err.Error()[:len("query failed across all providers:")], "query failed across all providers:") { // KSPDelegator might return this
		t.Errorf("Error message '%s' did not contain expected substring '%s' or 'query failed across all providers'", err.Error(), expectedErrorSubstring)
	}
}

// Illustrative test for ProcessKSPResultsForAttention - usually this would have side effects to check
func TestAttentionService_ProcessKSPResultsForAttention(t *testing.T) {
	service := NewAttentionService(nil)
	results := []toolo.KSPResult{
		{ID: "r1", Title: "R1", Data: map[string]interface{}{"ksp_source": "test_ksp"}},
	}
	// This function currently just prints. In a real app, it would modify state
	// or interact with other components, which would be tested here.
	// For now, just call it to ensure no panics.
	service.ProcessKSPResultsForAttention(results)
	// If it had observable side effects, assertions would go here.
}
