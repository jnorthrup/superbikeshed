package trikeshed

import (
	"reflect"
	"testing"

	"app/cursors"
	"app/toolo"
)

// MockKSPForDSEL is a mock KSP for DSEL engine testing.
type MockKSPForDSEL struct {
	Name      string
	QueryFunc func(query toolo.KSPQuery) ([]toolo.KSPResult, error)
	LastQuery toolo.KSPQuery
}

func (m *MockKSPForDSEL) Query(query toolo.KSPQuery) ([]toolo.KSPResult, error) {
	m.LastQuery = query
	if m.QueryFunc != nil {
		return m.QueryFunc(query)
	}
	return []toolo.KSPResult{{ID: "dsel_res", Title: "DSEL Result", Data: map[string]interface{}{"ksp_source_test": m.Name}}}, nil
}
func (m *MockKSPForDSEL) GetName() string                    { return m.Name }
func (m *MockKSPForDSEL) GetCapabilities() map[string]string { return nil }

func setupDSELEngineWithMocks(t *testing.T) (*DSELEngine, *toolo.KSPDelegator, *cursors.AttentionService, *MockKSPForDSEL) {
	kspDelegator := toolo.NewKSPDelegator()
	mockKSP := &MockKSPForDSEL{Name: "dsel_mock_ksp"}
	err := kspDelegator.RegisterProvider(mockKSP, 1)
	if err != nil {
		t.Fatalf("Failed to register mock KSP: %v", err)
	}

	attentionService := cursors.NewAttentionService(kspDelegator) // Can be nil if not testing attention integration
	dselEngine := NewDSELEngine(kspDelegator, attentionService)

	return dselEngine, kspDelegator, attentionService, mockKSP
}

func TestNewDSELEngine(t *testing.T) {
	delegator := toolo.NewKSPDelegator()
	attService := cursors.NewAttentionService(delegator)
	engine := NewDSELEngine(delegator, attService)

	if engine == nil {
		t.Fatal("NewDSELEngine returned nil")
	}
	if engine.kspDelegator != delegator {
		t.Error("KSPDelegator not set correctly")
	}
	if engine.attentionService != attService {
		t.Error("AttentionService not set correctly")
	}
	if len(engine.elements) != 0 {
		t.Error("Initial elements map not empty")
	}
	if len(engine.currentContext) != 0 {
		t.Error("Initial context map not empty")
	}
}

func TestDSELEngine_ExecuteCommand_Unknown(t *testing.T) {
	engine, _, _, _ := setupDSELEngineWithMocks(t)
	cmd := DSELCommand{Type: "UNKNOWN_COMMAND"}
	_, err := engine.ExecuteCommand(cmd)
	if err == nil {
		t.Error("Expected error for unknown command type, got nil")
	}
}

func TestDSELEngine_DefineAttentionElement(t *testing.T) {
	engine, _, _, _ := setupDSELEngineWithMocks(t)

	params := map[string]interface{}{
		"name":        "testElement",
		"description": "A test attention element.",
	}
	cmd := DSELCommand{Type: CommandDefineAttentionElement, Params: params}

	msg, err := engine.ExecuteCommand(cmd)
	if err != nil {
		t.Fatalf("DefineAttentionElement failed: %v", err)
	}
	if msg != "Attention element 'testElement' defined" {
		t.Errorf("Unexpected success message: %s", msg)
	}

	element, exists := engine.GetAttentionElement("testElement")
	if !exists {
		t.Fatal("Attention element 'testElement' not found after definition")
	}
	if element.Name != "testElement" || element.Description != "A test attention element." {
		t.Errorf("Attention element properties not set correctly: %+v", element)
	}

	// Test defining duplicate
	_, err = engine.ExecuteCommand(cmd)
	if err == nil {
		t.Error("Expected error when defining duplicate attention element, got nil")
	}

	// Test missing name
	invalidParams := map[string]interface{}{"description": "A test attention element."}
	cmdInvalid := DSELCommand{Type: CommandDefineAttentionElement, Params: invalidParams}
	_, err = engine.ExecuteCommand(cmdInvalid)
	if err == nil {
		t.Error("Expected error when defining attention element with missing name, got nil")
	}
}

