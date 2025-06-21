package toolo

import (
	"fmt"
	"reflect"
	"sort"
	"testing"
)

// MockKSP is a mock implementation of KnowledgeSearchProvider for testing.
type MockKSP struct {
	Name          string
	Capabilities  map[string]string
	QueryResults  []KSPResult
	QueryError    error
	QueryReceived KSPQuery // Stores the last query received
}

func (m *MockKSP) Query(query KSPQuery) ([]KSPResult, error) {
	m.QueryReceived = query
	if m.QueryError != nil {
		return nil, m.QueryError
	}
	// Add source to results for easier verification in tests
	resultsWithSource := make([]KSPResult, len(m.QueryResults))
	for i, r := range m.QueryResults {
		resultsWithSource[i] = r
		if resultsWithSource[i].Data == nil {
			resultsWithSource[i].Data = make(map[string]interface{})
		}
		resultsWithSource[i].Data["ksp_source_test"] = m.Name // Differentiate from delegator's augmentation
	}
	return resultsWithSource, nil
}

func (m *MockKSP) GetName() string {
	return m.Name
}

func (m *MockKSP) GetCapabilities() map[string]string {
	return m.Capabilities
}

func TestNewKSPDelegator(t *testing.T) {
	delegator := NewKSPDelegator()
	if delegator == nil {
		t.Fatal("NewKSPDelegator returned nil")
	}
	if len(delegator.providers) != 0 {
		t.Errorf("Expected new delegator to have 0 providers, got %d", len(delegator.providers))
	}
}

func TestKSPDelegator_RegisterProvider(t *testing.T) {
	delegator := NewKSPDelegator()
	mockKSP1 := &MockKSP{Name: "ksp1"}
	mockKSP2 := &MockKSP{Name: "ksp2"}

	err := delegator.RegisterProvider(mockKSP1, 10)
	if err != nil {
		t.Fatalf("Failed to register ksp1: %v", err)
	}
	if len(delegator.providers) != 1 || delegator.providers[0].Name != "ksp1" {
		t.Errorf("ksp1 not registered correctly")
	}

	err = delegator.RegisterProvider(mockKSP2, 5) // Higher priority
	if err != nil {
		t.Fatalf("Failed to register ksp2: %v", err)
	}
	if len(delegator.providers) != 2 || delegator.providers[0].Name != "ksp2" {
		t.Errorf("ksp2 not registered with higher priority. Providers: %+v", delegator.providers)
	}

	// Test registering duplicate
	err = delegator.RegisterProvider(mockKSP1, 1)
	if err == nil {
		t.Errorf("Expected error when registering duplicate provider, got nil")
	}
}

func TestKSPDelegator_UnregisterProvider(t *testing.T) {
	delegator := NewKSPDelegator()
	mockKSP1 := &MockKSP{Name: "ksp1"}
	delegator.RegisterProvider(mockKSP1, 10)

	err := delegator.UnregisterProvider("ksp1")
	if err != nil {
		t.Fatalf("Failed to unregister ksp1: %v", err)
	}
	if len(delegator.providers) != 0 {
		t.Errorf("Expected 0 providers after unregistering, got %d", len(delegator.providers))
	}

	// Test unregistering non-existent provider
	err = delegator.UnregisterProvider("nonexistent")
	if err == nil {
		t.Errorf("Expected error when unregistering non-existent provider, got nil")
	}
}

func TestKSPDelegator_ListProviders(t *testing.T) {
	delegator := NewKSPDelegator()
	mockKSP1 := &MockKSP{Name: "ksp1"}
	mockKSP2 := &MockKSP{Name: "ksp2"}
	mockKSP3 := &MockKSP{Name: "ksp3"}

	delegator.RegisterProvider(mockKSP1, 10)
	delegator.RegisterProvider(mockKSP2, 1) // Highest priority
	delegator.RegisterProvider(mockKSP3, 5) // Mid priority

	expectedOrder := []string{"ksp2", "ksp3", "ksp1"}
	actualOrder := delegator.ListProviders()

	if !reflect.DeepEqual(actualOrder, expectedOrder) {
		t.Errorf("ListProviders() returned %v, expected %v", actualOrder, expectedOrder)
	}
}

