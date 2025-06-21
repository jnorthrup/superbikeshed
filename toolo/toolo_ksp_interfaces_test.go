package toolo

import (
	"testing"
)

// TestKSPInterfaceStructs can be used to test any logic within KSPQuery, KSPResult if they become more complex.
func TestKSPInterfaceStructs(t *testing.T) {
	// Example: Ensure KSPQuery can be created
	query := KSPQuery{
		QueryText: "test query",
		Context:   map[string]interface{}{"lang": "en"},
	}
	if query.QueryText != "test query" {
		t.Errorf("Expected QueryText 'test query', got '%s'", query.QueryText)
	}

	// Example: Ensure KSPResult can be created
	result := KSPResult{
		ID:        "res1",
		Title:     "Test Result",
		Snippet:   "This is a test snippet.",
		Relevance: 0.8,
		Data:      map[string]interface{}{"source": "test_ksp"},
	}
	if result.ID != "res1" {
		t.Errorf("Expected ID 'res1', got '%s'", result.ID)
	}
}