func TestDSELEngine_SetContext(t *testing.T) {
	engine, _, _, _ := setupDSELEngineWithMocks(t)

	contextData := map[string]interface{}{"key1": "value1", "projectID": 123}
	cmd := DSELCommand{Type: CommandSetContext, Params: map[string]interface{}{"data": contextData}}

	msg, err := engine.ExecuteCommand(cmd)
	if err != nil {
		t.Fatalf("SetContext failed: %v", err)
	}
	if msg != "Context updated" {
		t.Errorf("Unexpected success message: %s", msg)
	}

	currentCtx := engine.GetCurrentContext()
	if val, ok := currentCtx["key1"].(string); !ok || val != "value1" {
		t.Errorf("Context key1 not set correctly: %v", currentCtx["key1"])
	}
	if val, ok := currentCtx["projectID"].(int); !ok || val != 123 {
		t.Errorf("Context projectID not set correctly: %v", currentCtx["projectID"])
	}

	// Test updating context
	updatedContextData := map[string]interface{}{"key1": "newValue1", "key2": true}
	cmdUpdate := DSELCommand{Type: CommandSetContext, Params: map[string]interface{}{"data": updatedContextData}}
	engine.ExecuteCommand(cmdUpdate)

	currentCtx = engine.GetCurrentContext()
	if val, ok := currentCtx["key1"].(string); !ok || val != "newValue1" {
		t.Errorf("Context key1 not updated correctly: %v", currentCtx["key1"])
	}
	if val, ok := currentCtx["key2"].(bool); !ok || !val {
		t.Errorf("Context key2 not added correctly: %v", currentCtx["key2"])
	}
	if val, ok := currentCtx["projectID"].(int); !ok || val != 123 { // Should still exist
		t.Errorf("Context projectID was lost after update: %v", currentCtx["projectID"])
	}

	// Test invalid data param
	cmdInvalid := DSELCommand{Type: CommandSetContext, Params: map[string]interface{}{"data": "not a map"}}
	_, err = engine.ExecuteCommand(cmdInvalid)
	if err == nil {
		t.Error("Expected error for SetContext with invalid data type, got nil")
	}
}

func TestDSELEngine_EngageKSP_Delegator(t *testing.T) {
	engine, _, _, mockKSP := setupDSELEngineWithMocks(t)

	// Set some engine context
	engine.ExecuteCommand(DSELCommand{
		Type:   CommandSetContext,
		Params: map[string]interface{}{"data": map[string]interface{}{"dsel_ctx_key": "dsel_val"}},
	})

	cmdParams := map[string]interface{}{
		"queryText": "test KSP query via DSEL",
		"context":   map[string]interface{}{"cmd_ctx_key": "cmd_val"},
	}
	cmd := DSELCommand{Type: CommandEngageKSP, Params: cmdParams}

	results, err := engine.ExecuteCommand(cmd)
	if err != nil {
		t.Fatalf("EngageKSP failed: %v", err)
	}

	kspResults, ok := results.([]toolo.KSPResult)
	if !ok {
		t.Fatalf("EngageKSP did not return []toolo.KSPResult: %T", results)
	}
	if len(kspResults) != 1 || kspResults[0].ID != "dsel_res" {
		t.Errorf("Unexpected KSP results: %+v", kspResults)
	}

	// Verify query passed to KSP
	if mockKSP.LastQuery.QueryText != "test KSP query via DSEL" {
		t.Errorf("Unexpected query text: %s", mockKSP.LastQuery.QueryText)
	}
	if mockKSP.LastQuery.Context["dsel_ctx_key"] != "dsel_val" {
		t.Errorf("DSEL context not passed: %v", mockKSP.LastQuery.Context)
	}
	if mockKSP.LastQuery.Context["cmd_ctx_key"] != "cmd_val" {
		t.Errorf("Command context not passed: %v", mockKSP.LastQuery.Context)
	}
}

func TestDSELEngine_EngageKSP_Targeted(t *testing.T) {
	engine, kspDelegator, _, _ := setupDSELEngineWithMocks(t) // Main mockKSP is "dsel_mock_ksp"

	// Register another KSP for targeting
	targetedKSP := &MockKSPForDSEL{Name: "targeted_ksp"}
	targetedKSP.QueryFunc = func(q toolo.KSPQuery) ([]toolo.KSPResult, error) {
		return []toolo.KSPResult{{ID: "targeted_res", Title: "Targeted Result"}}, nil
	}
	kspDelegator.RegisterProvider(targetedKSP, 2)

	cmdParams := map[string]interface{}{"queryText": "targeted query"}
	cmd := DSELCommand{Type: CommandEngageKSP, Params: cmdParams, Target: "targeted_ksp"}

	results, err := engine.ExecuteCommand(cmd)
	if err != nil {
		t.Fatalf("EngageKSP (targeted) failed: %v", err)
	}
	kspResults, _ := results.([]toolo.KSPResult)
	if len(kspResults) != 1 || kspResults[0].ID != "targeted_res" {
		t.Errorf("Unexpected results from targeted KSP: %+v", kspResults)
	}
	if targetedKSP.LastQuery.QueryText != "targeted query" {
		t.Errorf("Query not passed to targeted KSP. Got: %s", targetedKSP.LastQuery.QueryText)
	}
	// Ensure the default mock KSP was NOT called
	// If LastQuery has a field that's only set on call, check that.
	// Here, we rely on targetedKSP.LastQuery being set. If it's empty, default wasn't called.
	// (This part of the test depends on how MockKSPForDSEL is structured. Let's assume LastQuery is only set on call)
	// mainMockKSP := kspDelegator.providers[0].Provider.(*MockKSPForDSEL) // This might be fragile if order changes
	var mainMockKSP *MockKSPForDSEL
	prov, _ := kspDelegator.GetProvider("dsel_mock_ksp")
	mainMockKSP = prov.(*MockKSPForDSEL)

	if mainMockKSP.LastQuery.QueryText == "targeted query" { // Check if it was inadvertently called
		t.Error("Default KSP was called for a targeted query")
	}
}