func TestKSPDelegator_DelegateQuery_NoProviders(t *testing.T) {
	delegator := NewKSPDelegator()
	_, err := delegator.DelegateQuery(KSPQuery{QueryText: "test"})
	if err == nil {
		t.Errorf("Expected error when delegating query with no providers, got nil")
	}
}

func TestKSPDelegator_DelegateQuery_SingleProvider(t *testing.T) {
	delegator := NewKSPDelegator()
	mockKSP1 := &MockKSP{
		Name: "ksp1",
		QueryResults: []KSPResult{
			{ID: "res1", Title: "Result 1", Relevance: 0.9},
		},
	}
	delegator.RegisterProvider(mockKSP1, 10)

	query := KSPQuery{QueryText: "test query"}
	results, err := delegator.DelegateQuery(query)
	if err != nil {
		t.Fatalf("DelegateQuery failed: %v", err)
	}

	if len(results) != 1 || results[0].ID != "res1" {
		t.Errorf("Unexpected results: %+v", results)
	}
	if mockKSP1.QueryReceived.QueryText != "test query" {
		t.Errorf("Query not passed to KSP. Expected 'test query', got '%s'", mockKSP1.QueryReceived.QueryText)
	}
	if results[0].Data["ksp_source"] != "ksp1" {
		t.Errorf("ksp_source not added to result data: %v", results[0].Data)
	}
}

func TestKSPDelegator_DelegateQuery_MultipleProviders(t *testing.T) {
	delegator := NewKSPDelegator()
	mockKSP1 := &MockKSP{
		Name: "ksp1",
		QueryResults: []KSPResult{
			{ID: "k1r1", Title: "K1 Result 1", Relevance: 0.7},
		},
	}
	mockKSP2 := &MockKSP{ // Higher priority
		Name: "ksp2",
		QueryResults: []KSPResult{
			{ID: "k2r1", Title: "K2 Result 1", Relevance: 0.9},
			{ID: "k2r2", Title: "K2 Result 2", Relevance: 0.8},
		},
	}
	delegator.RegisterProvider(mockKSP1, 10)
	delegator.RegisterProvider(mockKSP2, 1)

	query := KSPQuery{QueryText: "multi test"}
	results, err := delegator.DelegateQuery(query)
	if err != nil {
		t.Fatalf("DelegateQuery failed: %v", err)
	}

	// Expected order: k2r1 (0.9), k2r2 (0.8), k1r1 (0.7)
	expectedOrderIDs := []string{"k2r1", "k2r2", "k1r1"}
	if len(results) != 3 {
		t.Fatalf("Expected 3 results, got %d: %+v", len(results), results)
	}

	actualOrderIDs := make([]string, len(results))
	for i, r := range results {
		actualOrderIDs[i] = r.ID
	}

	if !reflect.DeepEqual(actualOrderIDs, expectedOrderIDs) {
		t.Errorf("Results not sorted by relevance correctly. Got %v, expected %v", actualOrderIDs, expectedOrderIDs)
	}

	if mockKSP1.QueryReceived.QueryText != "multi test" || mockKSP2.QueryReceived.QueryText != "multi test" {
		t.Errorf("Query not passed to all KSPs")
	}

	// Check ksp_source and ksp_priority in data
	for _, r := range results {
		source, okS := r.Data["ksp_source"].(string)
		priority, okP := r.Data["ksp_priority"].(int)
		if !okS || !okP {
			t.Errorf("ksp_source or ksp_priority missing or not of expected type in result %s: Data: %v", r.ID, r.Data)
			continue
		}
		if source == "ksp1" && priority != 10 {
			t.Errorf("Incorrect priority for ksp1 result %s: expected 10, got %d", r.ID, priority)
		}
		if source == "ksp2" && priority != 1 {
			t.Errorf("Incorrect priority for ksp2 result %s: expected 1, got %d", r.ID, priority)
		}
	}
}