func TestDSELEngine_EngageKSP_WithAttentionContext(t *testing.T) {
	engine, _, attentionService, mockKSP := setupDSELEngineWithMocks(t)

	// Set attention focus
	attentionService.SetFocus("doc_section", "focused content", map[string]interface{}{"attn_meta": "meta_val"})

	cmdParams := map[string]interface{}{"queryText": "query with attention"}
	cmd := DSELCommand{Type: CommandEngageKSP, Params: cmdParams}

	_, err := engine.ExecuteCommand(cmd)
	if err != nil {
		t.Fatalf("EngageKSP with attention context failed: %v", err)
	}

	if mockKSP.LastQuery.Context["current_attention_focus_type"] != "doc_section" {
		t.Errorf("Attention focus type not in KSP query context: %v", mockKSP.LastQuery.Context)
	}
	if mockKSP.LastQuery.Context["current_attention_focus_content"] != "focused content" {
		t.Errorf("Attention focus content not in KSP query context: %v", mockKSP.LastQuery.Context)
	}
	// Note: metadata from attentionService.currentFocus.Metadata is not automatically added by DSELEngine
	// The DSELEngine only adds Type and Content explicitly. This is fine per current implementation.
}

func TestDSELEngine_EngageKSP_NoDelegator(t *testing.T) {
	engine := NewDSELEngine(nil, nil) // No KSP Delegator
	cmdParams := map[string]interface{}{"queryText": "query"}
	cmd := DSELCommand{Type: CommandEngageKSP, Params: cmdParams}
	_, err := engine.ExecuteCommand(cmd)
	if err == nil {
		t.Error("Expected error when engaging KSP without a delegator, got nil")
	}
}

func TestDSELEngine_EngageKSP_MissingQueryText(t *testing.T) {
	engine, _, _, _ := setupDSELEngineWithMocks(t)
	cmdParams := map[string]interface{}{"context": map[string]interface{}{"key": "val"}} // No queryText
	cmd := DSELCommand{Type: CommandEngageKSP, Params: cmdParams}
	_, err := engine.ExecuteCommand(cmd)
	if err == nil {
		t.Error("Expected error for EngageKSP with missing queryText, got nil")
	}
}

func TestDSELEngine_GetAttentionElement(t *testing.T) {
	engine, _, _, _ := setupDSELEngineWithMocks(t)

	_, exists := engine.GetAttentionElement("nonexistent")
	if exists {
		t.Error("GetAttentionElement returned true for non-existent element")
	}

	params := map[string]interface{}{"name": "myElement", "description": "details"}
	cmd := DSELCommand{Type: CommandDefineAttentionElement, Params: params}
	engine.ExecuteCommand(cmd)

	element, exists := engine.GetAttentionElement("myElement")
	if !exists {
		t.Fatal("GetAttentionElement returned false for existing element")
	}
	if element.Name != "myElement" {
		t.Errorf("Got wrong element: %s", element.Name)
	}
}

func TestDSELEngine_GetCurrentContext(t *testing.T) {
	engine, _, _, _ := setupDSELEngineWithMocks(t)
	initialCtx := engine.GetCurrentContext()
	if len(initialCtx) != 0 {
		t.Errorf("Initial context should be empty, got: %v", initialCtx)
	}

	contextData := map[string]interface{}{"testKey": "testValue"}
	cmd := DSELCommand{Type: CommandSetContext, Params: map[string]interface{}{"data": contextData}}
	engine.ExecuteCommand(cmd)

	currentCtx := engine.GetCurrentContext()
	if !reflect.DeepEqual(currentCtx, contextData) {
		t.Errorf("GetCurrentContext returned %v, expected %v", currentCtx, contextData)
	}

	// Ensure it's a copy
	currentCtx["newKey"] = "newValue"
	originalCtxUnchanged := engine.GetCurrentContext()
	if _, found := originalCtxUnchanged["newKey"]; found {
		t.Error("GetCurrentContext did not return a copy, modification affected internal state.")
	}
}