func TestKSPDelegator_DelegateQuery_ProviderError(t *testing.T) {
	delegator := NewKSPDelegator()
	mockKSP1 := &MockKSP{
		Name:       "ksp1_error",
		QueryError: fmt.Errorf("ksp1 failed"),
	}
	mockKSP2 := &MockKSP{
		Name: "ksp2_ok",
		QueryResults: []KSPResult{
			{ID: "k2good", Title: "Good Result", Relevance: 0.9},
		},
	}
	delegator.RegisterProvider(mockKSP1, 5)
	delegator.RegisterProvider(mockKSP2, 10) // Lower priority but should still return results

	query := KSPQuery{QueryText: "error test"}
	results, err := delegator.DelegateQuery(query)
	if err != nil {
		// We might get an error if ALL providers fail. If some succeed, we might not.
		// The current implementation returns results if any provider succeeds.
		t.Logf("DelegateQuery returned an error (which might be ok if all failed, but here one should succeed): %v", err)
	}

	if len(results) != 1 || results[0].ID != "k2good" {
		t.Errorf("Expected results from ksp2_ok, got: %+v", results)
	}

	// Test case: All providers fail
	delegator = NewKSPDelegator()
	mockKSPErrorOnly := &MockKSP{Name: "ksp_error_only", QueryError: fmt.Errorf("always fails")}
	delegator.RegisterProvider(mockKSPErrorOnly, 1)
	_, err = delegator.DelegateQuery(query)
	if err == nil {
		t.Errorf("Expected an error when all providers fail, but got nil")
	}
}

func TestKSPDelegator_GetProvider(t *testing.T) {
	delegator := NewKSPDelegator()
	mockKSP1 := &MockKSP{Name: "ksp1"}
	delegator.RegisterProvider(mockKSP1, 10)

	provider, err := delegator.GetProvider("ksp1")
	if err != nil {
		t.Fatalf("GetProvider failed for ksp1: %v", err)
	}
	if provider.GetName() != "ksp1" {
		t.Errorf("GetProvider returned wrong provider. Expected 'ksp1', got '%s'", provider.GetName())
	}

	_, err = delegator.GetProvider("nonexistent")
	if err == nil {
		t.Errorf("Expected error when getting non-existent provider, got nil")
	}
}

func TestKSPDelegator_ProviderSorting(t *testing.T) {
	delegator := NewKSPDelegator()
	kspA := &MockKSP{Name: "A"}
	kspB := &MockKSP{Name: "B"}
	kspC := &MockKSP{Name: "C"}

	// Register in non-sorted order of priority
	delegator.RegisterProvider(kspA, 20)
	delegator.RegisterProvider(kspB, 5)  // Highest priority
	delegator.RegisterProvider(kspC, 10) // Mid priority

	// Check internal order
	if delegator.providers[0].Name != "B" || delegator.providers[1].Name != "C" || delegator.providers[2].Name != "A" {
		t.Errorf("Providers not sorted correctly internally. Order: %s, %s, %s",
			delegator.providers[0].Name, delegator.providers[1].Name, delegator.providers[2].Name)
	}

	// ListProviders should also reflect this
	expectedOrder := []string{"B", "C", "A"}
	actualOrder := delegator.ListProviders()
	if !reflect.DeepEqual(actualOrder, expectedOrder) {
		t.Errorf("ListProviders() after mixed priority registration: got %v, expected %v", actualOrder, expectedOrder)
	}
}

// Helper for sorting results by ID for comparison if relevance is equal or not primary sort key
func sortResultsByID(results []KSPResult) {
	sort.Slice(results, func(i, j int) bool {
		return results[i].ID < results[j].ID
	})
}
